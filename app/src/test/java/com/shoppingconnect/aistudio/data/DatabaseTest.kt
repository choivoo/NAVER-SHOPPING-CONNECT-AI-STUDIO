package com.shoppingconnect.aistudio.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.data.db.AppDatabase
import com.shoppingconnect.aistudio.data.db.GenerationEntity
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.Block
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.ProductIntelligence
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.domain.model.Scene
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.pipeline.DemoData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ProjectRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ProjectRepository(db, ProjectFiles(ctx))
    }

    @After fun tearDown() = db.close()

    @Test fun createUpdateReadDeleteWithCascade() = runTest {
        val product = DemoData.product()
        val p = repo.createProject(product, "v1")
        repo.saveProduct(p.id, product, ProductIntelligence(summary = "요약", keyFeatures = listOf("a")))
        val article = Article(title = "제목", blocks = listOf(Block(type = BlockType.PARAGRAPH, text = "본문")))
        val articleId = repo.saveArticle(p.id, article, ContentStrategy(), "Draft 1", "claude-opus-5-5", "v1", 70)
        repo.saveArticle(p.id, article.copy(title = "수정 제목"), ContentStrategy(), "User Edit", "", "v1", 72, markEdited = true)
        repo.saveShortform(p.id, ShortTimeline(scenes = listOf(Scene(durationMs = 2000))), null)
        db.assets().upsert(MediaAssetEntity(newId(), p.id, AssetKind.ORIGINAL, "/x.jpg", "image/jpeg", null, CopyrightType.PRODUCT_SOURCE, false, 10, 10, 1, "product", null, 0))

        val b = repo.observeBundle(p.id).first()!!
        assertThat(b.article!!.title).isEqualTo("수정 제목")
        assertThat(b.intelligence!!.summary).isEqualTo("요약")
        assertThat(b.timeline!!.scenes).hasSize(1)
        assertThat(b.project.hasBlog).isTrue()
        assertThat(b.project.hasShorts).isTrue()
        assertThat(repo.observeVersions(articleId).first().map { it.label }).containsExactly("User Edit", "Draft 1")
        assertThat(repo.cachedIntelligence(product.sourceUrl)).isNotNull()

        repo.setStatus(p.id, ProjectStatus.PUBLISHED)
        assertThat(repo.project(p.id)!!.publishedAt).isNotNull()
        assertThat(repo.search("수정", null).first()).hasSize(1)
        assertThat(repo.search("", ProjectStatus.DRAFT).first()).isEmpty()

        val json = repo.exportBackup(p.id)
        assertThat(json).contains("aistudio.project.v1")

        repo.deleteProject(p.id)
        assertThat(db.articles().forProject(p.id)).isNull()
        assertThat(db.assets().forProject(p.id)).isEmpty()
        assertThat(db.shortforms().forProject(p.id)).isNull()
    }

    @Test fun generationRoundTrip() = runTest {
        val p = repo.createProject(DemoData.product(), "v1")
        val g = GenerationEntity(newId(), p.id, "https://x", GenerationState.RUNNING, "[]", "{}", "", "v1", null, null, "[]", null, null, 0, 0, 0, null)
        db.generations().upsert(g)
        assertThat(db.generations().inStates(listOf(GenerationState.RUNNING)).single().id).isEqualTo(g.id)
    }

    @Test fun versionTrimKeepsLatest() = runTest {
        val p = repo.createProject(DemoData.product(), "v1")
        var id = ""
        repeat(45) { i -> id = repo.saveArticle(p.id, Article(title = "t$i"), ContentStrategy(), "v$i", "", "v1", 0) }
        assertThat(repo.observeVersions(id).first()).hasSize(40)
    }

    @Test fun settingsSerialisationRoundTrip() {
        val s = AppSettings(onboardingDone = true, disclosureText = "고지", shortsDurationSec = 45)
        val back = AppJson.decodeFromString(AppSettings.serializer(), AppJson.encodeToString(AppSettings.serializer(), s))
        assertThat(back).isEqualTo(s)
        // Unknown/renamed fields from future versions must not break decoding.
        assertThat(AppJson.decodeFromString(AppSettings.serializer(), """{"onboardingDone":true,"futureField":1}""").onboardingDone).isTrue()
    }
}
