package com.shoppingconnect.aistudio.qa

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs after scripts/device-qa.sh has killed the app process (am kill while backgrounded) following
 * DeviceUiQaTest: the edited blog title, the shorts draft and the finished render must come back
 * from Room in a fresh process.
 */
@RunWith(AndroidJUnit4::class)
class DeviceRestoreQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun has(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(t: String, ms: Long = 30_000) = compose.waitUntil(ms) { has(t) }
    private fun settle() { compose.waitForIdle(); Thread.sleep(800); compose.waitForIdle() }

    @Test fun projectEditsAndRenderSurviveProcessDeath() {
        // Only meaningful as the second half of scripts/device-qa.sh (after DeviceUiQaTest + process kill).
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("restoreCheck") == "1")
        waitText("AI 콘텐츠 만들기")
        val product = hasText("노이즈 캔슬링", substring = true)
        if (compose.onAllNodes(product).fetchSemanticsNodes().isEmpty()) {
            val n = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
            for (i in 0 until n) {
                runCatching { compose.onAllNodes(hasScrollAction())[i].performScrollToNode(product) }
                if (compose.onAllNodes(product).fetchSemanticsNodes().isNotEmpty()) break
            }
        }
        compose.onAllNodes(product)[0].performClick()
        waitText("Product Intelligence Report")

        compose.onNodeWithText("블로그 편집").performClick()
        waitText("제목 후보"); settle()
        val title = compose.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().config
            .getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.EditableText) { null }?.text.orEmpty()
        QaRecorder.record("Process death: blog title", "MEASURED", "restored" to title.contains(DeviceUiQaTest.MARKER.trim()))
        assertThat(title).contains(DeviceUiQaTest.MARKER.trim())
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("Product Intelligence Report")

        compose.onNodeWithText("Shorts Studio").performClick()
        waitText("AI 자동 편집"); settle()
        compose.onNodeWithContentDescription("렌더 · 내보내기").performClick()
        waitText("Render Queue")
        val done = has("완료 ·")
        QaRecorder.record("Process death: shorts draft + render output", "MEASURED", "renderListed" to done)
        QaRecorder.screenshot("restore_after_process_death")
        assertThat(done).isTrue()
    }
}
