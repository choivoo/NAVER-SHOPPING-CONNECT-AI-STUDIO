package com.shoppingconnect.aistudio.media.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class EncodedSample(val data: ByteArray, val ptsUs: Long, val flags: Int)

class EncodedAudio(val format: MediaFormat, val samples: List<EncodedSample>)

/** Encodes interleaved stereo PCM16 to AAC-LC using the platform MediaCodec encoder. */
object AacEncoder {
    fun encode(pcm: ShortArray, rate: Int = Pcm.RATE, channels: Int = 2, bitrate: Int = 160_000, isCancelled: () -> Boolean = { false }): EncodedAudio {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val out = ArrayList<EncodedSample>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var inPos = 0 // in shorts
        var inputDone = false
        try {
            while (true) {
                if (isCancelled()) throw kotlinx.coroutines.CancellationException("cancelled")
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        val maxShorts = (buf.capacity() / 2 / channels) * channels
                        val count = minOf(maxShorts, pcm.size - inPos)
                        val ptsUs = (inPos / channels).toLong() * 1_000_000L / rate
                        if (count <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, inPos, count)
                            codec.queueInputBuffer(idx, 0, count * 2, ptsUs, 0)
                            inPos += count
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFormat = codec.outputFormat
                } else if (o >= 0) {
                    val buf: ByteBuffer = codec.getOutputBuffer(o)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val bytes = ByteArray(info.size)
                        buf.position(info.offset); buf.get(bytes, 0, info.size)
                        out += EncodedSample(bytes, info.presentationTimeUs, info.flags)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release()
        }
        return EncodedAudio(outFormat ?: fmt, out)
    }
}
