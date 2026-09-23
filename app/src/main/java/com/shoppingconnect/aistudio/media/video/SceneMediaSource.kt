package com.shoppingconnect.aistudio.media.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.shoppingconnect.aistudio.core.security.ImageSecurity
import com.shoppingconnect.aistudio.domain.model.Scene
import java.io.File

/**
 * Bounded-memory media source: images are decoded once, downsampled to what the output needs,
 * and held in an LRU cache sized in bytes (prevents OOM with many high-resolution assets).
 */
class SceneMediaSource(
    private val resolvePath: (Scene) -> String?,
    private val maxDim: Int,
    cacheBytes: Int = 96 * 1024 * 1024,
    private val accurateVideoFrames: Boolean = true,
) : FrameMediaSource {
    private val cache = object : LruCache<String, Bitmap>(cacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val missing = HashSet<String>()
    private val retrievers = HashMap<String, MediaMetadataRetriever>()

    override fun image(scene: Scene): Bitmap? {
        val path = resolvePath(scene) ?: return null
        if (path in missing) return null
        cache.get(path)?.let { return it }
        val bmp = ImageSecurity.decodeFileBounded(path, maxDim)
        if (bmp == null) { missing += path; return null }
        cache.put(path, bmp)
        return bmp
    }

    override fun blurred(scene: Scene): Bitmap? {
        val path = resolvePath(scene) ?: return null
        val key = "blur:$path"
        cache.get(key)?.let { return it }
        val src = if (scene.isVideo) videoFrame(scene, scene.trimStartMs) else image(scene)
        src ?: return null
        val small = Bitmap.createScaledBitmap(src, (src.width / 16).coerceAtLeast(2), (src.height / 16).coerceAtLeast(2), true)
        cache.put(key, small)
        return small
    }

    override fun videoFrame(scene: Scene, localMs: Long): Bitmap? {
        val path = resolvePath(scene) ?: return null
        if (path in missing || !File(path).exists()) return null
        val r = retrievers.getOrPut(path) { MediaMetadataRetriever().apply { runCatching { setDataSource(path) }.onFailure { missing += path } } }
        val us = localMs * 1000
        val option = if (accurateVideoFrames) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val key = "v:$path:${if (accurateVideoFrames) localMs / 33 else localMs / 500}"
        cache.get(key)?.let { return it }
        val bmp = runCatching { r.getScaledFrameAtTime(us, option, maxDim, maxDim) }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun release() {
        retrievers.values.forEach { runCatching { it.release() } }
        retrievers.clear()
        cache.evictAll()
    }
}
