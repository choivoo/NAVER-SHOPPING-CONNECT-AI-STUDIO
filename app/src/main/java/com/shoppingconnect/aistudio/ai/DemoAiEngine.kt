package com.shoppingconnect.aistudio.ai

import com.shoppingconnect.aistudio.core.common.formatPrice
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.ContentPreset
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.HookCandidate
import com.shoppingconnect.aistudio.domain.model.HookType
import com.shoppingconnect.aistudio.domain.model.MotionEffect
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProductIntelligence
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.TitleCandidate
import com.shoppingconnect.aistudio.domain.model.TitleCategory
import com.shoppingconnect.aistudio.domain.model.TransitionType
import com.shoppingconnect.aistudio.domain.model.UsageStatus

/**
 * DEMO MODE engine — deterministic templates built only from the verified product facts.
 * It lets the whole UI (editor, cards, shorts, render) be exercised without an API key.
 * Output is always labelled as demo content in the UI and never presented as AI output.
 */
class DemoAiEngine : AiEngine {
    override val isDemo = true
    override val modelLabel = "DEMO (템플릿)"

    private fun Product.shortName() = title.removePrefix("[데모]").trim().split(" ").take(4).joinToString(" ")
    private fun Product.specLines() = specifications.take(6).map { "${it.name}: ${it.value}" }

    override suspend fun analyzeProduct(product: Product): ProductIntelligence {
        val specs = product.specLines()
        val name = product.shortName()
        return ProductIntelligence(
            summary = "${product.brand?.let { "$it " } ?: ""}$name 제품 정보를 정리한 데모 분석입니다.",
            keyFeatures = specs.ifEmpty { listOf("상품 상세 정보는 판매 페이지에서 확인 필요") },
            targetAudience = listOfNotNull(product.category?.let { "$it 제품을 찾는 분" }, "구매 전 핵심 정보를 빠르게 확인하고 싶은 분"),
            useCases = listOf("일상 사용", "선물용 검토"),
            pros = specs.take(3).ifEmpty { listOf("판매 페이지에서 상세 스펙 확인 가능") },
            cautions = listOf("옵션(색상/사이즈) 확인", "배송 및 교환 조건 확인", "가격은 시점에 따라 변동 가능"),
            comparisonPoints = listOf("가격", "구성품", "사용 편의성"),
            searchIntent = "$name 구매 전 정보 확인",
            keywords = listOfNotNull(name, product.brand, product.category).distinct(),
            longTailKeywords = listOf("$name 특징", "$name 구매 전 확인", "$name 정보"),
            titleIdeas = listOf("$name 특징 정리", "$name 구매 전 확인할 점"),
            contentAngles = listOf("정보 정리형"),
        )
    }

    override suspend fun strategy(product: Product, intel: ProductIntelligence, hint: ContentStrategy): ContentStrategy = hint.copy(
        preset = if (hint.preset == ContentPreset.CUSTOM) ContentPreset.INFO else hint.preset,
        primaryKeyword = hint.primaryKeyword.ifBlank { product.shortName() },
        secondaryKeywords = intel.longTailKeywords.take(3),
        angle = "공개된 상품 정보를 기준으로 핵심만 정리",
        targetReader = intel.targetAudience.firstOrNull().orEmpty(),
        outline = listOf("어떤 제품인가요", "주요 특징", "이런 분께 맞아요", "구매 전 확인할 점", "정리"),
        cta = hint.cta.ifBlank { "현재 가격과 옵션은 아래 링크에서 확인해 보세요." },
    )

    override suspend fun write(product: Product, intel: ProductIntelligence, strategy: ContentStrategy, avoidPhrases: List<String>): WriterDraft {
        val name = product.shortName()
        val price = if (product.isVerified(Product.F_PRICE)) formatPrice(product.price, product.currency) else null
        return WriterDraft(
            hook = "[데모] $name, 어떤 제품인지 공개된 상품 정보를 기준으로 정리해 봤어요.",
            sections = listOf(
                SectionDraft("h2", "어떤 제품인가요"),
                SectionDraft("paragraph", "${product.brand?.let { "**$it**의 " } ?: ""}$name 제품입니다. " +
                    (product.category?.let { "$it 카테고리 상품이에요. " } ?: "") +
                    (price?.let { "작성 시점 기준 판매가는 $price 입니다." } ?: "가격은 판매 페이지에서 확인해 주세요.")),
                SectionDraft("h2", "주요 특징"),
                SectionDraft("list", "", intel.keyFeatures.take(5)),
                SectionDraft("h2", "이런 분께 맞아요"),
                SectionDraft("list", "", intel.targetAudience),
                SectionDraft("h2", "구매 전 확인할 점"),
                SectionDraft("paragraph", "제품 정보를 기준으로 살펴보면, 구매 전에 아래 내용을 판매 페이지에서 한 번 더 확인하는 것이 좋아요."),
                SectionDraft("list", "", intel.cautions),
                SectionDraft("h2", "정리"),
                SectionDraft("paragraph", "$name 의 공개 정보를 정리했어요. 실제 구매 전에는 최신 가격과 옵션을 꼭 확인해 주세요."),
            ),
            hashtags = (intel.keywords + listOf("상품정보", "쇼핑")).map { it.replace(" ", "") }.distinct().take(8),
            usedFacts = listOfNotNull(price),
        )
    }

    override suspend fun titles(product: Product, strategy: ContentStrategy, article: Article): List<TitleCandidate> {
        val n = product.shortName()
        val k = strategy.primaryKeyword.ifBlank { n }
        return listOf(
            TitleCandidate("$k 특징과 구매 전 확인할 점", TitleCategory.SEO),
            TitleCandidate("$k 정보 한눈에 정리", TitleCategory.SEO),
            TitleCandidate("$n, 어떤 분께 맞을까?", TitleCategory.CLICK),
            TitleCandidate("$n 살까 말까 고민이라면", TitleCategory.CLICK),
            TitleCandidate("$n 제품 정보 정리", TitleCategory.CLEAN),
            TitleCandidate("$n 핵심만 깔끔하게", TitleCategory.CLEAN),
            TitleCandidate("$n 스펙·옵션 정리", TitleCategory.INFO),
            TitleCandidate("$n 구매 전 체크리스트", TitleCategory.INFO),
            TitleCandidate("$n 요약", TitleCategory.SHORT),
            TitleCandidate("$n 핵심 정리", TitleCategory.SHORT),
        )
    }

    override suspend fun visualPlan(product: Product, article: Article, styleHint: CardStylePreset?): VisualDraft {
        val n = product.shortName()
        val intel = analyzeProduct(product)
        return VisualDraft(
            visualTheme = "깔끔한 정보 카드",
            stylePreset = styleHint ?: CardStylePreset.CLEAN,
            cards = listOf(
                CardDraft(CardType.HERO, n.take(25), product.brand ?: "상품 정보 정리", afterSection = -1, imageIndex = 0),
                CardDraft(CardType.FEATURE, "핵심 특징", items = intel.keyFeatures.take(3), afterSection = 1, imageIndex = if (product.images.size > 1) 1 else 0),
                CardDraft(CardType.SPEC, "제품 정보", afterSection = 0),
                CardDraft(CardType.TARGET, "이런 분께 잘 맞아요", items = intel.targetAudience.take(3), afterSection = 2),
                CardDraft(CardType.CHECK, "구매 전 확인하세요", items = intel.cautions.take(3), afterSection = 3),
                CardDraft(CardType.CTA, "최신 가격은 링크에서 확인", cta = "상품 보러가기", afterSection = 4, imageIndex = 0),
            ),
        )
    }

    override suspend fun shortform(product: Product, article: Article, durationSec: Int, template: ShortsTemplateId, usage: UsageStatus): ShortformDraft {
        val n = product.shortName()
        val intel = analyzeProduct(product)
        val f = intel.keyFeatures + listOf("판매 페이지에서 상세 확인")
        val base = listOf(
            SceneDraft("Hook", "이거 알고 사세요", "$n, 사기 전에 이것만 확인하세요.", 2.0, 0, TransitionType.NONE, MotionEffect.KEN_BURNS_IN),
            SceneDraft("제품 소개", n.take(16), "${product.brand ?: ""} $n 제품이에요.", 4.0, 0, TransitionType.FADE, MotionEffect.PAN_LEFT),
            SceneDraft("특징 1", f[0].take(16), f[0], 7.0, if (product.images.size > 1) 1 else 0, TransitionType.SLIDE_LEFT, MotionEffect.KEN_BURNS_IN),
            SceneDraft("특징 2", f.getOrElse(1) { f[0] }.take(16), f.getOrElse(1) { f[0] }, 7.0, if (product.images.size > 2) 2 else 0, TransitionType.SLIDE_LEFT, MotionEffect.KEN_BURNS_OUT),
            SceneDraft("추천 대상", "이런 분께 추천", intel.targetAudience.firstOrNull() ?: "구매를 고민 중인 분께 추천해요.", 5.0, 0, TransitionType.FADE, MotionEffect.PULSE),
            SceneDraft("CTA", "링크에서 확인", "최신 가격과 옵션은 링크에서 확인하세요.", 5.0, -1, TransitionType.ZOOM_IN, MotionEffect.NONE),
        )
        val scale = durationSec / base.sumOf { it.durationSec }
        return ShortformDraft(
            hooks = listOf(
                HookCandidate("이거 모르고 사면 아쉬워요", HookType.PROBLEM),
                HookCandidate("$n, 뭐가 다를까?", HookType.CURIOSITY),
                HookCandidate("비슷한 제품과 비교 포인트", HookType.COMPARISON),
                HookCandidate("핵심만 30초 정리", HookType.BENEFIT),
                HookCandidate("$n 어떤 분께 맞을까?", HookType.QUESTION),
            ),
            scenes = base.map { it.copy(durationSec = it.durationSec * scale) },
            bgmMood = BgmMood.UPBEAT,
            thumbnailTexts = listOf("구매 전 체크", "핵심 정리", "30초 요약", n.take(12), "이것만 보세요"),
        )
    }

    override suspend fun hooks(product: Product): List<HookCandidate> = shortform(product, Article(), 30, ShortsTemplateId.CLEAN_PRODUCT, UsageStatus.INTRO_ONLY).hooks

    override suspend fun rewrite(product: Product, text: String, action: RewriteAction, usage: UsageStatus): String = when (action) {
        RewriteAction.SHORTEN -> text.split(Regex("(?<=[.!?。])\\s+")).firstOrNull()?.trim() ?: text
        RewriteAction.HEADING -> text.replace("**", "").take(20)
        RewriteAction.FIX_TYPO -> text.replace(Regex("\\s{2,}"), " ").trim()
        RewriteAction.CTA -> "자세한 정보와 현재 가격은 아래 링크에서 확인해 보세요."
        else -> text
    }

    override suspend fun compliance(product: Product, articleText: String, usage: UsageStatus): List<ContentIssue> = emptyList()

    override suspend fun thumbnailTexts(product: Product): ThumbnailTexts =
        ThumbnailTexts(listOf("구매 전 체크", "핵심 정리", "30초 요약", product.shortName().take(12), "이것만 보세요"), listOf("사기 전에 확인하세요", "핵심만 정리했어요"))
}
