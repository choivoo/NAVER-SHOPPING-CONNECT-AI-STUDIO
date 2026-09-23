package com.shoppingconnect.aistudio.media.audio

import com.shoppingconnect.aistudio.domain.model.AudioMix
import com.shoppingconnect.aistudio.domain.model.TrackMix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** A mono source placed on the timeline. */
data class PlacedAudio(val pcm: Pcm, val startMs: Long, val gain: Float = 1f, val trimStartMs: Long = 0, val maxDurationMs: Long = Long.MAX_VALUE)

/**
 * Multi-track mixer: voice, music (looped to length), sfx and clip audio, with per-track
 * volume/mute/solo/fade/pan, voice-driven BGM auto-ducking (attack/release smoothing) and a
 * master peak limiter so the result never clips. Output: interleaved stereo 16-bit @ 44.1 kHz.
 */
object AudioMixer {
    fun mix(
        durationMs: Long,
        mix: AudioMix,
        voice: List<PlacedAudio>,
        music: Pcm?,
        sfx: List<PlacedAudio>,
        clipAudio: List<PlacedAudio>,
        rate: Int = Pcm.RATE,
    ): ShortArray {
        val n = Pcm.msToSamples(durationMs, rate)
        val anySolo = mix.voice.solo || mix.music.solo || mix.sfx.solo
        fun audible(t: TrackMix) = !t.muted && (!anySolo || t.solo)

        val voiceBus = FloatArray(n)
        if (audible(mix.voice)) (voice + clipAudio).forEach { place(voiceBus, it, rate) }
        applyFades(voiceBus, mix.voice, rate)

        val musicBus = FloatArray(n)
        if (music != null && music.samples.isNotEmpty() && audible(mix.music)) {
            for (i in 0 until n) musicBus[i] = music.samples[i % music.samples.size]
            applyFades(musicBus, mix.music, rate)
            if (mix.ducking && voice.isNotEmpty()) duck(musicBus, voiceBus, mix.duckLevel, rate)
        }

        val sfxBus = FloatArray(n)
        if (audible(mix.sfx)) sfx.forEach { place(sfxBus, it, rate) }

        val out = ShortArray(n * 2)
        val (vl, vr) = pan(mix.voice.pan)
        val (ml, mr) = pan(mix.music.pan)
        val (sl, sr) = pan(mix.sfx.pan)
        var limGain = 1f
        val release = exp(-1.0 / (0.12 * rate)).toFloat()
        for (i in 0 until n) {
            val v = voiceBus[i] * mix.voice.volume
            val m = musicBus[i] * mix.music.volume
            val s = sfxBus[i] * mix.sfx.volume
            var l = v * vl + m * ml + s * sl
            var r = v * vr + m * mr + s * sr
            if (mix.limiter) {
                val peak = maxOf(abs(l), abs(r))
                val target = if (peak * limGain > 0.92f) 0.92f / peak else 1f
                limGain = if (target < limGain) target else target + (limGain - target) * release
                l = softClip(l * limGain); r = softClip(r * limGain)
            }
            out[i * 2] = (l.coerceIn(-1f, 1f) * 32767).toInt().toShort()
            out[i * 2 + 1] = (r.coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
        return out
    }

    private fun softClip(x: Float): Float = if (abs(x) < 0.9f) x else tanh(x.toDouble()).toFloat()

    /** Constant-power pan: -1 left … 1 right. */
    fun pan(p: Float): Pair<Float, Float> {
        val a = (p.coerceIn(-1f, 1f) + 1) * Math.PI / 4
        return (cos(a) * sqrt(2.0)).toFloat() to (sin(a) * sqrt(2.0)).toFloat()
    }

    private fun place(bus: FloatArray, a: PlacedAudio, rate: Int) {
        val start = Pcm.msToSamples(a.startMs, rate)
        val trim = Pcm.msToSamples(a.trimStartMs, rate)
        val maxLen = if (a.maxDurationMs == Long.MAX_VALUE) Int.MAX_VALUE else Pcm.msToSamples(a.maxDurationMs, rate)
        val src = a.pcm.samples
        var i = 0
        while (true) {
            val si = trim + i
            val di = start + i
            if (si >= src.size || di >= bus.size || i >= maxLen) break
            if (di >= 0) bus[di] += src[si] * a.gain
            i++
        }
        // de-click tail
        val tail = minOf(256, i)
        for (k in 0 until tail) { val di = start + i - tail + k; if (di in bus.indices) bus[di] *= (tail - k).toFloat() / tail }
    }

    private fun applyFades(bus: FloatArray, t: TrackMix, rate: Int) {
        val fi = Pcm.msToSamples(t.fadeInMs, rate).coerceAtMost(bus.size)
        for (i in 0 until fi) bus[i] *= i.toFloat() / fi
        val fo = Pcm.msToSamples(t.fadeOutMs, rate).coerceAtMost(bus.size)
        for (i in 0 until fo) bus[bus.size - 1 - i] *= i.toFloat() / fo
    }

    /** Sidechain ducking: music drops to [level] while voice is present, with 60 ms attack / 350 ms release. */
    fun duck(music: FloatArray, voice: FloatArray, level: Float, rate: Int) {
        val win = rate / 50
        val attack = exp(-1.0 / (0.06 * rate)).toFloat()
        val rel = exp(-1.0 / (0.35 * rate)).toFloat()
        var g = 1f
        var i = 0
        while (i < music.size) {
            val end = minOf(i + win, music.size)
            var sum = 0f
            for (k in i until end) sum += voice[k] * voice[k]
            val rms = sqrt(sum / (end - i))
            val target = if (rms > 0.02f) level else 1f
            for (k in i until end) {
                val coef = if (target < g) attack else rel
                g = target + (g - target) * coef
                music[k] *= g
            }
            i = end
        }
    }
}
