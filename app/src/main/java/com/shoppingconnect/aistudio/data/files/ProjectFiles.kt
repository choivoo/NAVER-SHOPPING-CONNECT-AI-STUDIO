package com.shoppingconnect.aistudio.data.files

import android.content.Context
import com.shoppingconnect.aistudio.domain.model.AssetKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App-private project storage: files/projects/<projectId>/<kind>/… (scoped storage, no permissions needed). */
@Singleton
class ProjectFiles @Inject constructor(@ApplicationContext private val context: Context) {
    val root: File get() = File(context.filesDir, "projects").apply { mkdirs() }
    val cacheRoot: File get() = context.cacheDir
    val shareDir: File get() = File(context.cacheDir, "share").apply { mkdirs() }
    val exportsDir: File get() = File(context.filesDir, "exports").apply { mkdirs() }

    fun projectDir(projectId: String): File = File(root, projectId.replace(Regex("[^A-Za-z0-9-]"), "")).apply { mkdirs() }

    fun dir(projectId: String, kind: AssetKind): File = File(projectDir(projectId), kind.name.lowercase()).apply { mkdirs() }

    fun newFile(projectId: String, kind: AssetKind, name: String): File = File(dir(projectId, kind), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    fun workDir(projectId: String): File = File(context.cacheDir, "work/$projectId").apply { mkdirs() }

    /** Only files inside app storage may be read/deleted through asset paths. */
    fun isInside(path: String): Boolean {
        val canon = File(path).canonicalPath
        return canon.startsWith(context.filesDir.canonicalPath) || canon.startsWith(context.cacheDir.canonicalPath)
    }

    fun sizeOf(f: File): Long = if (!f.exists()) 0 else if (f.isFile) f.length() else f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    data class Usage(val images: Long, val videos: Long, val audio: Long, val cache: Long, val total: Long)

    fun usage(projectId: String? = null): Usage {
        val base = projectId?.let { projectDir(it) } ?: root
        var images = 0L; var videos = 0L; var audio = 0L
        base.walkBottomUp().filter { it.isFile }.forEach { f ->
            when (f.extension.lowercase()) {
                "jpg", "jpeg", "png", "webp", "gif", "heic" -> images += f.length()
                "mp4" -> videos += f.length()
                "wav", "m4a", "aac", "mp3", "ogg" -> audio += f.length()
            }
        }
        val cache = if (projectId == null) sizeOf(context.cacheDir) else sizeOf(workDir(projectId))
        return Usage(images, videos, audio, cache, images + videos + audio + cache)
    }

    /** Clears regenerable caches only; project originals are never touched here. */
    fun clearCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun deleteProject(projectId: String) { projectDir(projectId).deleteRecursively(); workDir(projectId).deleteRecursively() }

    fun deleteAll() { root.deleteRecursively(); exportsDir.deleteRecursively(); clearCache() }
}
