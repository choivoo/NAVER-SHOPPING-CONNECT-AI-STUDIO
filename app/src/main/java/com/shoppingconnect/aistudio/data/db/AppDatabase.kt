package com.shoppingconnect.aistudio.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProjectEntity::class, ProductEntity::class, ArticleEntity::class, ArticleVersionEntity::class,
        MediaAssetEntity::class, ShortformEntity::class, GenerationEntity::class, PromptVersionEntity::class,
        RenderJobEntity::class, ManualMetricEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun products(): ProductDao
    abstract fun articles(): ArticleDao
    abstract fun assets(): MediaAssetDao
    abstract fun shortforms(): ShortformDao
    abstract fun generations(): GenerationDao
    abstract fun prompts(): PromptVersionDao
    abstract fun renders(): RenderJobDao
    abstract fun metrics(): ManualMetricDao

    companion object {
        const val NAME = "aistudio.db"
    }
}
