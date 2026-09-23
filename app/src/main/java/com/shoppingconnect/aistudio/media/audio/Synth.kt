package com.shoppingconnect.aistudio.media.audio

import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.SfxType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedurally generated background music and sound effects. Everything is synthesised by the
 * app itself at render time, so there is no third-party audio content and no licensing question.
 */
object BgmSynth {
    private data class MoodSpec(val bpm: Int, val chords: List<List<Int>>, val drums: Boolean, val hat: Boolean, val bright: Float, val bassOct: Int)

    private fun spec(m: BgmMood) = when (m) {
        BgmMood.UPBEAT -> MoodSpec(118, listOf(listOf(60, 64, 67), listOf(67, 71, 74), listOf(69, 72, 76), listOf(65, 69, 72)), true, true, 0.8f, 36)
        BgmMood.MINIMAL -> MoodSpec(92, listOf(listOf(62, 65, 69), listOf(60, 64, 67)), false, true, 0.4f, 38)
        BgmMood.TECH -> MoodSpec(124, listOf(listOf(57, 60, 64), listOf(53, 57, 60), listOf(55, 59, 62), listOf(52, 55, 59)), true, true, 0.9f, 33)
        BgmMood.CUTE -> MoodSpec(128, listOf(listOf(72, 76, 79), listOf(69, 72, 76), listOf(65, 69, 72), listOf(67, 71, 74)), true, false, 1.0f, 48)
        BgmMood.PREMIUM -> MoodSpec(84, listOf(listOf(57, 60, 64, 67), listOf(53, 57, 60, 64), listOf(50, 53, 57, 60), listOf(52, 56, 59, 62)), false, false, 0.35f, 33)
        BgmMood.CALM -> MoodSpec(76, listOf(listOf(60, 64, 67, 71), listOf(57, 60, 64, 67)), false, false, 0.3f, 36)
        BgmMood.NONE -> MoodSpec(100, emptyList(), false, false, 0f, 36)
    }

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun generate(mood: BgmMood, durationMs: Long, rate: Int = Pcm.RATE, seed: Int = 7): Pcm {
        val n = Pcm.msToSamples(durationMs, rate)
        val out = FloatArray(n)
        if (mood == BgmMood.NONE || n == 0) return Pcm(out, rate)
        val s = spec(mood)
        val beat = 60.0 / s.bpm
        val barSamples = (beat * 4 * rate).toInt()
        val rnd = Random(seed)
        // Pad chords (soft saw-ish via 3 harmonics) with slow attack
        for (i in 0 until n) {
            val bar = i / barSamples
            val chord = s.chords[bar % s.chords.size]
            val tInBar = (i % barSamples).toDouble() / rate
            val env = (1 - exp(-tInBar * 3)) * (0.75 + 0.25 * sin(2 * PI * tInBar / (beat * 4)))
            val t = i.toDouble() / rate
            var v = 0.0
            chord.forEach { note ->
                val f = hz(note)
                v += sin(2 * PI * f * t) + s.bright * 0.35 * sin(4 * PI * f * t) + s.bright * 0.15 * sin(6 * PI * f * t)
            }
            out[i] += (v / chord.size * 0.09 * env).toFloat()
            // bass on root, pulses per beat
            val beatPos = (t % beat) / beat
            val bassEnv = exp(-beatPos * 4)
            out[i] += (sin(2 * PI * hz(s.bassOct + (chord[0] % 12)) * t) * 0.16 * bassEnv).toFloat()
        }
        if (s.drums) {
            val beatSamples = (beat * rate).toInt()
            var b = 0
            while (b * beatSamples < n) {
                val start = b * beatSamples
                if (b % 2 == 0) addKick(out, start, rate) else addSnare(out, start, rate, rnd)
                b++
            }
        }
        if (s.hat) {
            val half = (beat / 2 * rate).toInt()
            var k = 0
            while (k * half < n) { addHat(out, k * half, rate, rnd, if (k % 2 == 1) 0.05f else 0.03f); k++ }
        }
        return Pcm(out, rate)
    }

    private fun addKick(out: FloatArray, start: Int, rate: Int) {
        val len = (0.25 * rate).toInt()
        var phase = 0.0
        for (i in 0 until len) {
            val idx = start + i; if (idx >= out.size) return
            val t = i.toDouble() / rate
            val f = 50 + 90 * exp(-t * 30)
            phase += 2 * PI * f / rate
            out[idx] += (sin(phase) * exp(-t * 9) * 0.45).toFloat()
        }
    }

    private fun addSnare(out: FloatArray, start: Int, rate: Int, rnd: Random) {
        val len = (0.18 * rate).toInt()
        for (i in 0 until len) {
            val idx = start + i; if (idx >= out.size) return
            val t = i.toDouble() / rate
            out[idx] += ((rnd.nextFloat() * 2 - 1) * exp(-t * 22) * 0.16 + sin(2 * PI * 190 * t) * exp(-t * 30) * 0.08).toFloat()
        }
    }

    private fun addHat(out: FloatArray, start: Int, rate: Int, rnd: Random, gain: Float) {
        val len = (0.04 * rate).toInt()
        var prev = 0f
        for (i in 0 until len) {
            val idx = start + i; if (idx >= out.size) return
            val noise = rnd.nextFloat() * 2 - 1
            val hp = noise - prev; prev = noise
            out[idx] += hp * gain * exp(-i.toFloat() / len * 5)
        }
    }
}

object SfxSynth {
    fun generate(type: SfxType, rate: Int = Pcm.RATE): Pcm {
        val rnd = Random(3)
        return when (type) {
            SfxType.POP -> tone(0.09, rate) { t -> sin(2 * PI * (900 - 5000 * t) * t) * exp(-t * 40) * 0.6 }
            SfxType.TAP -> tone(0.04, rate) { t -> sin(2 * PI * 1800 * t) * exp(-t * 120) * 0.5 }
            SfxType.WHOOSH -> noiseSweep(0.35, rate, rnd, rising = false)
            SfxType.TRANSITION -> noiseSweep(0.45, rate, rnd, rising = true)
            SfxType.SUCCESS -> tone(0.5, rate) { t ->
                val notes = listOf(72, 76, 79, 84)
                val idx = (t / 0.1).toInt().coerceAtMost(3)
                val lt = t - idx * 0.1
                sin(2 * PI * 440.0 * 2.0.pow((notes[idx] - 69) / 12.0) * t) * exp(-lt * 8) * 0.35
            }
        }
    }

    private fun tone(sec: Double, rate: Int, f: (Double) -> Double): Pcm {
        val n = (sec * rate).toInt()
        return Pcm(FloatArray(n) { f(it.toDouble() / rate).toFloat() }, rate)
    }

    private fun noiseSweep(sec: Double, rate: Int, rnd: Random, rising: Boolean): Pcm {
        val n = (sec * rate).toInt()
        val out = FloatArray(n)
        var lp = 0f
        for (i in 0 until n) {
            val p = i.toFloat() / n
            val cutoff = if (rising) 0.02f + 0.5f * p else 0.5f - 0.45f * p
            lp += cutoff * ((rnd.nextFloat() * 2 - 1) - lp)
            val env = sin(PI * p).toFloat()
            out[i] = lp * env * 0.9f
        }
        return Pcm(out, rate)
    }
}
