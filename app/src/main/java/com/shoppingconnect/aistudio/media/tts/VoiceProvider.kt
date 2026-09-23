package com.shoppingconnect.aistudio.media.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.domain.model.VoiceSettings
import com.shoppingconnect.aistudio.media.audio.WavIO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class VoiceOption(val name: String, val label: String, val networkRequired: Boolean, val quality: Int)

data class SynthResult(val file: File, val durationMs: Long)

/** Provider abstraction: Android TTS today; cloud TTS or imported audio plug in behind the same interface. */
interface VoiceProvider {
    val id: String
    val label: String
    suspend fun isAvailable(): Boolean
    suspend fun voices(): List<VoiceOption>
    suspend fun synthesize(text: String, settings: VoiceSettings, outDir: File): SynthResult
}

/**
 * On-device Android TextToSpeech (Korean). Voice presets map to real installed voices plus
 * pitch/rate — Android does not expose voice gender, so no fake voice IDs are invented.
 */
@Singleton
class AndroidTtsProvider @Inject constructor(@ApplicationContext private val context: Context) : VoiceProvider {
    override val id = "android_tts"
    override val label = "Android TTS (기기 내장)"

    private var tts: TextToSpeech? = null
    private val initMutex = Mutex()
    private val synthMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private suspend fun engine(): TextToSpeech = initMutex.withLock {
        tts?.let { return it }
        val ready = CompletableDeferred<Int>()
        val t = withContext(Dispatchers.Main) { TextToSpeech(context) { status -> ready.complete(status) } }
        val status = withTimeout(15_000) { ready.await() }
        if (status != TextToSpeech.SUCCESS) {
            t.shutdown(); throw AppException(ErrorKind.NotConfigured, "기기에 TTS 엔진이 없습니다. 설정에서 음성 합성 엔진을 설치해 주세요.")
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { utteranceId?.let { pending.remove(it)?.complete(true) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utteranceId?.let { pending.remove(it)?.complete(false) } }
            override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let { pending.remove(it)?.complete(false) } }
        })
        val lang = t.setLanguage(Locale.KOREAN)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            AppLog.w("TTS", "Korean voice data missing")
        }
        tts = t
        t
    }

    override suspend fun isAvailable(): Boolean = runCatching {
        val t = engine()
        t.isLanguageAvailable(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE
    }.getOrDefault(false)

    override suspend fun voices(): List<VoiceOption> = runCatching {
        engine().voices.orEmpty().filter { it.locale.language == "ko" && !it.features.contains("notInstalled") }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired })
            .map { VoiceOption(it.name, prettyName(it), it.isNetworkConnectionRequired, it.quality) }
    }.getOrDefault(emptyList())

    private fun prettyName(v: Voice): String {
        val base = v.name.substringAfter("ko-kr-x-", v.name).substringBefore("-")
        return "한국어 ${base.uppercase()}${if (v.isNetworkConnectionRequired) " (온라인)" else ""}"
    }

    override suspend fun synthesize(text: String, settings: VoiceSettings, outDir: File): SynthResult = synthMutex.withLock {
        val t = engine()
        val voices = t.voices.orEmpty().filter { it.locale.language == "ko" }
        val chosen = settings.voiceName?.let { n -> voices.firstOrNull { it.name == n } }
            ?: voices.filter { !it.isNetworkConnectionRequired }.let { local ->
                // Presets pick different installed voices when several exist.
                if (local.isEmpty()) null else local[settings.preset.ordinal % local.size]
            }
        chosen?.let { t.voice = it } ?: t.setLanguage(Locale.KOREAN)
        t.setPitch((settings.preset.pitch * settings.pitch).coerceIn(0.5f, 2f))
        t.setSpeechRate((settings.preset.rate * settings.speed).coerceIn(0.5f, 2.5f))
        outDir.mkdirs()
        val file = File(outDir, "tts_${newId()}.wav")
        val uid = newId()
        val done = CompletableDeferred<Boolean>()
        pending[uid] = done
        val res = t.synthesizeToFile(text, null, file, uid)
        if (res != TextToSpeech.SUCCESS) { pending.remove(uid); throw AppException(ErrorKind.RenderFailure, "음성 합성을 시작하지 못했습니다.") }
        val ok = withTimeout(60_000) { done.await() }
        if (!ok || !file.exists() || file.length() < 100) throw AppException(ErrorKind.RenderFailure, "음성 합성에 실패했습니다.")
        val pcm = withContext(Dispatchers.IO) { WavIO.read(file) }
        SynthResult(file, pcm.durationMs)
    }

    fun shutdown() { tts?.shutdown(); tts = null }

}

/** User-provided narration audio (already recorded/imported) — no synthesis involved. */
class ImportedAudioVoiceProvider(private val file: File) : VoiceProvider {
    override val id = "imported"
    override val label = "직접 가져온 음성 파일"
    override suspend fun isAvailable() = file.exists()
    override suspend fun voices() = emptyList<VoiceOption>()
    override suspend fun synthesize(text: String, settings: VoiceSettings, outDir: File): SynthResult =
        SynthResult(file, com.shoppingconnect.aistudio.media.audio.AudioDecoder.decode(file).durationMs)
}
