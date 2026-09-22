package com.shoppingconnect.aistudio.ai.agents

import com.shoppingconnect.aistudio.ai.AiEngine
import com.shoppingconnect.aistudio.ai.RewriteAction
import com.shoppingconnect.aistudio.ai.ShortformDraft
import com.shoppingconnect.aistudio.ai.ThumbnailTexts
import com.shoppingconnect.aistudio.ai.VisualDraft
import com.shoppingconnect.aistudio.ai.WriterDraft
import com.shoppingconnect.aistudio.ai.claude.AiGateway
import com.shoppingconnect.aistudio.ai.claude.TaskWeight
import com.shoppingconnect.aistudio.ai.prompts.PromptName
import com.shoppingconnect.aistudio.ai.prompts.PromptRepository
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.ContentPreset
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.HookCandidate
import com.shoppingconnect.aistudio.domain.model.HookType
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProductIntelligence
import com.shoppingconnect.aistudio.domain.model.Severity
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.TitleCandidate
import com.shoppingconnect.aistudio.domain.model.TitleCategory
import com.shoppingconnect.aistudio.domain.model.Tone
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/** Serialises the verified product fact sheet (and nothing else) for prompts. */
fun Product.factJson(): String = AppJson.encodeToString(JsonElement.serializer(), toJson(factSheet()))

internal fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
    is Iterable<*> -> JsonArray(v.map { toJson(it) })
    else -> JsonPrimitive(v.toString())
}

fun usageRule(usage: UsageStatus): String = when (usage) {
    UsageStatus.USED -> "작성자는 이 제품을 실제로 사용했습니다. 다만 구체적인 사용 기간·결과는 작성자가 직접 보완할 수 있도록 일반적인 표현으로 두고, 확인되지 않은 효과를 단정하지 마세요."
    UsageStatus.NOT_USED, UsageStatus.INTRO_ONLY ->
        "작성자는 이 제품을 사용하지 않았습니다. \"제가 직접 써보니\", \"며칠 사용해봤는데\", \"직접 구매해서 써봤습니다\" 같은 경험 서술은 금지입니다. \"제품 정보를 기준으로 살펴보면\", \"공개된 상품 정보를 기준으로 보면\"처럼 작성하세요."
}

/**
 * Multi-agent implementation on top of Claude. Each agent is a separate structured call with
 * its own prompt template and JSON schema; results are exchanged as typed JSON.
 */
class ClaudeAiEngine(
    private val gateway: AiGateway,
    private val prompts: PromptRepository,
    private val settings: AppSettings,
) : AiEngine {
    override val isDemo = false
    override val modelLabel: String get() = gateway.lastMeta?.model ?: "Claude"

    private val v get() = settings.promptVersion

    private suspend fun system() = prompts.render(v, PromptName.SYSTEM)

    private inline fun <reified T> decode(obj: JsonObject): T = AppJson.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), obj)

    private fun nonEmptyArr(obj: JsonObject, key: String, min: Int = 1): List<String> {
        val arr = obj[key] as? JsonArray ?: return listOf("$key 누락")
        return if (arr.size < min) listOf("$key 항목이 ${min}개 미만") else emptyList()
    }

    override suspend fun analyzeProduct(product: Product): ProductIntelligence {
        val user = prompts.render(v, PromptName.PRODUCT_ANALYZER, mapOf("product_json" to product.factJson()))
        val schema = Schema.obj(
            "summary" to Schema.str(), "keyFeatures" to Schema.strArr(), "targetAudience" to Schema.strArr(),
            "useCases" to Schema.strArr(), "pros" to Schema.strArr(), "cautions" to Schema.strArr(),
            "comparisonPoints" to Schema.strArr(), "searchIntent" to Schema.str(), "keywords" to Schema.strArr(),
            "longTailKeywords" to Schema.strArr(), "titleIdeas" to Schema.strArr(), "contentAngles" to Schema.strArr(),
        )
        val obj = gateway.json(settings, TaskWeight.HEAVY, system(), user, schema, 6000) {
            nonEmptyArr(it, "keyFeatures") + nonEmptyArr(it, "keywords")
        }
        return decode(obj)
    }

    override suspend fun strategy(product: Product, intel: ProductIntelligence, hint: ContentStrategy): ContentStrategy {
        val user = prompts.render(
            v, PromptName.STRATEGY,
            mapOf(
                "preset_hint" to if (hint.preset == ContentPreset.CUSTOM) "없음(적절히 선택)" else hint.preset.name,
                "tone_hint" to hint.tone.name,
                "keyword_hint" to hint.primaryKeyword.ifBlank { "없음(적절히 선택)" },
                "usage" to hint.usage.label,
                "product_json" to product.factJson(),
                "intelligence_json" to AppJson.encodeToString(ProductIntelligence.serializer(), intel),
            ),
        )
        val schema = Schema.obj(
            "preset" to Schema.enumOf(ContentPreset.entries.filter { it != ContentPreset.CUSTOM }.map { it.name }),
            "tone" to Schema.enumOf<Tone>(), "primaryKeyword" to Schema.str(), "secondaryKeywords" to Schema.strArr(),
            "angle" to Schema.str(), "targetReader" to Schema.str(), "outline" to Schema.strArr(), "cta" to Schema.str(),
        )
        val obj = gateway.json(settings, TaskWeight.HEAVY, system(), user, schema, 4000) { nonEmptyArr(it, "outline", 3) }
        val dto = decode<StrategyDto>(obj)
        return hint.copy(
            preset = if (hint.preset == ContentPreset.CUSTOM) hint.preset else dto.preset,
            tone = hint.tone,
            primaryKeyword = hint.primaryKeyword.ifBlank { dto.primaryKeyword },
            secondaryKeywords = dto.secondaryKeywords,
            angle = dto.angle,
            targetReader = dto.targetReader,
            outline = dto.outline,
            cta = hint.cta.ifBlank { dto.cta },
        )
    }

    @Serializable
    private data class StrategyDto(
        val preset: ContentPreset = ContentPreset.INFO, val tone: Tone = Tone.FRIENDLY, val primaryKeyword: String = "",
        val secondaryKeywords: List<String> = emptyList(), val angle: String = "", val targetReader: String = "",
        val outline: List<String> = emptyList(), val cta: String = "",
    )

    override suspend fun write(product: Product, intel: ProductIntelligence, strategy: ContentStrategy, avoidPhrases: List<String>): WriterDraft {
        val brand = settings.brand
        val user = prompts.render(
            v, PromptName.BLOG_WRITER,
            mapOf(
                "target_chars" to strategy.length.targetChars.toString(),
                "tone" to strategy.tone.label,
                "preset" to "${strategy.preset.label} (${strategy.preset.guide})",
                "primary_keyword" to strategy.primaryKeyword,
                "usage" to strategy.usage.label,
                "usage_rule" to usageRule(strategy.usage),
                "avoid_phrases" to avoidPhrases.take(15).joinToString("\n") { "- $it" }.ifBlank { "(없음)" },
                "brand_profile" to "말투: ${brand.blogTone.label}" + (if (brand.signature.isNotBlank()) "\n맺음말 서명: ${brand.signature}" else "") +
                    (if (brand.bannedPhrases.isNotEmpty()) "\n쓰지 말 표현: ${brand.bannedPhrases.joinToString()}" else ""),
                "custom_instruction" to strategy.customInstruction.takeIf { it.isNotBlank() }?.let { "## 사용자 추가 요청\n<user_text>\n$it\n</user_text>" }.orEmpty(),
                "strategy_json" to AppJson.encodeToString(ContentStrategy.serializer(), strategy),
                "intelligence_json" to AppJson.encodeToString(ProductIntelligence.serializer(), intel),
                "product_json" to product.factJson(),
            ),
        )
        val section = Schema.obj("type" to Schema.enumOf(listOf("h2", "paragraph", "list", "quote")), "text" to Schema.str(), "items" to Schema.strArr())
        val schema = Schema.obj("hook" to Schema.str(), "sections" to Schema.arr(section), "hashtags" to Schema.strArr(), "usedFacts" to Schema.strArr())
        val obj = gateway.json(settings, TaskWeight.HEAVY, system(), user, schema, settings.maxOutputTokens) { o ->
            val errs = mutableListOf<String>()
            val secs = o["sections"] as? JsonArray
            if (secs == null || secs.size < 4) errs += "sections가 4개 미만"
            if ((o["hook"] as? JsonPrimitive)?.content.isNullOrBlank()) errs += "hook 누락"
            errs
        }
        return decode<WriterDraft>(obj).let { d -> d.copy(sections = d.sections.filter { it.text.isNotBlank() || it.items.isNotEmpty() }) }
    }

    override suspend fun titles(product: Product, strategy: ContentStrategy, article: Article): List<TitleCandidate> {
        val summary = article.blocks.filter { it.type == BlockType.H2 }.joinToString(" / ") { it.text }
        val user = prompts.render(
            v, PromptName.TITLES,
            mapOf("usage" to strategy.usage.label, "primary_keyword" to strategy.primaryKeyword, "product_json" to product.factJson(), "article_summary" to summary),
        )
        val item = Schema.obj("text" to Schema.str(), "category" to Schema.enumOf<TitleCategory>())
        val schema = Schema.obj("titles" to Schema.arr(item))
        val obj = gateway.json(settings, TaskWeight.LIGHT, system(), user, schema, 3000) { nonEmptyArr(it, "titles", 10) }
        return decode<TitlesDto>(obj).titles.filter { it.text.isNotBlank() }.map { TitleCandidate(it.text.trim(), it.category) }
    }

    @Serializable private data class TitleDto(val text: String = "", val category: TitleCategory = TitleCategory.CLEAN)
    @Serializable private data class TitlesDto(val titles: List<TitleDto> = emptyList())

    override suspend fun visualPlan(product: Product, article: Article, styleHint: CardStylePreset?): VisualDraft {
        val sections = article.blocks.filter { it.type == BlockType.H2 }.mapIndexed { i, b -> "$i. ${b.text}" }.joinToString("\n")
        val user = prompts.render(
            v, PromptName.VISUAL_STRATEGY,
            mapOf("style_hint" to (styleHint?.name ?: "없음"), "sections" to sections, "image_count" to product.images.size.toString(), "product_json" to product.factJson()),
        )
        val card = Schema.obj(
            "type" to Schema.enumOf(listOf("HERO", "FEATURE", "SPEC", "PROS", "CHECK", "TARGET", "CTA")),
            "title" to Schema.str(), "subtitle" to Schema.str(), "items" to Schema.strArr(), "cta" to Schema.str(),
            "afterSection" to Schema.int(), "imageIndex" to Schema.int(),
        )
        val schema = Schema.obj("visualTheme" to Schema.str(), "stylePreset" to Schema.enumOf<CardStylePreset>(), "cards" to Schema.arr(card))
        val obj = gateway.json(settings, TaskWeight.HEAVY, system(), user, schema, 4000) { nonEmptyArr(it, "cards", 3) }
        val d = decode<VisualDraft>(obj)
        return d.copy(stylePreset = styleHint ?: d.stylePreset)
    }

    override suspend fun shortform(product: Product, article: Article, durationSec: Int, template: ShortsTemplateId, usage: UsageStatus): ShortformDraft {
        val summary = article.plainText().take(1500)
        val user = prompts.render(
            v, PromptName.SHORTFORM,
            mapOf(
                "duration" to durationSec.toString(), "template" to template.label, "syllables_per_sec" to "4.5",
                "image_count" to product.images.size.toString(), "usage" to usage.label,
                "product_json" to product.factJson(), "article_summary" to summary,
            ),
        )
        val scene = Schema.obj(
            "purpose" to Schema.str(), "onScreenText" to Schema.str(), "narration" to Schema.str(), "durationSec" to Schema.num(),
            "imageIndex" to Schema.int(),
            "transition" to Schema.enumOf(listOf("NONE", "FADE", "CROSS_FADE", "SLIDE_LEFT", "SLIDE_UP", "ZOOM_IN", "ZOOM_OUT", "WIPE", "FLASH")),
            "motion" to Schema.enumOf(listOf("KEN_BURNS_IN", "KEN_BURNS_OUT", "PAN_LEFT", "PAN_RIGHT", "PULSE", "NONE")),
        )
        val hook = Schema.obj("text" to Schema.str(), "type" to Schema.enumOf<HookType>())
        val schema = Schema.obj(
            "hooks" to Schema.arr(hook), "scenes" to Schema.arr(scene),
            "bgmMood" to Schema.enumOf(BgmMood.entries.filter { it != BgmMood.NONE }.map { it.name }),
            "thumbnailTexts" to Schema.strArr(),
        )
        val obj = gateway.json(settings, TaskWeight.HEAVY, system(), user, schema, 6000) { nonEmptyArr(it, "scenes", 4) + nonEmptyArr(it, "hooks", 3) }
        return decode(obj)
    }

    override suspend fun hooks(product: Product): List<HookCandidate> {
        val user = prompts.render(v, PromptName.HOOKS, mapOf("product_json" to product.factJson()))
        val schema = Schema.obj("hooks" to Schema.arr(Schema.obj("text" to Schema.str(), "type" to Schema.enumOf<HookType>())))
        val obj = gateway.json(settings, TaskWeight.LIGHT, system(), user, schema, 1500) { nonEmptyArr(it, "hooks", 3) }
        return decode<HooksDto>(obj).hooks
    }

    @Serializable private data class HooksDto(val hooks: List<HookCandidate> = emptyList())

    override suspend fun rewrite(product: Product, text: String, action: RewriteAction, usage: UsageStatus): String {
        val user = prompts.render(
            v, PromptName.REWRITE,
            mapOf("action" to action.label, "action_guide" to action.guide, "usage" to usage.label, "product_json" to product.factJson(), "text" to text.take(6000)),
        )
        val weight = if (action == RewriteAction.FIX_TYPO || action == RewriteAction.HEADING) TaskWeight.LIGHT else TaskWeight.HEAVY
        return gateway.text(settings, weight, system(), user, 3000).trim().removeSurrounding("\"")
    }

    override suspend fun compliance(product: Product, articleText: String, usage: UsageStatus): List<ContentIssue> {
        val user = prompts.render(v, PromptName.COMPLIANCE, mapOf("usage" to usage.label, "product_json" to product.factJson(), "article_text" to articleText.take(20000)))
        val issue = Schema.obj(
            "code" to Schema.enumOf(listOf("FAKE_EXPERIENCE", "EXAGGERATION", "UNVERIFIED_FACT", "MISSING_DISCLOSURE", "OTHER")),
            "severity" to Schema.enumOf<Severity>(), "excerpt" to Schema.str(), "message" to Schema.str(), "suggestion" to Schema.str(),
        )
        val schema = Schema.obj("issues" to Schema.arr(issue))
        val obj = gateway.json(settings, TaskWeight.LIGHT, system(), user, schema, 3000) { o -> if (o["issues"] !is JsonArray) listOf("issues 누락") else emptyList() }
        return (obj["issues"]?.jsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
            runCatching { AppJson.decodeFromJsonElement(IssueDto.serializer(), el) }.getOrNull()
        }.map { ContentIssue("AI_${it.code}", it.severity, it.message, it.suggestion.ifBlank { null }, excerpt = it.excerpt) }
    }

    @Serializable private data class IssueDto(val code: String = "OTHER", val severity: Severity = Severity.WARNING, val excerpt: String = "", val message: String = "", val suggestion: String = "")

    override suspend fun thumbnailTexts(product: Product): ThumbnailTexts {
        val user = prompts.render(v, PromptName.THUMBNAIL, mapOf("product_json" to product.factJson()))
        val schema = Schema.obj("titles" to Schema.strArr(), "hooks" to Schema.strArr())
        val obj = gateway.json(settings, TaskWeight.LIGHT, system(), user, schema, 1500) { nonEmptyArr(it, "titles", 3) }
        return decode(obj)
    }
}
