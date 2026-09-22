package com.shoppingconnect.aistudio.content

import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.Severity

/** Content Memory + Duplicate Detector over the user's own previous articles (local only). */
object ContentMemory {
    /** Sentences/phrases the user has used in 2+ previous articles — fed to the writer as "avoid". */
    fun frequentPhrases(previous: List<Article>, limit: Int = 15): List<String> {
        val counts = HashMap<String, Int>()
        previous.forEach { a ->
            a.blocks.filter { it.type == BlockType.PARAGRAPH }
                .flatMap { KoreanText.sentences(it.text) }
                .map { it.replace("**", "").trim() }
                .filter { it.length in 10..60 }
                .map { it.take(24) }
                .toSet()
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    data class DuplicateResult(val projectTitle: String, val similarity: Double)

    fun mostSimilar(current: Article, others: List<Pair<String, Article>>): DuplicateResult? {
        val cur = KoreanText.shingles(current.plainText())
        return others.map { (title, a) -> DuplicateResult(title, KoreanText.jaccard(cur, KoreanText.shingles(a.plainText()))) }
            .maxByOrNull { it.similarity }
    }

    fun duplicateIssue(result: DuplicateResult?, threshold: Double = 0.45): ContentIssue? {
        if (result == null || result.similarity < threshold) return null
        return ContentIssue(
            "DUPLICATE", Severity.WARNING,
            "이전 글 \"${result.projectTitle}\"과(와) 유사도 ${(result.similarity * 100).toInt()}%",
            "도입부와 표현을 바꿔 중복 콘텐츠를 피하세요.",
        )
    }

    /** Sentences within one article that repeat nearly verbatim. */
    fun repeatedSentences(article: Article): List<String> {
        val seen = HashMap<String, Int>()
        article.blocks.filter { it.type == BlockType.PARAGRAPH }.flatMap { KoreanText.sentences(it.text) }.forEach {
            val k = it.replace(Regex("\\s+"), "").take(30)
            if (k.length >= 12) seen[k] = (seen[k] ?: 0) + 1
        }
        return seen.filter { it.value > 1 }.keys.toList()
    }
}
