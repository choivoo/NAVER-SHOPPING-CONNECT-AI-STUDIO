package com.shoppingconnect.aistudio.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.ProjectStatus

/** One product = one project. Everything else hangs off the project and cascades on delete. */
@Entity(tableName = "projects", indices = [Index("status"), Index("updatedAt")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val brand: String?,
    val status: ProjectStatus,
    val sourceUrl: String,
    val thumbnailPath: String?,
    val tags: List<String>,
    val analyticsNotes: String,
    val promptVersion: String,
    val isDemo: Boolean,
    val hasBlog: Boolean,
    val hasShorts: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val publishedAt: Long? = null,
)

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("cacheKey")],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val cacheKey: String,
    val productJson: String,
    val intelligenceJson: String?,
    val fetchedAt: Long,
)

@Entity(
    tableName = "articles",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"], unique = true)],
)
data class ArticleEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val articleJson: String,
    val strategyJson: String,
    val visualPlanJson: String?,
    val qualityScore: Int,
    val model: String,
    val promptVersion: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "article_versions",
    foreignKeys = [ForeignKey(ArticleEntity::class, ["id"], ["articleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("articleId")],
)
data class ArticleVersionEntity(
    @PrimaryKey val id: String,
    val articleId: String,
    val label: String,
    val articleJson: String,
    val createdAt: Long,
)

@Entity(
    tableName = "media_assets",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("kind")],
)
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val kind: AssetKind,
    val path: String,
    val mimeType: String,
    val sourceUrl: String?,
    val copyrightType: CopyrightType,
    val aiGenerated: Boolean,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val usage: String,
    val metaJson: String?,
    val createdAt: Long,
) {
    val aspectRatio: Float get() = if (height == 0) 1f else width.toFloat() / height
}

@Entity(
    tableName = "shortforms",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["projectId"], unique = true)],
)
data class ShortformEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val timelineJson: String,
    val thumbnailJson: String?,
    val outputPath: String?,
    val thumbnailPath: String?,
    val durationMs: Long,
    val autosaveJson: String?,
    val updatedAt: Long,
)

@Entity(
    tableName = "generations",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("state")],
)
data class GenerationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val inputUrl: String,
    val state: GenerationState,
    val stepsJson: String,
    val optionsJson: String,
    val model: String,
    val promptVersion: String,
    val errorKind: String?,
    val errorMessage: String?,
    val warningsJson: String,
    val batchId: String?,
    val workId: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val startedAt: Long,
    val finishedAt: Long?,
)

@Entity(tableName = "prompt_versions")
data class PromptVersionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val hash: String,
    val usedCount: Int,
    val lastUsedAt: Long,
)

enum class RenderState { QUEUED, RENDERING, SUCCEEDED, FAILED, CANCELLED }

@Entity(
    tableName = "render_jobs",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("state")],
)
data class RenderJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val shortformId: String,
    val state: RenderState,
    val progress: Int,
    val outputPath: String?,
    val errorMessage: String?,
    val settingsJson: String,
    val workId: String?,
    val renderMs: Long,
    val frames: Int,
    val createdAt: Long,
    val finishedAt: Long?,
)

/** Revenue/performance data entered manually by the user — the app never fabricates it. */
@Entity(tableName = "manual_metrics", indices = [Index("projectId")])
data class ManualMetricEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val label: String,
    val amount: Long,
    val note: String,
    val date: Long,
)
