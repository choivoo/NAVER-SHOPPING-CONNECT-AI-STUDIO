package com.shoppingconnect.aistudio.ai

import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.HookCandidate
import com.shoppingconnect.aistudio.domain.model.MotionEffect
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProductIntelligence
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.TitleCandidate
import com.shoppingconnect.aistudio.domain.model.TransitionType
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import kotlinx.serialization.Serializable

@Serializable
data class SectionDraft(val type: String = "paragraph", val text: String = "", val items: List<String> = emptyList())

@Serializable
data class WriterDraft(
    val hook: String = "",
    val sections: List<SectionDraft> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val usedFacts: List<String> = emptyList(),
)

@Serializable
data class CardDraft(
    val type: CardType = CardType.FEATURE,
    val title: String = "",
    val subtitle: String = "",
    val items: List<String> = emptyList(),
    val cta: String = "",
    val afterSection: Int = -1,
    val imageIndex: Int = -1,
)

@Serializable
data class VisualDraft(val visualTheme: String = "", val stylePreset: CardStylePreset = CardStylePreset.CLEAN, val cards: List<CardDraft> = emptyList())

@Serializable
data class SceneDraft(
    val purpose: String = "",
    val onScreenText: String = "",
    val narration: String = "",
    val durationSec: Double = 3.0,
    val imageIndex: Int = -1,
    val transition: TransitionType = TransitionType.FADE,
    val motion: MotionEffect = MotionEffect.KEN_BURNS_IN,
)

@Serializable
data class ShortformDraft(
    val hooks: List<HookCandidate> = emptyList(),
    val scenes: List<SceneDraft> = emptyList(),
    val bgmMood: BgmMood = BgmMood.UPBEAT,
    val thumbnailTexts: List<String> = emptyList(),
)

enum class RewriteAction(val label: String, val guide: String) {
    REWRITE("다시 쓰기", "같은 의미를 더 자연스럽고 읽기 쉽게 다시 쓰세요."),
    POLISH("선택 부분 다듬기", "문장을 매끄럽게 다듬고 어색한 표현을 고치세요."),
    SHORTEN("짧게", "핵심만 남겨 절반 정도 길이로 줄이세요."),
    EXPAND("길게", "상품 데이터 범위 안에서 설명을 보강해 1.5배 정도로 늘리세요. 새로운 사실은 추가하지 마세요."),
    FRIENDLY("친근하게", "친근하고 부드러운 말투로 바꾸세요."),
    PROFESSIONAL("전문적으로", "전문적이고 신뢰감 있는 말투로 바꾸세요."),
    FIX_TYPO("오탈자 수정", "맞춤법, 띄어쓰기, 오탈자만 고치고 나머지는 그대로 두세요."),
    SEO("SEO 개선", "주요 키워드를 자연스럽게 1회 포함하고 가독성을 높이세요. 키워드 나열 금지."),
    FACT_CHECK("Fact Check", "상품 데이터와 다른 사실이나 확인되지 않은 수치를 제거하거나 '판매 페이지에서 확인'으로 바꾸세요."),
    CTA("CTA", "과장 없이 링크 확인을 권하는 행동 유도 문장으로 바꾸세요."),
    HEADING("Heading", "이 내용을 대표하는 20자 이내 소제목 하나로 만드세요."),
}

@Serializable
data class ThumbnailTexts(val titles: List<String> = emptyList(), val hooks: List<String> = emptyList())

/** Everything the multi-agent pipeline needs from an AI backend. */
interface AiEngine {
    val isDemo: Boolean
    val modelLabel: String
    suspend fun analyzeProduct(product: Product): ProductIntelligence
    suspend fun strategy(product: Product, intel: ProductIntelligence, hint: ContentStrategy): ContentStrategy
    suspend fun write(product: Product, intel: ProductIntelligence, strategy: ContentStrategy, avoidPhrases: List<String>): WriterDraft
    suspend fun titles(product: Product, strategy: ContentStrategy, article: Article): List<TitleCandidate>
    suspend fun visualPlan(product: Product, article: Article, styleHint: CardStylePreset?): VisualDraft
    suspend fun shortform(product: Product, article: Article, durationSec: Int, template: ShortsTemplateId, usage: UsageStatus): ShortformDraft
    suspend fun hooks(product: Product): List<HookCandidate>
    suspend fun rewrite(product: Product, text: String, action: RewriteAction, usage: UsageStatus): String
    suspend fun compliance(product: Product, articleText: String, usage: UsageStatus): List<ContentIssue>
    suspend fun thumbnailTexts(product: Product): ThumbnailTexts
}
