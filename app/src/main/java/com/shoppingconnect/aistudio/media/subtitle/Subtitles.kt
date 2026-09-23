package com.shoppingconnect.aistudio.media.subtitle

import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.SubtitleCue

/**
 * Korean-aware subtitle segmentation. Lines are kept short for 9:16 screens:
 * split at punctuation first, then at connective endings (…고, …며, …서, …지만) and
 * particles, never in the middle of a word.
 */
object KoreanSubtitleSegmenter {
    private val connectiveEndings = listOf("하고", "되고", "이고", "지만", "는데", "면서", "으며", "니까", "어서", "아서", "해서", "하며", "고", "며", "서", "도", "면")

    fun segment(text: String, maxChars: Int = 14): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        // Break after sentence punctuation — but never inside numbers such as "5.3" or "1,200".
        val phrases = clean.split(Regex("(?<=[!?。…~])\\s*|(?<=[.,])(?!\\d)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        return phrases.flatMap { splitPhrase(it, maxChars) }.map { it.trimEnd(',', ' ') }.filter { it.isNotBlank() }
    }

    private fun splitPhrase(p: String, maxChars: Int): List<String> {
        if (p.length <= maxChars) return listOf(p)
        val words = p.split(' ')
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for ((i, w) in words.withIndex()) {
            val candidate = if (cur.isEmpty()) w else "$cur $w"
            if (candidate.length > maxChars && cur.isNotEmpty()) {
                out += cur.toString(); cur.clear(); cur.append(w)
            } else {
                cur.clear(); cur.append(candidate)
            }
            // Prefer to break right after a connective ending when the line is reasonably full.
            val endsConnective = connectiveEndings.any { w.trimEnd(',', '.').endsWith(it) }
            if (endsConnective && cur.length >= maxChars * 0.55 && i < words.lastIndex) { out += cur.toString(); cur.clear() }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        // Hard-wrap any single long word (URLs, compound nouns).
        return out.flatMap { line -> if (line.length <= maxChars + 4) listOf(line) else line.chunked(maxChars) }
    }

    /** Distributes cues over [startMs, startMs+durationMs] proportional to text length. */
    fun timeCues(lines: List<String>, startMs: Long, durationMs: Long, gapMs: Long = 60): List<SubtitleCue> {
        if (lines.isEmpty() || durationMs <= 0) return emptyList()
        val total = lines.sumOf { it.length.coerceAtLeast(2) }.toDouble()
        var t = startMs.toDouble()
        return lines.map { line ->
            val d = durationMs * line.length.coerceAtLeast(2) / total
            val cue = SubtitleCue(text = line, startMs = t.toLong(), endMs = (t + d - gapMs).toLong().coerceAtLeast(t.toLong() + 200))
            t += d
            cue
        }
    }
}

/** Highlights only words that are backed by product facts (numbers+units, brand, key terms). */
object KeywordHighlighter {
    private val numberWithUnit = Regex("[0-9][0-9,.]*\\s*(시간|분|초|mAh|W|w|kg|g|mm|cm|m|L|ml|mL|인치|GB|TB|%|원|개|매|단|종|년|개월|Hz|K)")

    fun factTokens(product: Product): Set<String> {
        val facts = buildString {
            append(product.title).append(' ')
            product.brand?.let { append(it).append(' ') }
            product.specifications.forEach { append(it.name).append(' ').append(it.value).append(' ') }
            product.description?.let { append(it.take(2000)) }
        }
        val tokens = mutableSetOf<String>()
        numberWithUnit.findAll(facts).forEach { tokens += it.value.replace(" ", "") }
        product.brand?.takeIf { it.length >= 2 }?.let { tokens += it }
        return tokens
    }

    fun highlights(text: String, factTokens: Set<String>): List<String> {
        val compact = text.replace(" ", "")
        return numberWithUnit.findAll(text).map { it.value }.filter { it.replace(" ", "") in factTokens }.toList() +
            factTokens.filter { it.length >= 2 && !it.first().isDigit() && compact.contains(it) }
    }
}

object SrtIO {
    private fun ts(ms: Long) = "%02d:%02d:%02d,%03d".format(ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)

    fun export(cues: List<SubtitleCue>): String = buildString {
        cues.sortedBy { it.startMs }.forEachIndexed { i, c ->
            appendLine(i + 1); appendLine("${ts(c.startMs)} --> ${ts(c.endMs)}"); appendLine(c.text); appendLine()
        }
    }

    private val timeRe = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")

    fun parse(srt: String): List<SubtitleCue> {
        val out = mutableListOf<SubtitleCue>()
        srt.replace("\r", "").split(Regex("\\n\\s*\\n")).forEach { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            val idx = lines.indexOfFirst { timeRe.containsMatchIn(it) }
            if (idx < 0) return@forEach
            val m = timeRe.find(lines[idx])!!.groupValues
            fun ms(h: String, mi: String, s: String, f: String) = h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + f.padEnd(3, '0').toLong()
            val text = lines.drop(idx + 1).joinToString(" ").replace(Regex("<[^>]+>"), "").trim()
            if (text.isNotEmpty()) out += SubtitleCue(text = text, startMs = ms(m[1], m[2], m[3], m[4]), endMs = ms(m[5], m[6], m[7], m[8]))
        }
        return out
    }
}

/** Speech-to-text abstraction for subtitle timing sources. */
interface SubtitleSource {
    val id: String
    val label: String
    val available: Boolean
    val unavailableReason: String?
}

object SubtitleSources {
    val TTS_TIMING = object : SubtitleSource {
        override val id = "tts_timing"; override val label = "TTS 타이밍 기반"; override val available = true; override val unavailableReason = null
    }
    val IMPORTED_SRT = object : SubtitleSource {
        override val id = "srt"; override val label = "SRT 파일 가져오기"; override val available = true; override val unavailableReason = null
    }
    val LOCAL_STT = object : SubtitleSource {
        override val id = "local_stt"; override val label = "기기 내 음성 인식"; override val available = false
        override val unavailableReason = "Android SpeechRecognizer는 녹음 파일 전사를 지원하지 않아 v1.0에서는 비활성화했습니다."
    }
    val CLOUD_STT = object : SubtitleSource {
        override val id = "cloud_stt"; override val label = "클라우드 STT"; override val available = false
        override val unavailableReason = "외부 STT 제공자 키가 필요합니다 (향후 연결 가능한 구조)."
    }
    val all = listOf(TTS_TIMING, IMPORTED_SRT, LOCAL_STT, CLOUD_STT)
}
