package com.shoppingconnect.aistudio.core.network

import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.security.UrlSecurity
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HttpClients {
    const val USER_AGENT_BROWSER =
        "Mozilla/5.0 (Linux; Android 15; SM-F9xx) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"

    /** Client for first-party APIs (Claude, Naver Open API). TLS only, generous timeouts for AI calls. */
    fun api(): OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.MODERN_TLS))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(360, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Client for user-supplied product URLs: public-only DNS, no automatic redirects
     * (each hop is re-validated by [SafeFetcher]), small timeouts.
     */
    fun fetch(): OkHttpClient = OkHttpClient.Builder()
        .dns(UrlSecurity.publicOnlyDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** Result of a redirect-aware fetch of a user-supplied URL. */
data class FetchedPage(
    val requestedUrl: String,
    val finalUrl: HttpUrl,
    val redirects: List<String>,
    val status: Int,
    val contentType: String?,
    val body: String,
)

/**
 * Fetches a user-supplied URL with at most [maxRedirects] hops; every hop is re-checked
 * against [UrlSecurity] (scheme, host, private IP). Body size is capped.
 */
class SafeFetcher(private val client: OkHttpClient, private val maxRedirects: Int = 5, private val maxBytes: Long = 3L * 1024 * 1024) {
    suspend fun get(raw: String): FetchedPage {
        var url = UrlSecurity.requireAllowed(raw)
        val hops = mutableListOf<String>()
        repeat(maxRedirects + 1) {
            val req = Request.Builder().url(url)
                .header("User-Agent", HttpClients.USER_AGENT_BROWSER)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.6")
                .build()
            client.newCall(req).await().use { resp ->
                if (resp.isRedirect) {
                    val loc = resp.header("Location") ?: throw AppException(ErrorKind.ProductExtractionFailed, "리다이렉트 주소가 없습니다.")
                    val next = url.resolve(loc) ?: throw AppException(ErrorKind.InvalidUrl, "잘못된 리다이렉트 주소입니다.")
                    hops += next.toString()
                    url = UrlSecurity.requireAllowed(next.toString())
                    AppLog.d("SafeFetcher", "redirect → ${url.host}")
                    return@repeat
                }
                val body = resp.body ?: throw AppException(ErrorKind.ProductExtractionFailed, "빈 응답")
                val source = body.source()
                source.request(maxBytes)
                val bytes = source.buffer.let { buf -> buf.readByteArray(minOf(buf.size, maxBytes)) }
                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                return FetchedPage(raw, url, hops, resp.code, resp.header("Content-Type"), String(bytes, charset))
            }
        }
        throw AppException(ErrorKind.ProductExtractionFailed, "리다이렉트가 너무 많습니다.")
    }

    /** Downloads binary content (images) with the same SSRF protection. */
    suspend fun getBytes(raw: String, limit: Long = 25L * 1024 * 1024): ByteArray {
        var url = UrlSecurity.requireAllowed(raw)
        repeat(maxRedirects + 1) {
            val req = Request.Builder().url(url).header("User-Agent", HttpClients.USER_AGENT_BROWSER).build()
            client.newCall(req).await().use { resp ->
                if (resp.isRedirect) {
                    val next = resp.header("Location")?.let { url.resolve(it) } ?: throw AppException(ErrorKind.InvalidUrl)
                    url = UrlSecurity.requireAllowed(next.toString())
                    return@repeat
                }
                if (!resp.isSuccessful) throw AppException(ErrorKind.NetworkError, "HTTP ${resp.code}")
                val body = resp.body ?: throw AppException(ErrorKind.NetworkError, "빈 응답")
                val len = body.contentLength()
                if (len > limit) throw AppException(ErrorKind.UnsupportedFormat, "파일이 너무 큽니다.")
                val src = body.source()
                src.request(limit + 1)
                if (src.buffer.size > limit) throw AppException(ErrorKind.UnsupportedFormat, "파일이 너무 큽니다.")
                return src.buffer.readByteArray()
            }
        }
        throw AppException(ErrorKind.NetworkError, "리다이렉트가 너무 많습니다.")
    }
}
