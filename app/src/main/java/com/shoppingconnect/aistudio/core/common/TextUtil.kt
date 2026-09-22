package com.shoppingconnect.aistudio.core.common

import java.security.MessageDigest
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray()).joinToString("") { "%02x".format(it) }

private val dateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREA)
private val dayFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA)

fun Long.formatDateTime(): String = dateFmt.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
fun Long.formatDate(): String = dayFmt.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun formatPrice(amount: Long?, currency: String? = "KRW"): String? {
    if (amount == null || amount <= 0) return null
    return if (currency == null || currency == "KRW") {
        NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
    } else "$currency ${NumberFormat.getNumberInstance(Locale.US).format(amount)}"
}

fun Long.formatBytes(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024L * 1024 * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    else -> "%.2f GB".format(this / (1024.0 * 1024 * 1024))
}

fun Long.formatDurationMs(): String {
    val totalSec = this / 1000
    return "%d:%02d.%d".format(totalSec / 60, totalSec % 60, (this % 1000) / 100)
}

/** Grapheme-aware-ish length for Korean/emoji text (counts code points). */
fun String.cpLength(): Int = codePointCount(0, length)

fun String.truncateCp(max: Int, ellipsis: String = "…"): String {
    if (cpLength() <= max) return this
    val end = offsetByCodePoints(0, max)
    return substring(0, end).trimEnd() + ellipsis
}
