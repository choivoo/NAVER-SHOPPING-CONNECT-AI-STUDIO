package com.shoppingconnect.aistudio.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.core.network.SafeFetcher
import com.shoppingconnect.aistudio.core.security.ImageSecurity
import com.shoppingconnect.aistudio.data.db.MediaAssetDao
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaAssetDao,
    private val files: ProjectFiles,
    private val fetcher: SafeFetcher,
) {
    fun observe(projectId: String): Flow<List<MediaAssetEntity>> = dao.observeForProject(projectId)
    suspend fun list(projectId: String) = dao.forProject(projectId)
    suspend fun get(id: String) = dao.get(id)

    /** Downloads product images offered by the product source page (SSRF-safe, validated). */
    suspend fun downloadProductImages(projectId: String, urls: List<String>, max: Int = 8): List<MediaAssetEntity> = withContext(Dispatchers.IO) {
        urls.take(max).mapNotNull { url ->
            try {
                val bytes = fetcher.getBytes(url)
                val type = ImageSecurity.validate(bytes)
                val f = files.newFile(projectId, AssetKind.ORIGINAL, "${newId()}.${type.ext}")
                f.writeBytes(bytes)
                register(projectId, AssetKind.ORIGINAL, f, type.mime, url, CopyrightType.PRODUCT_SOURCE, aiGenerated = false, usage = "product")
            } catch (e: Exception) {
                AppLog.w("Media", "image download failed", e)
                null
            }
        }
    }

    /** Copies a Photo Picker / SAF uri into project storage after sniffing and bounds checks. */
    suspend fun importUri(projectId: String, uri: Uri, copyright: CopyrightType = CopyrightType.USER_UPLOAD, kind: AssetKind = AssetKind.ORIGINAL): MediaAssetEntity = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mime = cr.getType(uri).orEmpty()
        if (mime.startsWith("video/")) {
            val f = files.newFile(projectId, AssetKind.VIDEO, "${newId()}.mp4")
            cr.openInputStream(uri)?.use { input -> FileOutputStream(f).use { input.copyTo(it) } } ?: throw AppException(ErrorKind.StorageFailure)
            if (f.length() > 500L * 1024 * 1024) { f.delete(); throw AppException(ErrorKind.UnsupportedFormat, "영상이 너무 큽니다 (최대 500MB).") }
            return@withContext register(projectId, AssetKind.VIDEO, f, "video/mp4", null, copyright, false, "user_video")
        }
        if (mime.startsWith("audio/")) {
            val ext = when { mime.contains("wav") -> "wav"; mime.contains("mpeg") -> "mp3"; mime.contains("ogg") -> "ogg"; else -> "m4a" }
            val f = files.newFile(projectId, AssetKind.AUDIO, "${newId()}.$ext")
            cr.openInputStream(uri)?.use { input -> FileOutputStream(f).use { input.copyTo(it) } } ?: throw AppException(ErrorKind.StorageFailure)
            if (f.length() > 100L * 1024 * 1024) { f.delete(); throw AppException(ErrorKind.UnsupportedFormat, "오디오 파일이 너무 큽니다.") }
            return@withContext register(projectId, AssetKind.AUDIO, f, mime, null, copyright, false, "user_audio")
        }
        val bytes = cr.openInputStream(uri)?.use { input ->
            val buf = input.readBytes()
            if (buf.size > ImageSecurity.MAX_BYTES) throw AppException(ErrorKind.UnsupportedFormat, "이미지가 너무 큽니다.")
            buf
        } ?: throw AppException(ErrorKind.StorageFailure)
        val type = ImageSecurity.validate(bytes)
        val f = files.newFile(projectId, kind, "${newId()}.${type.ext}")
        f.writeBytes(bytes)
        register(projectId, kind, f, type.mime, null, copyright, false, "user_image")
    }

    suspend fun saveBitmap(
        projectId: String, bitmap: Bitmap, kind: AssetKind, usage: String, metaJson: String? = null,
        copyright: CopyrightType = CopyrightType.APP_GENERATED, replaceId: String? = null, jpegQuality: Int = 92,
    ): MediaAssetEntity = withContext(Dispatchers.IO) {
        val id = replaceId ?: newId()
        val f = files.newFile(projectId, kind, "$id.jpg")
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, it) }
        // Blog upload optimisation: re-encode oversized files at lower quality.
        if (f.length() > 1_800_000 && jpegQuality > 80) FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        register(projectId, kind, f, "image/jpeg", null, copyright, aiGenerated = copyright == CopyrightType.AI_GENERATED || copyright == CopyrightType.APP_GENERATED, usage = usage, metaJson = metaJson, id = id)
    }

    suspend fun registerFile(projectId: String, kind: AssetKind, file: File, mime: String, copyright: CopyrightType, usage: String, metaJson: String? = null): MediaAssetEntity =
        register(projectId, kind, file, mime, null, copyright, copyright == CopyrightType.AI_GENERATED, usage, metaJson)

    private suspend fun register(
        projectId: String, kind: AssetKind, f: File, mime: String, sourceUrl: String?, copyright: CopyrightType,
        aiGenerated: Boolean, usage: String, metaJson: String? = null, id: String = newId(),
    ): MediaAssetEntity {
        var w = 0; var h = 0
        if (mime.startsWith("image/")) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, o); w = o.outWidth; h = o.outHeight
        }
        val e = MediaAssetEntity(id, projectId, kind, f.absolutePath, mime, sourceUrl, copyright, aiGenerated, w, h, f.length(), usage, metaJson, System.currentTimeMillis())
        dao.upsert(e)
        return e
    }

    suspend fun delete(asset: MediaAssetEntity) = withContext(Dispatchers.IO) {
        if (files.isInside(asset.path)) File(asset.path).delete()
        dao.delete(asset)
    }

    suspend fun loadBitmap(assetId: String?, maxDim: Int = 2160): Bitmap? = withContext(Dispatchers.IO) {
        val a = assetId?.let { dao.get(it) } ?: return@withContext null
        if (!a.mimeType.startsWith("image/") || !files.isInside(a.path)) return@withContext null
        ImageSecurity.decodeFileBounded(a.path, maxDim)
    }

    fun loadBitmapPath(path: String?, maxDim: Int = 2160): Bitmap? =
        path?.takeIf { files.isInside(it) }?.let { ImageSecurity.decodeFileBounded(it, maxDim) }
}
