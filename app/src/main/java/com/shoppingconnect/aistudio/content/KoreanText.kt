package com.shoppingconnect.aistudio.content

object KoreanText {
    fun sentences(text: String): List<String> =
        text.replace("\n", " ").split(Regex("(?<=[.!?。…])(?!\\d)\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Splits a paragraph that is too long for mobile reading at sentence boundaries. */
    fun splitParagraph(text: String, maxChars: Int = 220): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (s in sentences(text)) {
            if (cur.isNotEmpty() && cur.length + s.length + 1 > maxChars) {
                out += cur.toString().trim(); cur.clear()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        if (cur.isNotEmpty()) out += cur.toString().trim()
        return out
    }

    fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isBlank()) return 0
        var i = 0; var n = 0
        val h = haystack.lowercase(); val k = needle.lowercase()
        while (true) { i = h.indexOf(k, i); if (i < 0) break; n++; i += k.length }
        return n
    }

    fun shingles(text: String, n: Int = 3): Set<String> {
        val t = text.replace(Regex("[\\s\\p{Punct}]+"), "")
        if (t.length < n) return setOf(t)
        return (0..t.length - n).map { t.substring(it, it + n) }.toSet()
    }

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }
}
