package com.shoppingconnect.aistudio.data.repository

import androidx.room.withTransaction
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.core.common.sha256
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.data.db.AppDatabase
import com.shoppingconnect.aistudio.data.db.ArticleEntity
import com.shoppingconnect.aistudio.data.db.ArticleVersionEntity
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.db.ProductEntity
import com.shoppingconnect.aistudio.data.db.ProjectEntity
import com.shoppingconnect.aistudio.data.db.ShortformEntity
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProductIntelligence
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.ThumbnailSpec
import com.shoppingconnect.aistudio.domain.model.VisualPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

data class ProjectBundle(
    val project: ProjectEntity,
    val product: Product?,
    val intelligence: ProductIntelligence?,
    val article: Article?,
    val articleId: String?,
    val strategy: ContentStrategy?,
    val visualPlan: VisualPlan?,
    val qualityScore: Int,
    val timeline: ShortTimeline?,
    val thumbnail: ThumbnailSpec?,
    val shortformId: String?,
    val videoPath: String?,
    val thumbnailPath: String?,
    val autosave: ShortTimeline?,
    val assets: List<MediaAssetEntity>,
    val model: String?,
    val promptVersion: String?,
)

@Serializable
data class ProjectBackup(
    val format: String = "aistudio.project.v1",
    val exportedAt: Long,
    val title: String,
    val status: String,
    val product: Product?,
    val intelligence: ProductIntelligence?,
    val strategy: ContentStrategy?,
    val article: Article?,
    val visualPlan: VisualPlan?,
    val timeline: ShortTimeline?,
    val thumbnail: ThumbnailSpec?,
    val assets: List<AssetRef>,
)

@Serializable
data class AssetRef(val id: String, val kind: String, val fileName: String, val copyright: String, val aiGenerated: Boolean, val sourceUrl: String?, val width: Int, val height: Int)

@Singleton
class ProjectRepository @Inject constructor(
    private val db: AppDatabase,
    private val files: ProjectFiles,
) {
    private val json = AppJson

    fun observeProjects(): Flow<List<ProjectEntity>> = db.projects().observeAll()
    fun search(q: String, status: ProjectStatus?) = db.projects().search(q.trim(), status)
    suspend fun project(id: String) = db.projects().get(id)

    fun cacheKey(url: String) = url.trim().lowercase().sha256()

    suspend fun createProject(product: Product, promptVersion: String): ProjectEntity {
        val now = System.currentTimeMillis()
        val p = ProjectEntity(
            id = newId(), title = product.title.ifBlank { "새 프로젝트" }, brand = product.brand, status = ProjectStatus.DRAFT,
            sourceUrl = product.affiliateUrl, thumbnailPath = null, tags = listOfNotNull(product.category?.substringAfterLast(">")?.trim(), product.brand).distinct(),
            analyticsNotes = "", promptVersion = promptVersion, isDemo = product.isDemo, hasBlog = false, hasShorts = false, createdAt = now, updatedAt = now,
        )
        db.withTransaction {
            db.projects().upsert(p)
            db.products().upsert(ProductEntity(product.id, p.id, cacheKey(product.sourceUrl), json.encodeToString(Product.serializer(), product), null, now))
        }
        return p
    }

    suspend fun updateProject(id: String, transform: (ProjectEntity) -> ProjectEntity) {
        val p = db.projects().get(id) ?: return
        db.projects().upsert(transform(p).copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setStatus(id: String, status: ProjectStatus) = updateProject(id) {
        it.copy(status = status, publishedAt = if (status == ProjectStatus.PUBLISHED) System.currentTimeMillis() else it.publishedAt)
    }

    suspend fun product(projectId: String): Product? = db.products().forProject(projectId)?.let { decodeProduct(it.productJson) }

    suspend fun saveProduct(projectId: String, product: Product, intel: ProductIntelligence?) {
        val existing = db.products().forProject(projectId)
        db.products().upsert(
            ProductEntity(
                existing?.id ?: product.id, projectId, cacheKey(product.sourceUrl),
                json.encodeToString(Product.serializer(), product),
                intel?.let { json.encodeToString(ProductIntelligence.serializer(), it) } ?: existing?.intelligenceJson,
                System.currentTimeMillis(),
            ),
        )
        updateProject(projectId) { it.copy(title = product.title.ifBlank { it.title }, brand = product.brand) }
    }

    /** Re-use a recent analysis of the same product instead of paying for it again. */
    suspend fun cachedIntelligence(sourceUrl: String, maxAgeMs: Long = 7L * 24 * 3600 * 1000): ProductIntelligence? =
        db.products().cachedAnalysis(cacheKey(sourceUrl), System.currentTimeMillis() - maxAgeMs)?.intelligenceJson?.let {
            runCatching { json.decodeFromString(ProductIntelligence.serializer(), it) }.getOrNull()
        }

    suspend fun intelligence(projectId: String): ProductIntelligence? = db.products().forProject(projectId)?.intelligenceJson?.let {
        runCatching { json.decodeFromString(ProductIntelligence.serializer(), it) }.getOrNull()
    }

    suspend fun saveArticle(
        projectId: String, article: Article, strategy: ContentStrategy, versionLabel: String?, model: String, promptVersion: String,
        qualityScore: Int, visualPlan: VisualPlan? = null, markEdited: Boolean = false,
    ): String {
        val existing = db.articles().forProject(projectId)
        val id = existing?.id ?: newId()
        val articleJson = json.encodeToString(Article.serializer(), article)
        db.withTransaction {
            db.articles().upsert(
                ArticleEntity(
                    id, projectId, article.title, articleJson, json.encodeToString(ContentStrategy.serializer(), strategy),
                    visualPlan?.let { json.encodeToString(VisualPlan.serializer(), it) } ?: existing?.visualPlanJson,
                    qualityScore, model.ifBlank { existing?.model.orEmpty() }, promptVersion, System.currentTimeMillis(),
                ),
            )
            if (versionLabel != null) {
                db.articles().insertVersion(ArticleVersionEntity(newId(), id, versionLabel, articleJson, System.currentTimeMillis()))
                db.articles().trimVersions(id, 40)
            }
            val p = db.projects().get(projectId)
            if (p != null) {
                val status = when {
                    markEdited && p.status in setOf(ProjectStatus.GENERATED, ProjectStatus.DRAFT) -> ProjectStatus.EDITED
                    else -> p.status
                }
                db.projects().upsert(p.copy(title = article.title.ifBlank { p.title }, hasBlog = article.blocks.isNotEmpty(), status = status, updatedAt = System.currentTimeMillis()))
            }
        }
        return id
    }

    suspend fun saveVisualPlan(projectId: String, plan: VisualPlan) {
        val a = db.articles().forProject(projectId) ?: return
        db.articles().upsert(a.copy(visualPlanJson = json.encodeToString(VisualPlan.serializer(), plan), updatedAt = System.currentTimeMillis()))
    }

    fun observeVersions(articleId: String) = db.articles().observeVersions(articleId)
    suspend fun version(id: String) = db.articles().version(id)?.let { json.decodeFromString(Article.serializer(), it.articleJson) }

    suspend fun saveShortform(projectId: String, timeline: ShortTimeline, thumbnail: ThumbnailSpec?, outputPath: String? = null, thumbnailPath: String? = null): String {
        val existing = db.shortforms().forProject(projectId)
        val id = existing?.id ?: newId()
        db.shortforms().upsert(
            ShortformEntity(
                id, projectId, json.encodeToString(ShortTimeline.serializer(), timeline),
                thumbnail?.let { json.encodeToString(ThumbnailSpec.serializer(), it) } ?: existing?.thumbnailJson,
                outputPath ?: existing?.outputPath, thumbnailPath ?: existing?.thumbnailPath, timeline.durationMs, null, System.currentTimeMillis(),
            ),
        )
        updateProject(projectId) { it.copy(hasShorts = timeline.scenes.isNotEmpty(), thumbnailPath = thumbnailPath ?: it.thumbnailPath) }
        return id
    }

    suspend fun setVideoOutput(shortformId: String, path: String?) {
        val s = db.shortforms().get(shortformId) ?: return
        db.shortforms().upsert(s.copy(outputPath = path, updatedAt = System.currentTimeMillis()))
    }

    suspend fun autosaveShortform(shortformId: String, timeline: ShortTimeline?) =
        db.shortforms().autosave(shortformId, timeline?.let { json.encodeToString(ShortTimeline.serializer(), it) })

    suspend fun timeline(projectId: String): Pair<String, ShortTimeline>? = db.shortforms().forProject(projectId)?.let {
        it.id to json.decodeFromString(ShortTimeline.serializer(), it.timelineJson)
    }

    fun observeBundle(projectId: String): Flow<ProjectBundle?> = combine(
        db.projects().observe(projectId),
        db.products().observeForProject(projectId),
        db.articles().observeForProject(projectId),
        db.shortforms().observeForProject(projectId),
        db.assets().observeForProject(projectId),
    ) { p, prod, art, sf, assets ->
        p ?: return@combine null
        ProjectBundle(
            project = p,
            product = prod?.let { decodeProduct(it.productJson) },
            intelligence = prod?.intelligenceJson?.let { runCatching { json.decodeFromString(ProductIntelligence.serializer(), it) }.getOrNull() },
            article = art?.let { runCatching { json.decodeFromString(Article.serializer(), it.articleJson) }.getOrNull() },
            articleId = art?.id,
            strategy = art?.let { runCatching { json.decodeFromString(ContentStrategy.serializer(), it.strategyJson) }.getOrNull() },
            visualPlan = art?.visualPlanJson?.let { runCatching { json.decodeFromString(VisualPlan.serializer(), it) }.getOrNull() },
            qualityScore = art?.qualityScore ?: 0,
            timeline = sf?.let { runCatching { json.decodeFromString(ShortTimeline.serializer(), it.timelineJson) }.getOrNull() },
            thumbnail = sf?.thumbnailJson?.let { runCatching { json.decodeFromString(ThumbnailSpec.serializer(), it) }.getOrNull() },
            shortformId = sf?.id,
            videoPath = sf?.outputPath,
            thumbnailPath = sf?.thumbnailPath,
            autosave = sf?.autosaveJson?.let { runCatching { json.decodeFromString(ShortTimeline.serializer(), it) }.getOrNull() },
            assets = assets,
            model = art?.model,
            promptVersion = art?.promptVersion,
        )
    }

    private fun decodeProduct(s: String): Product? = runCatching { json.decodeFromString(Product.serializer(), s) }.getOrNull()

    /** Previous articles (other projects) for Content Memory and the Duplicate Detector. */
    suspend fun otherArticles(projectId: String): List<Pair<String, Article>> = db.articles().allExcept(projectId).mapNotNull { e ->
        runCatching { e.title to json.decodeFromString(Article.serializer(), e.articleJson) }.getOrNull()
    }

    fun observeArticles() = db.articles().observeAll()
    fun observeShortforms() = db.shortforms().observeAll()

    suspend fun deleteProject(id: String) {
        db.projects().delete(id)
        files.deleteProject(id)
    }

    suspend fun deleteAll() {
        db.withTransaction { db.projects().deleteAll() }
        db.clearAllTables()
        files.deleteAll()
    }

    suspend fun exportBackup(projectId: String): String {
        val b = observeBundleOnce(projectId) ?: error("project not found")
        val backup = ProjectBackup(
            exportedAt = System.currentTimeMillis(), title = b.project.title, status = b.project.status.name, product = b.product,
            intelligence = b.intelligence, strategy = b.strategy, article = b.article, visualPlan = b.visualPlan, timeline = b.timeline, thumbnail = b.thumbnail,
            assets = b.assets.map { AssetRef(it.id, it.kind.name, java.io.File(it.path).name, it.copyrightType.name, it.aiGenerated, it.sourceUrl, it.width, it.height) },
        )
        return AppJson.encodeToString(ProjectBackup.serializer(), backup)
    }

    suspend fun observeBundleOnce(projectId: String): ProjectBundle? = observeBundle(projectId).first()
}
