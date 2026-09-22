package com.shoppingconnect.aistudio.domain.model

import com.shoppingconnect.aistudio.core.common.newId
import kotlinx.serialization.Serializable

enum class ContentPreset(val label: String, val guide: String) {
    INFO("정보형", "제품을 객관적으로 소개"),
    COMPARISON("비교형", "제품 선택 기준 중심"),
    GUIDE("사용 가이드형", "어떤 상황에 적합한지 설명"),
    TOP_POINTS("TOP 포인트형", "핵심 특징 중심"),
    SEO("SEO형", "검색 키워드를 자연스럽게 반영"),
    CUSTOM("직접 설정", "사용자 지정"),
}

enum class Tone(val label: String) { FRIENDLY("친근함"), PROFESSIONAL("전문적"), CONCISE("간결함"), DETAILED("상세함"), CASUAL("캐주얼"), INFORMATIVE("정보형") }

enum class ArticleLength(val label: String, val targetChars: Int) { SHORT("짧게", 1200), MEDIUM("보통", 2200), LONG("길게", 3500) }

/** Whether the creator actually used the product — drives the fake-review guard. */
enum class UsageStatus(val label: String) { USED("사용함"), NOT_USED("사용하지 않음"), INTRO_ONLY("단순 소개 콘텐츠") }

@Serializable
data class ContentStrategy(
    val preset: ContentPreset = ContentPreset.INFO,
    val tone: Tone = Tone.FRIENDLY,
    val length: ArticleLength = ArticleLength.MEDIUM,
    val usage: UsageStatus = UsageStatus.INTRO_ONLY,
    val primaryKeyword: String = "",
    val secondaryKeywords: List<String> = emptyList(),
    val angle: String = "",
    val targetReader: String = "",
    val outline: List<String> = emptyList(),
    val customInstruction: String = "",
    val cta: String = "",
)

enum class BlockType(val label: String) {
    H1("제목1"), H2("제목2"), PARAGRAPH("본문"), QUOTE("인용"), LIST("목록"), IMAGE("이미지"),
    DIVIDER("구분선"), LINK("링크"), PRODUCT_CARD("상품 카드"), CTA("CTA"), DISCLOSURE("광고 표시"),
}

/** One editor block. Paragraph text supports **bold** inline markup. */
@Serializable
data class Block(
    val id: String = newId(),
    val type: BlockType,
    val text: String = "",
    val items: List<String> = emptyList(),
    val assetId: String? = null,
    val url: String? = null,
    val aiGenerated: Boolean = true,
)

enum class TitleCategory(val label: String) { SEO("SEO"), CLICK("클릭형"), CLEAN("깔끔형"), INFO("정보형"), SHORT("짧은 제목") }

@Serializable
data class TitleCandidate(val text: String, val category: TitleCategory, val id: String = newId())

@Serializable
data class Article(
    val title: String = "",
    val titleCandidates: List<TitleCandidate> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val disclosure: String = "",
    val factsCheckedAt: Long = 0,
    val sources: List<SourceRef> = emptyList(),
) {
    fun plainText(): String = buildString {
        blocks.forEach { b ->
            when (b.type) {
                BlockType.LIST -> b.items.forEach { appendLine("• $it") }
                BlockType.IMAGE, BlockType.DIVIDER -> Unit
                else -> if (b.text.isNotBlank()) appendLine(b.text.replace("**", ""))
            }
            appendLine()
        }
    }.trim()

    val charCount: Int get() = plainText().length
}

@Serializable
data class SourceRef(val label: String, val url: String, val checkedAt: Long)

enum class Severity { INFO, WARNING, ERROR }

@Serializable
data class ContentIssue(
    val code: String,
    val severity: Severity,
    val message: String,
    val suggestion: String? = null,
    val blockId: String? = null,
    val excerpt: String? = null,
)

@Serializable
data class QualityReport(
    val score: Int,
    val checks: List<QualityCheck>,
)

@Serializable
data class QualityCheck(val name: String, val passed: Boolean, val detail: String, val weight: Int = 1)
