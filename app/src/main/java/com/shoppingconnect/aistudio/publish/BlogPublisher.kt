package com.shoppingconnect.aistudio.publish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.shoppingconnect.aistudio.content.ArticleFormatter
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.media.export.MediaExporter
import java.io.File

data class PublishPayload(val article: Article, val images: List<File>, val blogWriteUrl: String, val projectTitle: String)

sealed interface PublishResult {
    /** Content handed to the user's NAVER Blog editor; the user publishes it themselves. */
    data class HandedOff(val savedImages: Int, val openedApp: Boolean, val message: String) : PublishResult
    data class Shared(val message: String) : PublishResult
    data class Unavailable(val reason: String) : PublishResult
}

/**
 * Posting adapter. v1.0 does NOT assume a public NAVER Blog write API exists — posting is
 * always completed by the user inside NAVER's own editor after review.
 */
interface BlogPublisher {
    val id: String
    val label: String
    val description: String
    fun isAvailable(context: Context): Boolean
    suspend fun publish(context: Context, payload: PublishPayload): PublishResult
}

/** Default: clipboard (rich HTML + text) + images saved to the gallery + open NAVER Blog (app or web, user's own session). */
class NaverBlogHandoffPublisher(private val exporter: MediaExporter) : BlogPublisher {
    override val id = "naver_handoff"
    override val label = "네이버 블로그에서 게시"
    override val description = "글을 클립보드에 복사하고 이미지를 갤러리(Pictures/AIStudio)에 저장한 뒤 네이버 블로그 편집 화면을 엽니다. 붙여넣기 후 직접 확인하고 게시하세요."

    override fun isAvailable(context: Context) = true

    override suspend fun publish(context: Context, payload: PublishPayload): PublishResult {
        val html = ArticleFormatter.toHtml(payload.article)
        val text = ArticleFormatter.toPlainText(payload.article)
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newHtmlText("블로그 글: ${payload.article.title}", text, html))
        var saved = 0
        payload.images.forEachIndexed { i, f ->
            runCatching { exporter.saveImage(f, "${payload.projectTitle.take(30)}_${i + 1}.jpg"); saved++ }
                .onFailure { AppLog.w("Publish", "image export failed", it) }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(NAVER_BLOG_PACKAGE)
        return if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            PublishResult.HandedOff(saved, true, "네이버 블로그 앱을 열었습니다. 글쓰기 → 본문에 붙여넣기 후 이미지 ${saved}장을 갤러리에서 추가하세요.")
        } else {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(context, Uri.parse(payload.blogWriteUrl))
            PublishResult.HandedOff(saved, false, "네이버 블로그 글쓰기 화면을 열었습니다(로그인은 네이버 페이지에서 직접). 본문에 붙여넣기 후 이미지 ${saved}장을 추가하세요.")
        }
    }

    companion object { const val NAVER_BLOG_PACKAGE = "com.nhn.android.blog" }
}

/** Android Sharesheet with text + images (e.g. share straight into the NAVER Blog app). */
class SharePublisher(private val exporter: MediaExporter) : BlogPublisher {
    override val id = "share"
    override val label = "공유하기"
    override val description = "Android 공유 시트로 글과 이미지를 다른 앱(네이버 블로그 앱 포함)에 전달합니다."
    override fun isAvailable(context: Context) = true
    override suspend fun publish(context: Context, payload: PublishPayload): PublishResult {
        val text = ArticleFormatter.toPlainText(payload.article)
        val intent = if (payload.images.isEmpty()) {
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "블로그 글 공유")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else exporter.shareIntent(payload.images, "image/*", text, "블로그 글 공유")
        context.startActivity(intent)
        return PublishResult.Shared("공유 시트를 열었습니다.")
    }
}

/** Placeholder adapter for a future official API; reports unavailability instead of faking success. */
class FutureOfficialApiPublisher : BlogPublisher {
    override val id = "official_api"
    override val label = "공식 API 자동 게시"
    override val description = "현재 공개된 네이버 블로그 글쓰기 공식 API가 확인되지 않아 비활성화되어 있습니다."
    override fun isAvailable(context: Context) = false
    override suspend fun publish(context: Context, payload: PublishPayload) = PublishResult.Unavailable(description)
}
