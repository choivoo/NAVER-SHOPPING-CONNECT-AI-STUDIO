package com.shoppingconnect.aistudio.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.shoppingconnect.aistudio.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Launches the real app (Hilt graph, Room, DataStore, navigation) under Robolectric and walks the
 * main flow at phone / Fold cover / Fold inner / tablet sizes.
 */
@RunWith(RobolectricTestRunner::class)
class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        init {
            // WorkManager must be initialised before the Activity (and its view models) start.
        }
    }

    @Before fun setUp() {
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext()) }
    }

    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Onboarding is shown only on first launch (settings persist between tests in one process). */
    private fun passOnboarding() {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("건너뛰기").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("AI 콘텐츠 만들기").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("건너뛰기").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("건너뛰기").performClick()
        waitFor("AI 콘텐츠 만들기")
    }

    private fun tab(label: String) = rule.onNodeWithContentDescription(label, useUnmergedTree = true).performClick()

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun phoneFlowHomeLinkCheckAndTabs() {
        passOnboarding()
        rule.onNodeWithText("AI 콘텐츠 만들기").performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("https://naver.me/", substring = true)).performTextInput("https://naver.me/xyz")
        waitFor("Compatible Link")
        tab("프로젝트")
        waitFor("첫 쇼핑 콘텐츠를 만들어보세요.")
        tab("설정")
        waitFor("Account · NAVER")
        rule.onNodeWithText("AI").performClick()
        waitFor("Auto Best")
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun unsupportedUrlIsReported() {
        passOnboarding()
        rule.onNode(hasText("https://naver.me/", substring = true)).performTextInput("file:///etc/passwd")
        waitFor("Unsupported URL")
    }

    @Test @Config(qualifiers = "w840dp-h900dp")
    fun foldInnerUsesRailAndTwoColumnHome() {
        passOnboarding()
        rule.onNodeWithText("최근 프로젝트").assertExists()
        tab("분석")
        waitFor("프로젝트 상태")
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land")
    fun tabletLandscape() {
        passOnboarding()
        tab("콘텐츠")
        waitFor("작성된 블로그 글이 없습니다.")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-night")
    fun darkModeLargeFontManualInput() {
        passOnboarding()
        rule.onNodeWithText("상품 정보 직접 입력").performScrollTo().performClick()
        waitFor("상품명 *")
    }
}
