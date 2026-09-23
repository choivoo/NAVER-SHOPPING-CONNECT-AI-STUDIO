package com.shoppingconnect.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shoppingconnect.aistudio.ui.screens.analytics.AnalyticsScreen
import com.shoppingconnect.aistudio.ui.screens.batch.BatchScreen
import com.shoppingconnect.aistudio.ui.screens.blog.BlogPreviewScreen
import com.shoppingconnect.aistudio.ui.screens.blog.PublishScreen
import com.shoppingconnect.aistudio.ui.screens.editor.EditorScreen
import com.shoppingconnect.aistudio.ui.screens.home.HomeScreen
import com.shoppingconnect.aistudio.ui.screens.lists.ContentScreen
import com.shoppingconnect.aistudio.ui.screens.lists.ShortsListScreen
import com.shoppingconnect.aistudio.ui.screens.manual.ManualInputScreen
import com.shoppingconnect.aistudio.ui.screens.onboarding.OnboardingScreen
import com.shoppingconnect.aistudio.ui.screens.pipeline.PipelineScreen
import com.shoppingconnect.aistudio.ui.screens.projects.ProjectDetailScreen
import com.shoppingconnect.aistudio.ui.screens.projects.ProjectsScreen
import com.shoppingconnect.aistudio.ui.screens.settings.DeveloperScreen
import com.shoppingconnect.aistudio.ui.screens.settings.SettingsScreen
import com.shoppingconnect.aistudio.ui.screens.settings.SettingsSectionScreen
import com.shoppingconnect.aistudio.ui.screens.settings.StorageScreen
import com.shoppingconnect.aistudio.ui.screens.shorts.StudioScreen
import com.shoppingconnect.aistudio.ui.screens.shorts.ThumbnailStudioScreen
import com.shoppingconnect.aistudio.ui.screens.visual.VisualCardsScreen
import com.shoppingconnect.aistudio.ui.screens.visual.VisualEditorScreen

@Composable
fun AppNavHost(nav: NavHostController, startDestination: String) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { nav.navigate(TopDest.HOME.route) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
        }
        composable(TopDest.HOME.route) {
            HomeScreen(
                onOpenPipeline = { nav.navigate(Routes.pipeline(it)) },
                onOpenProject = { nav.navigate(Routes.project(it)) },
                onManual = { nav.navigate(Routes.manual(it)) },
                onBatch = { nav.navigate(Routes.BATCH) },
                onOpenSettings = { nav.navigate(Routes.settingsSection(it)) },
            )
        }
        composable(TopDest.PROJECTS.route) { ProjectsScreen(onOpen = { nav.navigate(Routes.project(it)) }, onBatch = { nav.navigate(Routes.BATCH) }) }
        composable(TopDest.CONTENT.route) { ContentScreen(onOpen = { nav.navigate(Routes.editor(it)) }) }
        composable(TopDest.SHORTS.route) { ShortsListScreen(onOpen = { nav.navigate(Routes.studio(it)) }) }
        composable(TopDest.ANALYTICS.route) { AnalyticsScreen() }
        composable(TopDest.SETTINGS.route) {
            SettingsScreen(onSection = { if (it == "storage_screen") nav.navigate(Routes.STORAGE) else nav.navigate(Routes.settingsSection(it)) }, onDeveloper = { nav.navigate(Routes.DEVELOPER) })
        }
        composable(Routes.PIPELINE, listOf(navArgument("genId") { type = NavType.StringType })) {
            PipelineScreen(
                onBack = back,
                onOpenProject = { nav.navigate(Routes.project(it)) },
                onOpenEditor = { nav.navigate(Routes.editor(it)) },
                onOpenStudio = { nav.navigate(Routes.studio(it)) },
                onManual = { url, gen -> nav.navigate(Routes.manual(url, gen)) },
                onSettings = { nav.navigate(Routes.settingsSection("ai")) },
            )
        }
        composable(Routes.MANUAL, listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" }, navArgument("genId") { type = NavType.StringType; defaultValue = "" })) {
            ManualInputScreen(onBack = back, onStarted = { gen -> nav.navigate(Routes.pipeline(gen)) { popUpTo(Routes.MANUAL) { inclusive = true } } })
        }
        composable(Routes.BATCH) { BatchScreen(onBack = back, onOpenProject = { nav.navigate(Routes.project(it)) }) }
        composable(Routes.PROJECT, listOf(navArgument("projectId") { type = NavType.StringType })) {
            ProjectDetailScreen(
                onBack = back,
                onEditor = { nav.navigate(Routes.editor(it)) }, onPreview = { nav.navigate(Routes.preview(it)) }, onPublish = { nav.navigate(Routes.publish(it)) },
                onVisuals = { nav.navigate(Routes.visuals(it)) }, onStudio = { nav.navigate(Routes.studio(it)) }, onPipeline = { nav.navigate(Routes.pipeline(it)) },
            )
        }
        composable(Routes.EDITOR, listOf(navArgument("projectId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("projectId").orEmpty()
            EditorScreen(onBack = back, onPreview = { nav.navigate(Routes.preview(id)) }, onPublish = { nav.navigate(Routes.publish(id)) })
        }
        composable(Routes.PREVIEW, listOf(navArgument("projectId") { type = NavType.StringType })) { BlogPreviewScreen(onBack = back) }
        composable(Routes.PUBLISH, listOf(navArgument("projectId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("projectId").orEmpty()
            PublishScreen(onBack = back, onEdit = { nav.navigate(Routes.editor(id)) })
        }
        composable(Routes.VISUALS, listOf(navArgument("projectId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("projectId").orEmpty()
            VisualCardsScreen(onBack = back, onEdit = { card -> nav.navigate(Routes.visualEditor(id, card)) })
        }
        composable(Routes.VISUAL_EDITOR, listOf(navArgument("projectId") { type = NavType.StringType }, navArgument("cardId") { type = NavType.StringType })) { VisualEditorScreen(onBack = back) }
        composable(Routes.STUDIO, listOf(navArgument("projectId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("projectId").orEmpty()
            StudioScreen(onBack = back, onThumbnail = { nav.navigate(Routes.thumbnail(id)) })
        }
        composable(Routes.THUMBNAIL, listOf(navArgument("projectId") { type = NavType.StringType })) { ThumbnailStudioScreen(onBack = back) }
        composable(Routes.SETTINGS_SECTION, listOf(navArgument("section") { type = NavType.StringType })) { e ->
            SettingsSectionScreen(e.arguments?.getString("section").orEmpty(), onBack = back)
        }
        composable(Routes.STORAGE) { StorageScreen(onBack = back) }
        composable(Routes.DEVELOPER) { DeveloperScreen(onBack = back) }
    }
}
