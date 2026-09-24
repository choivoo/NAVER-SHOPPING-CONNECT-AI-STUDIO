package com.shoppingconnect.aistudio.qa

import android.Manifest
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shoppingconnect.aistudio.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The real app, driven through its UI on an emulator or phone: onboarding → demo pipeline →
 * blog editor (Korean/English/numbers/emoji, undo/redo, bold) → visual cards → Shorts Studio →
 * WorkManager render → save to gallery → Android Sharesheet. On a foldable the posture is switched
 * with `cmd device_state` mid-edit to check that the Activity survives and state is kept.
 * Leaves the demo project (with a marker title) behind for DeviceRestoreQaTest after process death.
 */
@RunWith(AndroidJUnit4::class)
class DeviceUiQaTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val notifications: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(notifications).around(compose)

    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val states by lazy { Posture.states() }

    @After fun resetPosture() { if (states.foldable) Posture.set("reset") }

    @Test fun fullDemoWorkflow() {
        QaRecorder.record("Posture support", "MEASURED", "foldable" to states.foldable, "states" to states.raw)
        openHome()
        if (states.foldable) {
            Posture.set(states.opened!!); settle(); QaRecorder.screenshot("02_fold_home")
            Posture.set(states.closed!!); settle(); QaRecorder.screenshot("01_cover_home")
            Posture.set(states.opened!!); settle()
        } else QaRecorder.screenshot("01_home")

        // Demo pipeline through the real WorkManager worker.
        val demo = hasText("데모로 체험")
        scrollTo(demo)
        compose.onAllNodes(demo)[0].performClick()
        val t0 = System.currentTimeMillis()
        waitText("블로그 글 검수하기", 240_000)
        QaRecorder.record("Demo pipeline (UI)", "MEASURED", "ms" to System.currentTimeMillis() - t0)
        QaRecorder.screenshot("pipeline_done")

        // Blog editor.
        compose.onNodeWithText("블로그 글 검수하기").performClick()
        waitText("제목 후보")
        settle()
        val title = compose.onAllNodes(hasSetTextAction())[0]
        title.performTextInput(MARKER)
        settle()
        assertWithMessage("typed text missing from title").that(textOf(title)).contains(MARKER)
        compose.onNodeWithContentDescription("실행 취소").performClick(); settle()
        val afterUndo = textOf(title)
        compose.onNodeWithContentDescription("다시 실행").performClick(); settle()
        val afterRedo = textOf(title)
        QaRecorder.record("Editor input/undo/redo", "MEASURED", "afterUndoHasMarker" to afterUndo.contains(MARKER), "afterRedoHasMarker" to afterRedo.contains(MARKER))
        assertThat(afterUndo).doesNotContain(MARKER)
        assertThat(afterRedo).contains(MARKER)
        if (compose.onAllNodesWithContentDescription("굵게").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithContentDescription("굵게").performClick()
        QaRecorder.screenshot("03_fold_blog_editor")

        if (states.foldable) {
            val activity = compose.activity
            Posture.set(states.closed!!); settle()
            QaRecorder.screenshot("03b_cover_blog_editor")
            val sameAfterFold = compose.activity === activity
            val textAfterFold = textOf(compose.onAllNodes(hasSetTextAction())[0])
            Posture.set(states.opened!!); settle()
            val sameAfterUnfold = compose.activity === activity
            val textAfterUnfold = textOf(compose.onAllNodes(hasSetTextAction())[0])
            QaRecorder.record("Fold transition (editor)", "MEASURED", "sameActivityAfterFold" to sameAfterFold, "sameActivityAfterUnfold" to sameAfterUnfold,
                "textKeptFold" to textAfterFold.contains(MARKER), "textKeptUnfold" to textAfterUnfold.contains(MARKER))
            assertThat(sameAfterFold).isTrue()
            assertThat(sameAfterUnfold).isTrue()
            assertThat(textAfterFold).contains(MARKER)
            assertThat(textAfterUnfold).contains(MARKER)
        }
        Thread.sleep(2_500) // debounced autosave

        // Project → visual cards → Shorts Studio.
        backUntil("프로젝트 카드 열기", "Product Intelligence Report")
        if (compose.onAllNodesWithText("프로젝트 카드 열기").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("프로젝트 카드 열기").performClick()
        waitText("Product Intelligence Report")
        compose.onNodeWithText("이미지 카드").performClick()
        waitText("카드 추가"); settle()
        QaRecorder.screenshot("04_visual_cards")
        backUntil("Product Intelligence Report")
        compose.onNodeWithText("Shorts Studio").performClick()
        waitText("AI 자동 편집"); settle()
        QaRecorder.screenshot("05_shortform_editor")

        // Render through RenderWorker (foreground service), then save and share.
        compose.onNodeWithContentDescription("렌더 · 내보내기").performClick()
        waitText("렌더링 시작")
        compose.onNodeWithText("렌더링 시작", substring = true).performClick()
        val r0 = System.currentTimeMillis()
        compose.waitUntil(900_000) {
            compose.onAllNodesWithText("완료 ·", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("실패:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val failed = compose.onAllNodesWithText("실패:", substring = true).fetchSemanticsNodes().firstOrNull()
        QaRecorder.record("Render via RenderWorker (UI)", "MEASURED", "ms" to System.currentTimeMillis() - r0, "error" to failed?.config?.toString())
        QaRecorder.screenshot("06_render_complete")
        assertWithMessage("render failed in the app").that(failed).isNull()

        val before = countAppVideos()
        compose.onNodeWithText("기기에 저장").performClick()
        compose.waitUntil(30_000) { countAppVideos() > before }
        QaRecorder.record("Gallery save (UI)", "MEASURED", "videosBefore" to before, "videosAfter" to countAppVideos())

        compose.onNodeWithText("공유").performClick()
        val chooser = device.wait(Until.hasObject(By.pkg(java.util.regex.Pattern.compile("com.android.intentresolver|android"))), 15_000)
        QaRecorder.screenshot("share_sheet")
        QaRecorder.record("Share sheet (UI)", "MEASURED", "shown" to chooser)
        assertThat(chooser).isTrue()
        device.pressBack()
    }

    // ---------------------------------------------------------------- helpers
    private fun openHome() {
        compose.waitUntil(30_000) { has("건너뛰기") || has("AI 콘텐츠 만들기") }
        if (has("건너뛰기")) { QaRecorder.screenshot("00_onboarding"); compose.onNodeWithText("건너뛰기").performClick() }
        waitText("AI 콘텐츠 만들기")
        settle()
    }

    private fun has(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(t: String, ms: Long = 30_000) = compose.waitUntil(ms) { has(t) }
    private fun settle() { compose.waitForIdle(); Thread.sleep(800); compose.waitForIdle() }

    private fun scrollTo(m: androidx.compose.ui.test.SemanticsMatcher) {
        if (compose.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()) return
        val n = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until n) {
            runCatching { compose.onAllNodes(hasScrollAction())[i].performScrollToNode(m) }
            if (compose.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()) return
        }
    }

    private fun backUntil(vararg any: String) {
        repeat(4) {
            if (any.any { has(it) }) return
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            settle()
        }
    }

    private fun textOf(node: SemanticsNodeInteraction): String {
        val cfg = node.fetchSemanticsNode().config
        return cfg.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.EditableText) { null }?.text.orEmpty()
    }

    private fun countAppVideos(): Int {
        val cr = compose.activity.contentResolver
        return cr.query(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("%AIStudio%"), null)?.use { it.count } ?: 0
    }

    companion object {
        const val MARKER = " QA복원 English 123 😀"
    }
}

/** Fold posture through the platform DeviceStateManager (works on foldable emulators and phones). */
object Posture {
    data class States(val raw: String, val closed: String?, val opened: String?) { val foldable get() = closed != null && opened != null }

    fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    fun states(): States {
        val raw = shell("cmd device_state print-states").trim()
        // e.g. "DeviceState{identifier=0, name='CLOSED', ...}" — pick by name, fall back to ids 0/2.
        val entries = Regex("identifier=(\\d+), name='([A-Z_]+)'").findAll(raw).map { it.groupValues[1] to it.groupValues[2] }.toList()
        if (entries.size < 2) return States(raw, null, null)
        val closed = entries.firstOrNull { it.second.contains("CLOSED") || it.second.contains("FOLDED") }?.first
        val opened = entries.firstOrNull { it.second == "OPENED" || it.second.contains("OPEN") && !it.second.contains("HALF") }?.first
        return States(raw, closed, opened)
    }

    fun set(state: String) { shell("cmd device_state state $state"); Thread.sleep(1500) }
}
