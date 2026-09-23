package com.shoppingconnect.aistudio.ai

import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.ai.agents.Schema
import com.shoppingconnect.aistudio.ai.agents.factJson
import com.shoppingconnect.aistudio.content.ArticleAssembler
import com.shoppingconnect.aistudio.content.ComplianceChecker
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.MotionEffect
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.pipeline.DemoData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class DemoEngineAndSchemaTest {
    private val product = DemoData.product()

    @Test fun demoPipelineProducesCompliantArticle() = runTest {
        val e = DemoAiEngine()
        val intel = e.analyzeProduct(product)
        val strategy = e.strategy(product, intel, ContentStrategy(usage = UsageStatus.NOT_USED))
        val draft = e.write(product, intel, strategy, emptyList())
        val article = ArticleAssembler.assemble(draft, product, strategy, AppSettings())
        assertThat(article.blocks.count { it.type == BlockType.DISCLOSURE }).isEqualTo(2)
        assertThat(article.blocks.any { it.type == BlockType.PRODUCT_CARD }).isTrue()
        assertThat(article.blocks.first { it.type == BlockType.LINK }.url).isEqualTo(product.affiliateUrl)
        assertThat(ComplianceChecker.check(article.copy(title = "정리"), UsageStatus.NOT_USED, true).filter { it.code == "FAKE_EXPERIENCE" }).isEmpty()
        assertThat(e.titles(product, strategy, article).size).isAtLeast(10)
    }

    @Test fun demoShortformMatchesTargetDuration() = runTest {
        val d = DemoAiEngine().shortform(product, Article(), 45, ShortsTemplateId.DYNAMIC, UsageStatus.INTRO_ONLY)
        assertThat(d.scenes.sumOf { it.durationSec }).isWithin(0.01).of(45.0)
        assertThat(d.hooks).hasSize(5)
    }

    @Test fun schemaObjectsAreClosedAndFullyRequired() {
        val s = Schema.obj("a" to Schema.str(), "b" to Schema.arr(Schema.obj("c" to Schema.int())))
        assertThat(s["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat((s["required"] as JsonArray).map { it.jsonPrimitive.content }).containsExactly("a", "b")
        val inner = ((s["properties"] as JsonObject)["b"] as JsonObject)["items"] as JsonObject
        assertThat(inner["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
    }

    @Test fun agentJsonDecodesWithUnknownEnumsCoerced() {
        val json = """{"purpose":"Hook","onScreenText":"안녕","narration":"n","durationSec":2,"imageIndex":0,"transition":"TELEPORT","motion":"PAN_LEFT","extra":1}"""
        val s = AppJson.decodeFromString(SceneDraft.serializer(), json)
        assertThat(s.motion).isEqualTo(MotionEffect.PAN_LEFT)
        assertThat(s.transition).isEqualTo(com.shoppingconnect.aistudio.domain.model.TransitionType.FADE) // default
    }

    @Test fun factJsonContainsOnlyVerifiedFields() {
        val p = product.copy(seller = "비공개 판매자", verifiedFields = product.verifiedFields - "brand")
        val j = p.factJson()
        assertThat(j).doesNotContain("비공개 판매자")
        assertThat(j).doesNotContain("데모브랜드")
        assertThat(j).contains("unknownFields")
    }
}
