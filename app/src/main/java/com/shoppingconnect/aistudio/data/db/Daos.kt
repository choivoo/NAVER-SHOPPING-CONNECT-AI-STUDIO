package com.shoppingconnect.aistudio.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Upsert suspend fun upsert(p: ProjectEntity)
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: String): ProjectEntity?
    @Query("SELECT * FROM projects WHERE id = :id") fun observe(id: String): Flow<ProjectEntity?>
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") suspend fun all(): List<ProjectEntity>

    @Query(
        """SELECT * FROM projects
           WHERE (:status IS NULL OR status = :status)
             AND (:q = '' OR title LIKE '%' || :q || '%' OR IFNULL(brand,'') LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' OR status LIKE '%' || :q || '%')
           ORDER BY updatedAt DESC""",
    )
    fun search(q: String, status: ProjectStatus?): Flow<List<ProjectEntity>>

    @Query("UPDATE projects SET status = :status, updatedAt = :now WHERE id = :id") suspend fun setStatus(id: String, status: ProjectStatus, now: Long)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM projects") suspend fun deleteAll()
    @Query("SELECT COUNT(*) FROM projects WHERE createdAt >= :since") fun countSince(since: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM projects WHERE hasBlog = 1 AND status IN ('READY','PUBLISHED') AND updatedAt >= :since") fun blogsDoneSince(since: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM projects WHERE status = 'PUBLISHED' AND IFNULL(publishedAt, 0) >= :since") fun publishedSince(since: Long): Flow<Int>
}

@Dao
interface ProductDao {
    @Upsert suspend fun upsert(p: ProductEntity)
    @Query("SELECT * FROM products WHERE projectId = :projectId LIMIT 1") suspend fun forProject(projectId: String): ProductEntity?
    @Query("SELECT * FROM products WHERE projectId = :projectId LIMIT 1") fun observeForProject(projectId: String): Flow<ProductEntity?>
    @Query("SELECT * FROM products WHERE cacheKey = :key AND intelligenceJson IS NOT NULL AND fetchedAt >= :minTime ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun cachedAnalysis(key: String, minTime: Long): ProductEntity?
}

@Dao
interface ArticleDao {
    @Upsert suspend fun upsert(a: ArticleEntity)
    @Query("SELECT * FROM articles WHERE projectId = :projectId") suspend fun forProject(projectId: String): ArticleEntity?
    @Query("SELECT * FROM articles WHERE projectId = :projectId") fun observeForProject(projectId: String): Flow<ArticleEntity?>
    @Query("SELECT * FROM articles ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ArticleEntity>>
    @Query("SELECT * FROM articles WHERE projectId != :excludeProjectId") suspend fun allExcept(excludeProjectId: String): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVersion(v: ArticleVersionEntity)
    @Query("SELECT * FROM article_versions WHERE articleId = :articleId ORDER BY createdAt DESC") fun observeVersions(articleId: String): Flow<List<ArticleVersionEntity>>
    @Query("SELECT * FROM article_versions WHERE id = :id") suspend fun version(id: String): ArticleVersionEntity?
    @Query("DELETE FROM article_versions WHERE articleId = :articleId AND id NOT IN (SELECT id FROM article_versions WHERE articleId = :articleId ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimVersions(articleId: String, keep: Int)
}

@Dao
interface MediaAssetDao {
    @Upsert suspend fun upsert(a: MediaAssetEntity)
    @Query("SELECT * FROM media_assets WHERE id = :id") suspend fun get(id: String): MediaAssetEntity?
    @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY createdAt ASC") fun observeForProject(projectId: String): Flow<List<MediaAssetEntity>>
    @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY createdAt ASC") suspend fun forProject(projectId: String): List<MediaAssetEntity>
    @Query("SELECT * FROM media_assets") suspend fun all(): List<MediaAssetEntity>
    @Delete suspend fun delete(a: MediaAssetEntity)
    @Query("DELETE FROM media_assets WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface ShortformDao {
    @Upsert suspend fun upsert(s: ShortformEntity)
    @Query("SELECT * FROM shortforms WHERE projectId = :projectId") suspend fun forProject(projectId: String): ShortformEntity?
    @Query("SELECT * FROM shortforms WHERE projectId = :projectId") fun observeForProject(projectId: String): Flow<ShortformEntity?>
    @Query("SELECT * FROM shortforms WHERE id = :id") suspend fun get(id: String): ShortformEntity?
    @Query("SELECT * FROM shortforms ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ShortformEntity>>
    @Query("UPDATE shortforms SET autosaveJson = :json WHERE id = :id") suspend fun autosave(id: String, json: String?)
    @Query("SELECT COUNT(*) FROM shortforms WHERE outputPath IS NOT NULL AND updatedAt >= :since") fun renderedSince(since: Long): Flow<Int>
}

@Dao
interface GenerationDao {
    @Upsert suspend fun upsert(g: GenerationEntity)
    @Query("SELECT * FROM generations WHERE id = :id") suspend fun get(id: String): GenerationEntity?
    @Query("SELECT * FROM generations WHERE id = :id") fun observe(id: String): Flow<GenerationEntity?>
    @Query("SELECT * FROM generations WHERE projectId = :projectId ORDER BY startedAt DESC") fun observeForProject(projectId: String): Flow<List<GenerationEntity>>
    @Query("SELECT * FROM generations WHERE batchId = :batchId ORDER BY startedAt ASC") fun observeBatch(batchId: String): Flow<List<GenerationEntity>>
    @Query("SELECT * FROM generations WHERE batchId IS NOT NULL ORDER BY startedAt DESC LIMIT 50") fun observeBatches(): Flow<List<GenerationEntity>>
    @Query("SELECT * FROM generations WHERE state IN (:states)") suspend fun inStates(states: List<GenerationState>): List<GenerationEntity>
    @Query("SELECT * FROM generations ORDER BY startedAt DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<GenerationEntity>>
}

@Dao
interface PromptVersionDao {
    @Upsert suspend fun upsert(p: PromptVersionEntity)
    @Query("SELECT * FROM prompt_versions WHERE id = :id") suspend fun get(id: String): PromptVersionEntity?
    @Query("SELECT * FROM prompt_versions ORDER BY id") fun observeAll(): Flow<List<PromptVersionEntity>>
}

@Dao
interface RenderJobDao {
    @Upsert suspend fun upsert(r: RenderJobEntity)
    @Query("SELECT * FROM render_jobs WHERE id = :id") suspend fun get(id: String): RenderJobEntity?
    @Query("SELECT * FROM render_jobs ORDER BY createdAt DESC LIMIT 50") fun observeAll(): Flow<List<RenderJobEntity>>
    @Query("SELECT * FROM render_jobs WHERE projectId = :projectId ORDER BY createdAt DESC") fun observeForProject(projectId: String): Flow<List<RenderJobEntity>>
    @Query("UPDATE render_jobs SET progress = :progress, state = :state WHERE id = :id") suspend fun progress(id: String, progress: Int, state: RenderState)
    @Query("SELECT * FROM render_jobs WHERE state IN ('QUEUED','RENDERING')") suspend fun active(): List<RenderJobEntity>
}

@Dao
interface ManualMetricDao {
    @Upsert suspend fun upsert(m: ManualMetricEntity)
    @Query("SELECT * FROM manual_metrics ORDER BY date DESC") fun observeAll(): Flow<List<ManualMetricEntity>>
    @Query("DELETE FROM manual_metrics WHERE id = :id") suspend fun delete(id: String)
}
