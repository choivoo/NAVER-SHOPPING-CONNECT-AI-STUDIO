package com.shoppingconnect.aistudio.media.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Scoped-storage exports through MediaStore (no storage permission needed on Android 10+). */
@Singleton
class MediaExporter @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun saveVideo(file: File, displayName: String): Uri = save(file, displayName, "video/mp4", MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), Environment.DIRECTORY_MOVIES)

    suspend fun saveImage(file: File, displayName: String): Uri = save(file, displayName, "image/jpeg", MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), Environment.DIRECTORY_PICTURES)

    private suspend fun save(file: File, name: String, mime: String, collection: Uri, dir: String): Uri = withContext(Dispatchers.IO) {
        if (!file.exists()) throw AppException(ErrorKind.StorageFailure, "파일이 없습니다.")
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/AIStudio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(collection, values) ?: throw AppException(ErrorKind.StorageFailure)
        try {
            cr.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw AppException(ErrorKind.StorageFailure)
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            cr.delete(uri, null, null)
            throw if (e is AppException) e else AppException(ErrorKind.StorageFailure, cause = e)
        }
    }

    fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Android Sharesheet — YouTube, Instagram, TikTok, NAVER apps etc. appear if installed. */
    fun shareIntent(files: List<File>, mime: String, text: String? = null, title: String = "공유"): Intent {
        val uris = ArrayList(files.map { shareUri(it) })
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        send.type = mime
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
