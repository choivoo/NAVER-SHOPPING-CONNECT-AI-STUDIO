package com.shoppingconnect.aistudio.core.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind

/**
 * Defensive image decoding: sniffs magic bytes (never trusts the declared MIME type),
 * caps byte size and pixel count, and always samples down to a bounded size so a
 * malicious or huge image cannot crash the app with OOM.
 */
object ImageSecurity {
    const val MAX_BYTES = 25L * 1024 * 1024
    const val MAX_PIXELS = 60_000_000L

    enum class ImageType(val mime: String, val ext: String) {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp"), GIF("image/gif", "gif"), HEIF("image/heif", "heic")
    }

    fun sniff(bytes: ByteArray): ImageType? {
        if (bytes.size < 12) return null
        fun at(i: Int) = bytes[i].toInt() and 0xff
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> ImageType.JPEG
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> ImageType.PNG
            at(0) == 'R'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == 'F'.code &&
                at(8) == 'W'.code && at(9) == 'E'.code && at(10) == 'B'.code && at(11) == 'P'.code -> ImageType.WEBP
            at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code -> ImageType.GIF
            String(bytes, 4, 8, Charsets.US_ASCII).let { it.startsWith("ftypheic") || it.startsWith("ftypheix") || it.startsWith("ftypmif1") || it.startsWith("ftyphevc") } -> ImageType.HEIF
            else -> null
        }
    }

    fun validate(bytes: ByteArray): ImageType {
        if (bytes.size > MAX_BYTES) throw AppException(ErrorKind.UnsupportedFormat, "이미지가 너무 큽니다 (최대 25MB).")
        val type = sniff(bytes) ?: throw AppException(ErrorKind.UnsupportedFormat, "이미지 파일이 아닙니다.")
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw AppException(ErrorKind.UnsupportedFormat, "이미지를 해석할 수 없습니다.")
        if (opts.outWidth.toLong() * opts.outHeight > MAX_PIXELS) throw AppException(ErrorKind.UnsupportedFormat, "이미지 해상도가 너무 큽니다.")
        return type
    }

    fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxDim || height / (sample * 2) >= maxDim) sample *= 2
        return sample
    }

    /** Decodes a file with bounds checking, downsampled so the long edge is ≤ [maxDim]. */
    fun decodeFileBounded(path: String, maxDim: Int = 2160): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        if (opts.outWidth.toLong() * opts.outHeight > MAX_PIXELS) return null
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(opts.outWidth, opts.outHeight, maxDim)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            val bmp = BitmapFactory.decodeFile(path, decode) ?: return null
            val longEdge = maxOf(bmp.width, bmp.height)
            if (longEdge > maxDim) {
                val scale = maxDim.toFloat() / longEdge
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                    .also { if (it !== bmp) bmp.recycle() }
            } else bmp
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
