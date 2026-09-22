package com.shoppingconnect.aistudio.core.common

import android.util.Log
import com.shoppingconnect.aistudio.BuildConfig
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * App-wide logger. Every message passes through [Redactor] so API keys, OAuth tokens,
 * passwords and e-mail addresses never reach Logcat or the in-app developer log.
 */
object AppLog {
    data class Entry(val time: Long, val level: Char, val tag: String, val message: String)

    private const val MAX_ENTRIES = 500
    private val buffer = ConcurrentLinkedDeque<Entry>()

    fun d(tag: String, msg: String) = log('D', tag, msg, null)
    fun i(tag: String, msg: String) = log('I', tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = log('W', tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log('E', tag, msg, t)

    fun entries(): List<Entry> = buffer.toList()
    fun clear() = buffer.clear()

    private fun log(level: Char, tag: String, msg: String, t: Throwable?) {
        val safe = Redactor.redact(if (t != null) "$msg | ${t.javaClass.simpleName}: ${t.message}" else msg)
        buffer.addLast(Entry(System.currentTimeMillis(), level, tag, safe))
        while (buffer.size > MAX_ENTRIES) buffer.pollFirst()
        if (!BuildConfig.DEBUG && level == 'D') return
        when (level) {
            'D' -> Log.d("AIStudio/$tag", safe)
            'I' -> Log.i("AIStudio/$tag", safe)
            'W' -> Log.w("AIStudio/$tag", safe)
            else -> Log.e("AIStudio/$tag", safe)
        }
    }
}

object Redactor {
    private val patterns = listOf(
        Regex("sk-ant-[A-Za-z0-9_\\-]+") to "sk-ant-***",
        Regex("(?i)(x-api-key|authorization|access_token|refresh_token|client_secret|api[_-]?key|password|passwd|token)([\"'\\s:=]+)(Bearer\\s+)?[^\\s\"'&,}]+") to "$1$2***",
        Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+") to "Bearer ***",
        Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}") to "***@***",
        Regex("(?i)([?&](code|state|access_token)=)[^&\\s]+") to "$1***",
    )

    fun redact(input: String): String = patterns.fold(input) { acc, (re, rep) -> re.replace(acc, rep) }
}
