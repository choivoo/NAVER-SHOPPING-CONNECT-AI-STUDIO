package com.shoppingconnect.aistudio.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.shoppingconnect.aistudio.ai.claude.AiGateway
import com.shoppingconnect.aistudio.ai.claude.ClaudeClient
import com.shoppingconnect.aistudio.ai.claude.ModelResolver
import com.shoppingconnect.aistudio.core.network.HttpClients
import com.shoppingconnect.aistudio.core.network.SafeFetcher
import com.shoppingconnect.aistudio.core.security.KeystoreSecretStore
import com.shoppingconnect.aistudio.core.security.SecretStore
import com.shoppingconnect.aistudio.data.db.AppDatabase
import com.shoppingconnect.aistudio.data.settings.DataStoreSettingsRepository
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.product.NaverShoppingSearchApi
import com.shoppingconnect.aistudio.product.ProductExtractionService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun secretStore(impl: KeystoreSecretStore): SecretStore
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun projectDao(db: AppDatabase) = db.projects()
    @Provides fun productDao(db: AppDatabase) = db.products()
    @Provides fun articleDao(db: AppDatabase) = db.articles()
    @Provides fun assetDao(db: AppDatabase) = db.assets()
    @Provides fun shortformDao(db: AppDatabase) = db.shortforms()
    @Provides fun generationDao(db: AppDatabase) = db.generations()
    @Provides fun promptDao(db: AppDatabase) = db.prompts()
    @Provides fun renderDao(db: AppDatabase) = db.renders()
    @Provides fun metricDao(db: AppDatabase) = db.metrics()

    @Provides @Singleton @Named("api") fun apiClient(): OkHttpClient = HttpClients.api()
    @Provides @Singleton @Named("fetch") fun fetchClient(): OkHttpClient = HttpClients.fetch()

    @Provides @Singleton fun safeFetcher(@Named("fetch") c: OkHttpClient) = SafeFetcher(c)
    @Provides @Singleton fun claude(@Named("api") c: OkHttpClient, s: SecretStore) = ClaudeClient(c, s)
    @Provides @Singleton fun resolver(c: ClaudeClient) = ModelResolver(c)
    @Provides @Singleton fun gateway(c: ClaudeClient, r: ModelResolver) = AiGateway(c, r)
    @Provides @Singleton fun extraction(f: SafeFetcher) = ProductExtractionService(f)
    @Provides @Singleton fun naverSearch(@Named("api") c: OkHttpClient, s: SecretStore) = NaverShoppingSearchApi(c, s)
    @Provides fun workManager(@ApplicationContext ctx: Context): WorkManager = WorkManager.getInstance(ctx)
}
