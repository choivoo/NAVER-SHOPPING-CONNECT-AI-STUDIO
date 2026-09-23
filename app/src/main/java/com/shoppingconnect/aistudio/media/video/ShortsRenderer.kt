package com.shoppingconnect.aistudio.media.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaMuxer
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.media.audio.AacEncoder
import com.shoppingconnect.aistudio.media.audio.AudioDecoder
import com.shoppingconnect.aistudio.media.audio.AudioMixer
import com.shoppingconnect.aistudio.media.audio.BgmSynth
import com.shoppingconnect.aistudio.media.audio.EncodedAudio
import com.shoppingconnect.aistudio.media.audio.PlacedAudio
import com.shoppingconnect.aistudio.media.audio.Pcm
import com.shoppingconnect.aistudio.media.audio.SfxSynth
import java.io.File
import java.nio.ByteBuffer

data class RenderStats(val frames: Int, val renderMs: Long, val width: Int, val height: Int, val codec: String, val hardware: Boolean, val bytes: Long)

/**
 * Timeline → MP4 (H.264/HEVC + AAC). Audio is mixed and encoded first, then video frames are
 * rendered and encoded one at a time (constant memory) and interleaved into MediaMuxer.
 * Blocking and single-threaded by design (EGL context affinity) — call from a worker thread.
 */
class ShortsRenderer(
    private val timeline: ShortTimeline,
    private val resolvePath: (com.shoppingconnect.aistudio.domain.model.Scene) -> String?,
) {
    fun render(output: File, onProgress: (Int) -> Unit, isCancelled: () -> Boolean): RenderStats {
        if (timeline.scenes.isEmpty()) throw AppException(ErrorKind.RenderFailure, "장면이 없습니다.")
        val started = System.currentTimeMillis()
        val rs = timeline.render
        val choice = CodecSupport.choose(rs.hevc, rs.resolution.width, rs.resolution.height, rs.fps)
        val fps = rs.fps.coerceIn(24, 60)
        val bitrate = (choice.width * choice.height * fps * rs.quality.bitsPerPixel).toInt().coerceIn(2_000_000, 40_000_000)
        val durationMs = timeline.durationMs
        AppLog.i("Render", "start ${choice.width}x${choice.height}@$fps ${choice.mime} hw=${choice.hardware} ${durationMs}ms")

        onProgress(1)
        val audio = buildAudio(durationMs, isCancelled)
        onProgress(5)

        output.parentFile?.mkdirs()
        val tmp = File(output.parentFile, output.name + ".part")
        tmp.delete()
        val muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val media = SceneMediaSource(resolvePath, maxDim = (maxOf(choice.width, choice.height) * 1.2f).toInt())
        val encoder = SurfaceVideoEncoder(choice, fps, bitrate)
        val frame = Bitmap.createBitmap(choice.width, choice.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val renderer = FrameRenderer(timeline.copy(render = rs), choice.width, choice.height, media)
        val totalFrames = ((durationMs * fps) / 1000).toInt().coerceAtLeast(1)
        var videoTrack = -1
        var audioTrack = -1
        var muxerStarted = false
        var audioIdx = 0
        val info = MediaCodec.BufferInfo()
        var success = false

        fun writeAudioUpTo(ptsUs: Long) {
            if (!muxerStarted || audioTrack < 0) return
            while (audioIdx < audio.samples.size && audio.samples[audioIdx].ptsUs <= ptsUs) {
                val s = audio.samples[audioIdx]
                val bi = MediaCodec.BufferInfo().apply { set(0, s.data.size, s.ptsUs, s.flags) }
                muxer.writeSampleData(audioTrack, ByteBuffer.wrap(s.data), bi)
                audioIdx++
            }
        }

        fun drain(endOfStream: Boolean) {
            while (true) {
                val idx = encoder.codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "format changed twice" }
                        videoTrack = muxer.addTrack(encoder.codec.outputFormat)
                        if (audio.samples.isNotEmpty()) audioTrack = muxer.addTrack(audio.format)
                        muxer.start(); muxerStarted = true
                    }
                    idx >= 0 -> {
                        val buf = encoder.codec.getOutputBuffer(idx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxerStarted) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            writeAudioUpTo(info.presentationTimeUs)
                            muxer.writeSampleData(videoTrack, buf, info)
                        }
                        encoder.codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            for (f in 0 until totalFrames) {
                if (isCancelled()) throw kotlinx.coroutines.CancellationException("render cancelled")
                val tMs = f * 1000L / fps
                renderer.draw(canvas, tMs, preview = false)
                encoder.submit(frame, tMs * 1_000_000L)
                drain(false)
                if (f % 10 == 0) onProgress(5 + (f * 93 / totalFrames))
            }
            encoder.signalEnd()
            drain(true)
            writeAudioUpTo(Long.MAX_VALUE)
            success = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AppException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw AppException(ErrorKind.RenderFailure, "메모리가 부족합니다. 720p로 낮춰 다시 시도해 주세요.")
        } catch (e: Exception) {
            AppLog.e("Render", "render failed", e)
            throw AppException(ErrorKind.RenderFailure, e.message, e)
        } finally {
            encoder.release()
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
            media.release()
            frame.recycle()
            if (success) { output.delete(); tmp.renameTo(output) } else tmp.delete()
        }
        onProgress(100)
        return RenderStats(totalFrames, System.currentTimeMillis() - started, choice.width, choice.height, choice.mime, choice.hardware, output.length())
    }

    private fun buildAudio(durationMs: Long, isCancelled: () -> Boolean): EncodedAudio {
        val voice = timeline.voiceClips.filter { it.path != null && File(it.path).exists() }.mapNotNull { c ->
            runCatching { PlacedAudio(AudioDecoder.decode(File(c.path!!)), c.startMs, c.volume * timeline.voice.volume, c.trimStartMs, if (c.durationMs > 0) c.durationMs else Long.MAX_VALUE) }
                .onFailure { AppLog.w("Render", "voice clip skipped", it) }.getOrNull()
        }
        val music: Pcm? = timeline.music?.path?.let { p ->
            runCatching { AudioDecoder.decode(File(p), durationMs + 1000) }.onFailure { AppLog.w("Render", "music decode failed", it) }.getOrNull()
        } ?: if (timeline.bgmMood != BgmMood.NONE) BgmSynth.generate(timeline.bgmMood, durationMs) else null
        val sfx = timeline.sfx.mapNotNull { c -> c.sfx?.let { PlacedAudio(SfxSynth.generate(it), c.startMs, c.volume) } }
        val starts = timeline.sceneStarts()
        val clipAudio = timeline.scenes.mapIndexedNotNull { i, s ->
            val path = s.mediaPath
            if (!s.isVideo || path == null || s.volume <= 0f) return@mapIndexedNotNull null
            runCatching { PlacedAudio(AudioDecoder.decode(File(path), s.trimStartMs + s.durationMs), starts[i], s.volume, s.trimStartMs, s.durationMs) }.getOrNull()
        }
        if (isCancelled()) throw kotlinx.coroutines.CancellationException()
        val pcm = AudioMixer.mix(durationMs, timeline.mix, voice, music, sfx, clipAudio)
        return AacEncoder.encode(pcm, isCancelled = isCancelled)
    }
}
