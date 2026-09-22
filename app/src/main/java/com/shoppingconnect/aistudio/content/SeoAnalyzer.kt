package com.shoppingconnect.aistudio.content

import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.QualityCheck
import com.shoppingconnect.aistudio.domain.model.QualityReport

/**
 * "Content Quality Score" — a writing-quality aid, NOT a search ranking prediction.
 * Every check is deterministic and explained to the user.
 */
object SeoAnalyzer {
    fun analyze(article: Article, primaryKeyword: String, imageCount: Int): QualityReport {
        val text = article.plainText()
        val chars = text.length
        val kw = primaryKeyword.trim()
        val kwCount = if (kw.isBlank()) 0 else KoreanText.countOccurrences(text, kw)
        val density = if (chars == 0 || kw.isBlank()) 0.0 else kwCount * kw.length * 100.0 / chars
        val paragraphs = article.blocks.filter { it.type == BlockType.PARAGRAPH }
        val longParas = paragraphs.count { it.text.length > 260 }
        val headings = article.blocks.count { it.type == BlockType.H2 || it.type == BlockType.H1 }
        val titleLen = article.title.length
        val avgSentence = KoreanText.sentences(text).map { it.length }.average().takeIf { !it.isNaN() } ?: 0.0
        val hasDisclosure = article.blocks.any { it.type == BlockType.DISCLOSURE && it.text.isNotBlank() }

        val checks = listOf(
            QualityCheck("제목 길이", titleLen in 15..40, "현재 ${titleLen}자 (권장 15~40자)", 2),
            QualityCheck("제목에 핵심 키워드", kw.isNotBlank() && article.title.contains(kw, ignoreCase = true), if (kw.isBlank()) "핵심 키워드 미설정" else "키워드: $kw", 2),
            QualityCheck("핵심 키워드 사용", kwCount in 2..8, "본문 ${kwCount}회 (권장 2~8회)", 2),
            QualityCheck("키워드 과다 사용 없음", density <= 3.5, "밀도 %.1f%% (3.5%% 이하 권장)".format(density), 2),
            QualityCheck("본문 분량", chars >= 900, "${chars}자 (900자 이상 권장)", 2),
            QualityCheck("문단 길이", longParas == 0, if (longParas == 0) "모든 문단이 모바일에서 읽기 좋은 길이" else "긴 문단 ${longParas}개 — 나누기를 권장", 2),
            QualityCheck("소제목 구성", headings >= 3, "소제목 ${headings}개 (3개 이상 권장)", 1),
            QualityCheck("가독성(문장 길이)", avgSentence in 1.0..70.0, "평균 문장 길이 %.0f자".format(avgSentence), 1),
            QualityCheck("이미지 수", imageCount >= 3, "이미지/카드 ${imageCount}개 (3개 이상 권장)", 2),
            QualityCheck("설명 완성도", article.blocks.any { it.type == BlockType.PRODUCT_CARD } && article.blocks.any { it.type == BlockType.LINK }, "상품 정보 요약과 링크 포함 여부", 1),
            QualityCheck("해시태그", article.hashtags.size in 3..15, "${article.hashtags.size}개 (3~15개 권장)", 1),
            QualityCheck("광고/제휴 표시", hasDisclosure, if (hasDisclosure) "포함됨" else "누락 — 게시 전 반드시 추가", 3),
        )
        val total = checks.sumOf { it.weight }
        val got = checks.filter { it.passed }.sumOf { it.weight }
        return QualityReport(score = if (total == 0) 0 else got * 100 / total, checks = checks)
    }
}
