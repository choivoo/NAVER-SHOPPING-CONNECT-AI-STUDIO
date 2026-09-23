package com.shoppingconnect.aistudio.media.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Mono float PCM at [sampleRate]. The whole audio pipeline works on this type. */
class Pcm(val samples: FloatArray, val sampleRate: Int = RATE) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate

    fun resampled(target: Int = RATE): Pcm {
        if (target == sampleRate || samples.isEmpty()) return this
        val ratio = sampleRate.toDouble() / target
        val n = (samples.size / ratio).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val src = i * ratio
            val i0 = src.toInt()
            val i1 = minOf(i0 + 1, samples.size - 1)
            val f = (src - i0).toFloat()
            out[i] = samples[i0] * (1 - f) + samples[i1] * f
        }
        return Pcm(out, target)
    }

    companion object {
        const val RATE = 44100
        fun silence(ms: Long, rate: Int = RATE) = Pcm(FloatArray((ms * rate / 1000).toInt()), rate)
        fun msToSamples(ms: Long, rate: Int = RATE) = (ms * rate / 1000).toInt()
    }
}

object WavIO {
    /** Reads 8/16/24/32-bit PCM or 32-bit float WAV, downmixed to mono. */
    fun read(file: File): Pcm {
        val bytes = file.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size > 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "not a WAV file" }
        var pos = 12
        var channels = 1; var rate = 22050; var bits = 16; var format = 1
        var dataOff = -1; var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = bb.getInt(pos + 4)
            if (id == "fmt ") {
                format = bb.getShort(pos + 8).toInt() and 0xffff
                channels = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
                bits = bb.getShort(pos + 22).toInt()
            } else if (id == "data") {
                dataOff = pos + 8
                dataLen = if (len <= 0 || dataOff + len > bytes.size) bytes.size - dataOff else len
                break
            }
            pos += 8 + len + (len and 1)
        }
        require(dataOff > 0) { "no data chunk" }
        val bytesPer = bits / 8
        val frames = dataLen / (bytesPer * channels)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var acc = 0f
            for (c in 0 until channels) {
                val o = dataOff + (i * channels + c) * bytesPer
                acc += when {
                    format == 3 && bits == 32 -> bb.getFloat(o)
                    bits == 16 -> bb.getShort(o) / 32768f
                    bits == 8 -> ((bytes[o].toInt() and 0xff) - 128) / 128f
                    bits == 24 -> ((bytes[o].toInt() and 0xff) or ((bytes[o + 1].toInt() and 0xff) shl 8) or (bytes[o + 2].toInt() shl 16)) / 8388608f
                    bits == 32 -> bb.getInt(o) / 2147483648f
                    else -> 0f
                }
            }
            out[i] = acc / channels
        }
        return Pcm(out, rate)
    }

    fun write(file: File, pcm: Pcm) {
        val n = pcm.samples.size
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(pcm.sampleRate).putInt(pcm.sampleRate * 2).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(n * 2)
            raf.write(header.array())
            val body = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.samples.forEach { body.putShort((it.coerceIn(-1f, 1f) * 32767).roundToInt().toShort()) }
            raf.write(body.array())
        }
    }
}
