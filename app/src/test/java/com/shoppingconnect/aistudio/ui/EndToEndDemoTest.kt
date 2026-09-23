package com.shoppingconnect.aistudio.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.MainActivity
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.ai.claude.AiGateway
import com.shoppingconnect.aistudio.ai.claude.ClaudeClient
import com.shoppingconnect.aistudio.ai.claude.ModelResolver
import com.shoppingconnect.aistudio.ai.prompts.PromptRepository
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.core.network.SafeFetcher
import com.shoppingconnect.aistudio.core.security.InMemorySecretStore
import com.shoppingconnect.aistudio.data.db.AppDatabase
import com.shoppingconnect.aistudio.data.db.GenerationEntity
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.InMemorySettingsRepository
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.PipelineOptions
import com.shoppingconnect.aistudio.domain.model.StepState
import com.shoppingconnect.aistudio.media.shorts.ShortsService
import com.shoppingconnect.aistudio.media.tts.AndroidTtsProvider
import com.shoppingconnect.aistudio.pipeline.DemoData
import com.shoppingconnect.aistudio.pipeline.Notifier
import com.shoppingconnect.aistudio.pipeline.PipelineOrchestrator
import com.shoppingconnect.aistudio.product.ProductExtractionService
import com.shoppingconnect.aistudio.visual.VisualService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Full pipeline on the Demo product (no network, no API key): analysis → verification → strategy →
 * writing → visual cards (real Canvas rendering) → shorts draft → QC. Then the real app UI is
 * opened on the result and screenshots are written to docs/screenshots for visual QA.
 */
@RunWith(RobolectricTestRunner::class)
class EndToEndDemoTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val composeRule = createAndroidComposeRule<MainActivity>()
    val rule get() = composeRule

    /** Seeds the database (running the whole pipeline) before the Activity is launched. */
    private val seedRule = object : org.junit.rules.TestRule {
        override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description) = object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
                seedDemoProject()
                base.evaluate()
            }
        }
    }

    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(seedRule).around(composeRule)

    private fun seedDemoProject(): String = runBlocking {
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME).allowMainThreadQueries().build()
        val files = ProjectFiles(ctx)
        val projects = ProjectRepository(db, files)
        val http = OkHttpClient()
        val media = MediaRepository(ctx, db.assets(), files, SafeFetcher(http))
        val settings = InMemorySettingsRepository(AppSettings(onboardingDone = true, voiceEnabled = false))
        val client = ClaudeClient(http, InMemorySecretStore())
        val gateway = AiGateway(client, ModelResolver(client))
        val prompts = PromptRepository(ctx, db.prompts())
        val orchestrator = PipelineOrchestrator(
            projects, db.generations(), media, ProductExtractionService(SafeFetcher(http)),
            AiEngineProvider(settings, client, gateway, prompts), settings, VisualService(media),
            ShortsService(media, AndroidTtsProvider(ctx), files), gateway, Notifier(ctx),
        )
        val product = DemoData.product()
        val project = projects.createProject(product, "v1")
        media.saveBitmap(project.id, DemoData.illustration(), AssetKind.ORIGINAL, "demo", copyright = CopyrightType.APP_GENERATED)
        val gen = GenerationEntity(
            "gen-e2e", project.id, product.affiliateUrl, GenerationState.QUEUED,
            AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.shoppingconnect.aistudio.domain.model.StepStatus.serializer()), orchestrator.initialSteps()),
            AppJson.encodeToString(PipelineOptions.serializer(), PipelineOptions()), "", "v1", null, null, "[]", null, null, 0, 0, 0, null,
        )
        db.generations().upsert(gen)
        orchestrator.run(gen.id)

        val done = db.generations().get(gen.id)!!
        val steps = orchestrator.decodeSteps(done)
        assertThat(done.state).isAnyOf(GenerationState.SUCCEEDED, GenerationState.PARTIAL)
        assertThat(steps.none { it.state == StepState.ERROR || it.state == StepState.WAITING }).isTrue()
        val b = projects.observeBundleOnce(project.id)!!
        assertThat(b.article!!.title).isNotEmpty()
        assertThat(b.article!!.titleCandidates.size).isAtLeast(10)
        assertThat(b.article!!.blocks.count { it.type == BlockType.IMAGE }).isAtLeast(4)
        assertThat(b.visualPlan!!.cards.all { it.renderedAssetId != null }).isTrue()
        assertThat(b.timeline!!.scenes.size).isAtLeast(4)
        assertThat(b.thumbnailPath).isNotNull()
        assertThat(b.assets.count { it.kind == AssetKind.BLOG }).isEqualTo(b.visualPlan!!.cards.size)
        db.close()
        project.id
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        // Software draw of the real view hierarchy (Robolectric native graphics).
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        val dir = File("../docs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(t: String, ms: Long = 20_000) = rule.waitUntil(ms) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun openApp() {
        rule.waitUntil(20_000) { rule.onAllNodesWithText("건너뛰기").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("AI 콘텐츠 만들기").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithText("건너뛰기").fetchSemanticsNodes().isNotEmpty()) { shot(prefix + "01_onboarding"); rule.onNodeWithText("건너뛰기").performClick() }
        waitText("AI 콘텐츠 만들기")
    }

    private var prefix = ""

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    private fun walkthrough() {
        openApp()
        shot(prefix + "02_home")
        val target = hasText("노이즈 캔슬링", substring = true)
        if (rule.onAllNodes(target).fetchSemanticsNodes().isEmpty()) {
            val scrollables = rule.onAllNodes(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNodes().size
            for (i in 0 until scrollables) {
                runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[i].performScrollToNode(target) }
                if (rule.onAllNodes(target).fetchSemanticsNodes().isNotEmpty()) break
            }
        }
        rule.onAllNodes(target)[0].performClick()
        waitText("Product Intelligence Report")
        shot(prefix + "03_project")
        rule.onNodeWithText("블로그 편집").performClick()
        waitText("제목 후보")
        Thread.sleep(300); rule.waitForIdle()
        shot(prefix + "04_blog_editor")
        rule.onNodeWithText("제목 후보").performClick()
        waitText("제목 후보 (A/B)")
        shot(prefix + "05_title_candidates")
        back()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("제목 후보 (A/B)").fetchSemanticsNodes().isEmpty() }
        Thread.sleep(300); rule.waitForIdle()
        if (rule.onAllNodesWithText("Product Intelligence Report", substring = true).fetchSemanticsNodes().isEmpty()) back()
        waitText("Product Intelligence Report")
        rule.onNodeWithText("이미지 카드").performClick()
        waitText("카드 추가")
        Thread.sleep(500); rule.waitForIdle()
        shot(prefix + "06_visual_cards")
        back()
        waitText("Product Intelligence Report")
        rule.onNodeWithText("Shorts Studio").performClick()
        waitText("AI 자동 편집")
        Thread.sleep(800); rule.waitForIdle()
        shot(prefix + "07_shorts_studio")
        back()
        waitText("Product Intelligence Report")
        rule.onNodeWithText("미리보기").performClick()
        waitText("Mobile")
        shot(prefix + "08_blog_preview")
        back()
        waitText("Product Intelligence Report")
        rule.onNodeWithText("게시 준비").performClick()
        waitText("Checklist")
        shot(prefix + "09_publish_checklist")
    }

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun phoneEndToEnd() { prefix = "phone_"; walkthrough() }

    @Test @Config(qualifiers = "w904dp-h1000dp")
    fun foldInnerEndToEnd() { prefix = "fold_inner_"; walkthrough() }
}
