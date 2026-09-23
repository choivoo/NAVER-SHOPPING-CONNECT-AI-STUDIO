package com.shoppingconnect.aistudio.media.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import java.io.File
import java.nio.ByteOrder

/** Decodes any platform-supported audio (mp3, m4a/aac, ogg, wav, or a video's audio track) to mono [Pcm]. */
object AudioDecoder {
    fun decode(file: File, maxMs: Long = 10 * 60_000L): Pcm {
        if (file.extension.equals("wav", true)) return runCatching { WavIO.read(file).resampled() }.getOrElse { decodeWithCodec(file, maxMs) }
        return decodeWithCodec(file, maxMs)
    }

    private fun decodeWithCodec(file: File, maxMs: Long): Pcm {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw AppException(ErrorKind.UnsupportedFormat, "오디오 트랙이 없습니다.")
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val out = FloatArrayBuilder()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val maxSamples = maxMs * rate / 1000
            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = ex.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0); ex.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        outIdx >= 0 -> {
                            val buf = codec.getOutputBuffer(outIdx)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frames = sb.remaining() / channels
                            for (i in 0 until frames) {
                                var acc = 0f
                                for (c in 0 until channels) acc += sb.get(i * channels + c) / 32768f
                                out.add(acc / channels)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || out.size > maxSamples) outputDone = true
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }; codec.release()
            }
            return Pcm(out.toArray(), rate).resampled()
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw AppException(ErrorKind.UnsupportedFormat, "오디오를 읽을 수 없습니다.", e)
        } finally {
            ex.release()
        }
    }
}

class FloatArrayBuilder(initial: Int = 44100) {
    private var data = FloatArray(initial)
    var size = 0; private set
    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }
    fun toArray(): FloatArray = data.copyOf(size)
}
