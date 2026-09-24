package com.shoppingconnect.aistudio.device

import android.os.SystemClock
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.shoppingconnect.aistudio.MainActivity
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Real app on the real device: cold start, onboarding, demo pipeline through the actual
 * WorkManager worker, blog editor input (Korean/English/numbers/emoji), undo/redo, studio, settings.
 * Screenshots are real device screenshots (UiDevice), saved to the QA folder.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DeviceAppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun shot(name: String) { rule.waitForIdle(); device.takeScreenshot(File(QaLog.dir, "$name.png")) }
    private fun has(t: String) = rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(t: String, ms: Long = 30_000) = rule.waitUntil(ms) { has(t) }
    private fun tag(): String = if (rule.activity.resources.configuration.screenWidthDp >= 600) "inner" else "cover"

    private fun home() {
        rule.waitUntil(20_000) { has("건너뛰기") || has("AI 콘텐츠 만들기") }
        if (has("건너뛰기")) { shot("onboarding_${tag()}"); rule.onNodeWithText("건너뛰기").performClick() }
        waitText("AI 콘텐츠 만들기")
    }

    @Test fun t01_coldStartAndHome() {
        val t0 = SystemClock.elapsedRealtime()
        home()
        QaLog.row("App launch → home (${tag()})", "PASS", "home visible ${SystemClock.elapsedRealtime() - t0}ms after activity start, width ${rule.activity.resources.configuration.screenWidthDp}dp")
        shot("home_${tag()}")
        rule.onNode(hasSetTextAction()).performTextInput("https://naver.me/abc123")
        waitText("Compatible Link", 5_000)
        QaLog.row("URL check", "PASS", "Compatible Link shown for naver.me")
    }

    @Test fun t02_demoPipelineOnDevice() {
        home()
        if (!has("데모로 체험")) { QaLog.row("Demo pipeline", "SKIPPED", "AI already configured — demo button hidden"); return }
        val t0 = SystemClock.elapsedRealtime()
        rule.onNodeWithText("데모로 체험").performScrollTo().performClick()
        waitText("AI 파이프라인", 15_000)
        rule.waitUntil(180_000) { has("블로그 글 검수하기") || has("다시 시도") }
        shot("pipeline_done_${tag()}")
        val ok = has("블로그 글 검수하기")
        QaLog.row("Demo pipeline (real WorkManager)", if (ok) "PASS" else "FAIL", "8 steps in ${SystemClock.elapsedRealtime() - t0}ms")
        if (!ok) throw AssertionError("pipeline failed on device")

        rule.onNodeWithText("블로그 글 검수하기").performClick()
        waitText("제목 후보")
        shot("blog_editor_${tag()}")
        // Type into the first paragraph: Korean, English, numbers, emoji, long sentence.
        val input = " 한글 English 12345 😀🎧 아주 긴 문장을 입력해도 입력 지연이나 키보드 겹침이 없는지 확인합니다."
        rule.onAllNodes(hasSetTextAction())[1].performTextInput(input)
        rule.waitForIdle()
        val typed = has("12345 😀🎧")
        rule.onNodeWithContentDescription("실행 취소").performClick(); rule.waitForIdle()
        val undone = !has("12345 😀🎧")
        rule.onNodeWithContentDescription("다시 실행").performClick(); rule.waitForIdle()
        val redone = has("12345 😀🎧")
        shot("blog_editor_typed_${tag()}")
        QaLog.row("Blog editor input/undo/redo", if (typed && undone && redone) "PASS" else "FAIL", "typed=$typed undo=$undone redo=$redone")
        SystemClock.sleep(1800) // autosave debounce
        QaLog.row("Blog autosave", if (has("자동 저장됨")) "PASS" else "FAIL", "autosave indicator after edit")
    }

    @Test fun t03_studioAndSettings() {
        home()
        rule.onNodeWithContentDescription("Shorts").performClick()
        rule.waitForIdle()
        if (rule.onAllNodes(hasText("초", substring = true)).fetchSemanticsNodes().isNotEmpty() && has("드래프트")) {
            rule.onAllNodes(hasText("드래프트", substring = true))[0].performClick()
            waitText("AI 자동 편집")
            SystemClock.sleep(1500)
            shot("shorts_studio_${tag()}")
            QaLog.row("Shorts Studio open", "PASS", "preview + timeline rendered on device")
        } else QaLog.row("Shorts Studio open", "SKIPPED", "no shorts draft yet (run t02 first)")
        rule.onNodeWithContentDescription("설정").performClick()
        waitText("Account · NAVER")
        shot("settings_${tag()}")
        QaLog.row("Settings", "PASS", "settings list visible")
    }
}
