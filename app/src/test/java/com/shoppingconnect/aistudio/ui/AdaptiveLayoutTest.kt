package com.shoppingconnect.aistudio.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.shoppingconnect.aistudio.domain.model.PipelineStep
import com.shoppingconnect.aistudio.domain.model.StepState
import com.shoppingconnect.aistudio.ui.adaptive.FoldInfo
import com.shoppingconnect.aistudio.ui.adaptive.LayoutClass
import com.shoppingconnect.aistudio.ui.adaptive.LocalWindowLayout
import com.shoppingconnect.aistudio.ui.adaptive.WindowLayout
import com.shoppingconnect.aistudio.ui.adaptive.WorkspacePanes
import com.shoppingconnect.aistudio.ui.components.BlogRenderer
import com.shoppingconnect.aistudio.ui.components.ErrorPanel
import com.shoppingconnect.aistudio.ui.theme.AiStudioTheme
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.Block
import com.shoppingconnect.aistudio.domain.model.BlockType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AdaptiveLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun panes(layout: WindowLayout) = rule.setContent {
        AiStudioTheme {
            CompositionLocalProvider(LocalWindowLayout provides layout) {
                WorkspacePanes(left = { Text("LEFT") }, center = { Text("CENTER") }, right = { Text("RIGHT") })
            }
        }
    }

    @Test @Config(qualifiers = "w360dp-h800dp")
    fun coverScreenShowsSinglePane() {
        panes(WindowLayout(LayoutClass.COMPACT, FoldInfo(), 360))
        rule.onNodeWithText("CENTER").assertIsDisplayed()
        rule.onNodeWithText("LEFT").assertDoesNotExist()
        rule.onNodeWithText("RIGHT").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w700dp-h900dp")
    fun mediumShowsCenterAndInspector() {
        panes(WindowLayout(LayoutClass.MEDIUM, FoldInfo(), 700))
        rule.onNodeWithText("CENTER").assertIsDisplayed()
        rule.onNodeWithText("RIGHT").assertIsDisplayed()
        rule.onNodeWithText("LEFT").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w1100dp-h900dp")
    fun foldInnerShowsThreePanes() {
        panes(WindowLayout(LayoutClass.EXPANDED, FoldInfo(), 1100))
        listOf("LEFT", "CENTER", "RIGHT").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test @Config(qualifiers = "w1100dp-h900dp")
    fun separatingHingeKeepsAllPanesVisible() {
        panes(WindowLayout(LayoutClass.EXPANDED, FoldInfo(hasHinge = true, separating = true, vertical = true, hingeStartPx = 1000, hingeEndPx = 1060), 1100))
        listOf("LEFT", "CENTER", "RIGHT").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun errorPanelOffersRecovery() {
        rule.setContent { AiStudioTheme { ErrorPanel("상품 정보를 불러오지 못했습니다.", onRetry = {}, secondaryLabel = "직접 입력", onSecondary = {}) } }
        rule.onNodeWithText("다시 시도").assertIsDisplayed()
        rule.onNodeWithText("직접 입력").assertIsDisplayed()
    }

    @Test fun blogRendererShowsTitleAndDisclosure() {
        rule.setContent { AiStudioTheme { BlogRenderer(Article(title = "미리보기 제목", blocks = listOf(Block(type = BlockType.DISCLOSURE, text = "제휴 고지"), Block(type = BlockType.PARAGRAPH, text = "**굵게** 본문"))), emptyList()) } }
        rule.onNodeWithText("미리보기 제목").assertIsDisplayed()
        rule.onNodeWithText("제휴 고지").assertIsDisplayed()
    }

    @Suppress("unused") private val keep = PipelineStep.ANALYZE to StepState.RUNNING
}
