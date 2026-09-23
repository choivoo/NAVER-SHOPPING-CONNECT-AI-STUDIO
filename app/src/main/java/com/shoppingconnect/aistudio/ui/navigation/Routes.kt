package com.shoppingconnect.aistudio.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "홈", Icons.Default.Home),
    PROJECTS("projects", "프로젝트", Icons.Default.Folder),
    CONTENT("content", "콘텐츠", Icons.AutoMirrored.Filled.Article),
    SHORTS("shorts", "Shorts", Icons.Default.Movie),
    ANALYTICS("analytics", "분석", Icons.Default.Analytics),
    SETTINGS("settings", "설정", Icons.Default.Settings),
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val PIPELINE = "pipeline/{genId}"
    const val PROJECT = "project/{projectId}"
    const val EDITOR = "editor/{projectId}"
    const val PREVIEW = "preview/{projectId}"
    const val PUBLISH = "publish/{projectId}"
    const val VISUALS = "visuals/{projectId}"
    const val VISUAL_EDITOR = "visualEditor/{projectId}/{cardId}"
    const val STUDIO = "studio/{projectId}"
    const val THUMBNAIL = "thumbnail/{projectId}"
    const val MANUAL = "manual?url={url}&genId={genId}"
    const val BATCH = "batch"
    const val SETTINGS_SECTION = "settings/{section}"
    const val DEVELOPER = "developer"
    const val STORAGE = "storage"

    fun pipeline(genId: String) = "pipeline/$genId"
    fun project(id: String) = "project/$id"
    fun editor(id: String) = "editor/$id"
    fun preview(id: String) = "preview/$id"
    fun publish(id: String) = "publish/$id"
    fun visuals(id: String) = "visuals/$id"
    fun visualEditor(id: String, cardId: String) = "visualEditor/$id/$cardId"
    fun studio(id: String) = "studio/$id"
    fun thumbnail(id: String) = "thumbnail/$id"
    fun manual(url: String = "", genId: String = "") = "manual?url=${Uri.encode(url)}&genId=${Uri.encode(genId)}"
    fun settingsSection(s: String) = "settings/$s"
}
