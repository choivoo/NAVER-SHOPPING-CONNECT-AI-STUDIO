package com.shoppingconnect.aistudio.ai.claude

import com.shoppingconnect.aistudio.BuildConfig
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.core.network.await
import com.shoppingconnect.aistudio.core.security.SecretKeyName
import com.shoppingconnect.aistudio.core.security.SecretStore
import com.shoppingconnect.aistudio.data.settings.AiConnection
import com.shoppingconnect.aistudio.data.settings.AppSettings
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

data class ChatTurn(val role: String, val text: String)

data class ClaudeRequest(
    val model: String,
    val system: String,
    val turns: List<ChatTurn>,
    val maxTokens: Int = 16000,
    val effort: String? = null,
    val jsonSchema: JsonObject? = null,
)

data class ClaudeResponse(
    val text: String,
    val model: String,
    val stopReason: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val fellBack: Boolean,
)

data class ModelInfo(val id: String, val displayName: String, val maxInputTokens: Long?, val maxOutputTokens: Long?)

/**
 * Raw-HTTP Claude Messages API client (OkHttp). Works against api.anthropic.com with a
 * user-held key (developer mode) or against a backend proxy that holds the key server-side.
 *
 * Optional request features (JSON-schema structured output, server-side refusal fallback) are
 * dropped automatically when an endpoint/model rejects them with a 400, so the app keeps
 * working behind proxies or on models that don't support them.
 */
class ClaudeClient(
    private val http: OkHttpClient,
    private val secrets: SecretStore,
) {
    val totalInputTokens = AtomicLong(0)
    val totalOutputTokens = AtomicLong(0)

    data class Endpoint(val baseUrl: String, val headers: Map<String, String>, val isDirect: Boolean)

    fun isConfigured(settings: AppSettings): Boolean = runCatching { endpoint(settings) }.isSuccess

    fun endpoint(settings: AppSettings): Endpoint = when (settings.aiConnection) {
        AiConnection.DIRECT -> {
            val key = secrets.get(SecretKeyName.CLAUDE_API_KEY)
                ?: throw AppException(ErrorKind.AiNotConfigured)
            Endpoint(DIRECT_BASE, mapOf("x-api-key" to key), isDirect = true)
        }
        AiConnection.PROXY -> {
            val base = settings.proxyBaseUrl.ifBlank { BuildConfig.AI_PROXY_BASE_URL }.trimEnd('/')
            val url = base.toHttpUrlOrNull()
            if (base.isBlank() || url == null || !url.isHttps) throw AppException(ErrorKind.AiNotConfigured, "프록시 주소(https)를 설정해 주세요.")
            val token = secrets.get(SecretKeyName.AI_PROXY_TOKEN)
            Endpoint(base, if (token.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token"), isDirect = false)
        }
    }

    suspend fun listModels(settings: AppSettings): List<ModelInfo> {
        val ep = endpoint(settings)
        val out = mutableListOf<ModelInfo>()
        var afterId: String? = null
        repeat(5) {
            val url = "${ep.baseUrl}/v1/models?limit=100" + (afterId?.let { "&after_id=$it" } ?: "")
            val req = Request.Builder().url(url).get().apply {
                ep.headers.forEach { (k, v) -> header(k, v) }
                header("anthropic-version", API_VERSION)
            }.build()
            http.newCall(req).await().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw mapHttpError(resp.code, body, null)
                val obj = AppJson.parseToJsonElement(body).jsonObject
                obj["data"]?.jsonArray?.forEach { el ->
                    val m = el.jsonObject
                    out += ModelInfo(
                        id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach,
                        displayName = m["display_name"]?.jsonPrimitive?.contentOrNull ?: "",
                        maxInputTokens = m["max_input_tokens"]?.jsonPrimitive?.longOrNull,
                        maxOutputTokens = m["max_tokens"]?.jsonPrimitive?.longOrNull,
                    )
                }
                val hasMore = obj["has_more"]?.jsonPrimitive?.contentOrNull == "true"
                afterId = obj["last_id"]?.jsonPrimitive?.contentOrNull
                if (!hasMore || afterId == null) return out
            }
        }
        return out
    }

    suspend fun send(settings: AppSettings, request: ClaudeRequest): ClaudeResponse {
        val ep = endpoint(settings)
        var useSchema = request.jsonSchema != null && settings.strictJsonSchema
        var useFallback = settings.refusalFallback && ep.isDirect && request.model.contains("opus")
        var attempt = 0
        while (true) {
            attempt++
            val body = buildBody(request, useSchema, useFallback)
            val httpReq = Request.Builder().url("${ep.baseUrl}/v1/messages")
                .post(body.toString().toRequestBody(JSON))
                .apply {
                    ep.headers.forEach { (k, v) -> header(k, v) }
                    header("anthropic-version", API_VERSION)
                    header("content-type", "application/json")
                    if (useFallback) header("anthropic-beta", FALLBACK_BETA)
                }.build()
            val started = System.currentTimeMillis()
            val (code, text, retryAfter) = try {
                http.newCall(httpReq).await().use { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("retry-after")?.toLongOrNull()) }
            } catch (e: java.net.SocketTimeoutException) {
                if (attempt <= MAX_RETRIES) { delay(backoff(attempt)); continue }
                throw AppException(ErrorKind.AiTimeout, cause = e)
            } catch (e: java.io.IOException) {
                if (attempt <= MAX_RETRIES) { delay(backoff(attempt)); continue }
                throw AppException(ErrorKind.NetworkError, cause = e)
            }
            AppLog.d(TAG, "messages model=${request.model} status=$code in ${System.currentTimeMillis() - started}ms")
            if (code in 200..299) return parse(text, request.model)

            val message = errorMessage(text)
            if (code == 400) {
                // Degrade optional features instead of failing the user's job.
                val lower = message.lowercase()
                if (useFallback && ("fallback" in lower || "anthropic-beta" in lower)) { useFallback = false; continue }
                if (useSchema && ("output_config" in lower || "format" in lower || "schema" in lower)) { useSchema = false; continue }
            }
            if ((code == 429 || code == 500 || code == 502 || code == 503 || code == 504 || code == 529) && attempt <= MAX_RETRIES) {
                delay(((retryAfter ?: 0) * 1000).coerceAtLeast(backoff(attempt)).coerceAtMost(30_000))
                continue
            }
            throw mapHttpError(code, text, request.model)
        }
    }

    private fun backoff(attempt: Int): Long = (1500L shl (attempt - 1)).coerceAtMost(12_000)

    private fun buildBody(r: ClaudeRequest, useSchema: Boolean, useFallback: Boolean): JsonObject = buildJsonObject {
        put("model", r.model)
        put("max_tokens", r.maxTokens)
        // Stable system prompt first so prompt caching can reuse it across agents.
        put("system", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", r.system)
                put("cache_control", buildJsonObject { put("type", "ephemeral") })
            })
        })
        put("messages", buildJsonArray {
            r.turns.forEach { t -> add(buildJsonObject { put("role", t.role); put("content", t.text) }) }
        })
        val outputConfig = buildJsonObject {
            r.effort?.let { put("effort", it) }
            if (useSchema && r.jsonSchema != null) {
                put("format", buildJsonObject {
                    put("type", "json_schema")
                    put("schema", r.jsonSchema)
                })
            }
        }
        if (outputConfig.isNotEmpty()) put("output_config", outputConfig)
        if (useFallback) put("fallbacks", "default")
    }

    private fun parse(text: String, requestedModel: String): ClaudeResponse {
        val obj = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw AppException(ErrorKind.AiParseFailure, "응답 형식 오류")
        val stop = obj["stop_reason"]?.jsonPrimitive?.contentOrNull
        if (stop == "refusal") throw AppException(ErrorKind.AiRefused)
        val content = obj["content"] as? JsonArray ?: JsonArray(emptyList())
        val out = StringBuilder()
        var fellBack = false
        content.forEach { el ->
            val block = el.jsonObject
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> out.append(block["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                "fallback" -> fellBack = true
            }
        }
        val usage = obj["usage"]?.jsonObject
        val inTok = usage?.get("input_tokens")?.jsonPrimitive?.longOrNull ?: 0
        val outTok = usage?.get("output_tokens")?.jsonPrimitive?.longOrNull ?: 0
        totalInputTokens.addAndGet(inTok)
        totalOutputTokens.addAndGet(outTok)
        val result = out.toString()
        if (result.isBlank()) throw AppException(ErrorKind.AiParseFailure, "빈 응답을 받았습니다.")
        return ClaudeResponse(
            text = result,
            model = obj["model"]?.jsonPrimitive?.contentOrNull ?: requestedModel,
            stopReason = stop,
            inputTokens = inTok,
            outputTokens = outTok,
            fellBack = fellBack,
        )
    }

    private fun errorMessage(body: String): String = runCatching {
        AppJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: body.take(200)

    private fun mapHttpError(code: Int, body: String, model: String?): AppException {
        val msg = errorMessage(body)
        return when (code) {
            401, 403 -> AppException(ErrorKind.AiUnauthorized)
            404 -> AppException(ErrorKind.AiUnavailable, model?.let { "모델: $it" })
            413 -> AppException(ErrorKind.ContentTooLong)
            429 -> AppException(ErrorKind.AiRateLimited)
            400 -> if (msg.contains("too long", true) || msg.contains("context", true)) AppException(ErrorKind.ContentTooLong)
            else AppException(ErrorKind.AiUnavailable, "요청 오류: ${msg.take(160)}")
            402 -> AppException(ErrorKind.AiUnavailable, "결제/크레딧 상태를 확인해 주세요.")
            500, 502, 503, 504, 529 -> AppException(ErrorKind.AiTimeout, "Claude 서버가 일시적으로 혼잡합니다.")
            else -> AppException(ErrorKind.Unknown, "HTTP $code")
        }
    }

    companion object {
        const val TAG = "Claude"
        const val DIRECT_BASE = "https://api.anthropic.com"
        const val API_VERSION = "2023-06-01"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val MAX_RETRIES = 2
        private val JSON = "application/json".toMediaType()
    }
}
