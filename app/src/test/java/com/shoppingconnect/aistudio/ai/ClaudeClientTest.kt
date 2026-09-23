package com.shoppingconnect.aistudio.ai

import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.ai.claude.AiGateway
import com.shoppingconnect.aistudio.ai.claude.ChatTurn
import com.shoppingconnect.aistudio.ai.claude.ClaudeClient
import com.shoppingconnect.aistudio.ai.claude.ClaudeRequest
import com.shoppingconnect.aistudio.ai.claude.ModelResolver
import com.shoppingconnect.aistudio.ai.claude.TaskWeight
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.security.InMemorySecretStore
import com.shoppingconnect.aistudio.core.security.SecretKeyName
import com.shoppingconnect.aistudio.data.settings.AiQuality
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.ModelChoice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ClaudeClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ClaudeClient
    private val secrets = InMemorySecretStore().apply { put(SecretKeyName.CLAUDE_API_KEY, "sk-ant-test-key") }
    private val settings = AppSettings(refusalFallback = true)

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        val http = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()
        client = ClaudeClient(http, secrets, server.url("/").toString().trimEnd('/'), backoffBaseMs = 5)
    }

    @After fun tearDown() = server.shutdown()

    private fun ok(text: String, model: String = "claude-opus-5-5") = MockResponse().setResponseCode(200).setBody(
        """{"id":"msg_1","type":"message","role":"assistant","model":"$model","content":[{"type":"text","text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(text))}}],"stop_reason":"end_turn","usage":{"input_tokens":12,"output_tokens":34}}""",
    )

    private fun err(code: Int, type: String, msg: String) = MockResponse().setResponseCode(code).setBody("""{"type":"error","error":{"type":"$type","message":"$msg"}}""")

    private fun req(model: String = "claude-opus-5-5") = ClaudeRequest(model, "system", listOf(ChatTurn("user", "hi")), 1000, "medium")

    @Test fun sendsHeadersAndParsesText() = runTest {
        server.enqueue(ok("안녕하세요"))
        val r = client.send(settings, req())
        assertThat(r.text).isEqualTo("안녕하세요")
        assertThat(r.inputTokens).isEqualTo(12)
        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("x-api-key")).isEqualTo("sk-ant-test-key")
        assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01")
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("claude-opus-5-5")
        assertThat(body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content).isEqualTo("medium")
        assertThat(body["fallbacks"]!!.jsonPrimitive.content).isEqualTo("default")
        assertThat(recorded.getHeader("anthropic-beta")).isEqualTo(ClaudeClient.FALLBACK_BETA)
    }

    @Test fun retriesRateLimitThenSucceeds() = runTest {
        server.enqueue(err(429, "rate_limit_error", "slow down").addHeader("retry-after", "0"))
        server.enqueue(err(529, "overloaded_error", "busy"))
        server.enqueue(ok("done"))
        assertThat(client.send(settings, req()).text).isEqualTo("done")
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test fun rateLimitExhaustedMapsToAiRateLimited() = runTest {
        repeat(3) { server.enqueue(err(429, "rate_limit_error", "slow")) }
        expect(ErrorKind.AiRateLimited) { client.send(settings, req()) }
    }

    @Test fun unauthorizedMapsToAiUnauthorized() = runTest {
        server.enqueue(err(401, "authentication_error", "invalid x-api-key"))
        expect(ErrorKind.AiUnauthorized) { client.send(settings, req()) }
    }

    @Test fun modelNotFoundMapsToUnavailable() = runTest {
        server.enqueue(err(404, "not_found_error", "model: claude-x"))
        expect(ErrorKind.AiUnavailable) { client.send(settings, req()) }
    }

    @Test fun refusalIsReported() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[],"stop_reason":"refusal","usage":{"input_tokens":1,"output_tokens":0}}"""))
        expect(ErrorKind.AiRefused) { client.send(settings, req()) }
    }

    @Test fun emptyResponseIsParseFailure() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":""}],"stop_reason":"end_turn"}"""))
        expect(ErrorKind.AiParseFailure) { client.send(settings, req()) }
    }

    @Test fun degradesOptionalFeaturesOn400() = runTest {
        server.enqueue(err(400, "invalid_request_error", "Unexpected value(s) for the `anthropic-beta` header"))
        server.enqueue(ok("fine"))
        assertThat(client.send(settings, req()).text).isEqualTo("fine")
        server.takeRequest()
        val second = server.takeRequest()
        assertThat(second.getHeader("anthropic-beta")).isNull()
        assertThat(second.body.readUtf8()).doesNotContain("fallbacks")
    }

    @Test fun contentTooLong() = runTest {
        server.enqueue(err(413, "request_too_large", "too large"))
        expect(ErrorKind.ContentTooLong) { client.send(settings, req()) }
    }

    @Test fun timeoutAfterRetries() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) }
        expect(ErrorKind.AiTimeout) { client.send(settings, req()) }
    }

    @Test fun notConfiguredWithoutKey() = runTest {
        val c = ClaudeClient(OkHttpClient(), InMemorySecretStore(), server.url("/").toString())
        assertThat(c.isConfigured(AppSettings())).isFalse()
        expect(ErrorKind.AiNotConfigured) { c.send(AppSettings(), req()) }
    }

    @Test fun modelDiscoveryAutoBestPrefersOpus55() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"claude-sonnet-5","display_name":"Claude Sonnet 5"},{"id":"claude-opus-5","display_name":"Claude Opus 5"},{"id":"claude-opus-5-5","display_name":"Claude Opus 5.5"},{"id":"claude-haiku-4-5-20251001"}],"has_more":false}"""))
        val resolver = ModelResolver(client)
        val heavy = resolver.plan(settings.copy(aiQuality = AiQuality.BALANCED), TaskWeight.HEAVY)
        val light = resolver.plan(settings.copy(aiQuality = AiQuality.BALANCED), TaskWeight.LIGHT)
        assertThat(heavy.candidates.first()).isEqualTo("claude-opus-5-5")
        assertThat(light.candidates.first()).isEqualTo("claude-sonnet-5")
        assertThat(resolver.plan(settings.copy(aiQuality = AiQuality.ECONOMY), TaskWeight.HEAVY).candidates.first()).isEqualTo("claude-sonnet-5")
        assertThat(resolver.plan(settings.copy(modelChoice = ModelChoice.CUSTOM, customModel = "claude-opus-4-8"), TaskWeight.LIGHT).candidates.first()).isEqualTo("claude-opus-4-8")
    }

    @Test fun discoveryFailureUsesKnownFallbacks() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        val plan = ModelResolver(client).plan(settings.copy(aiQuality = AiQuality.BEST), TaskWeight.HEAVY)
        assertThat(plan.candidates).containsAtLeastElementsIn(ModelResolver.FALLBACK_OPUS + ModelResolver.FALLBACK_SONNET)
    }

    @Test fun bestOfIgnoresDatedSnapshotsAndHaiku() {
        assertThat(ModelResolver.bestOf(listOf("claude-opus-4-5-20251101", "claude-opus-4-8", "claude-haiku-4-5"), "opus")).isEqualTo("claude-opus-4-8")
        assertThat(ModelResolver.bestOf(listOf("claude-haiku-4-5"), "opus")).isNull()
    }

    // ---- AiGateway: structured output repair / retry / model fallback -----------------------

    private fun gateway(): AiGateway {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"claude-opus-5-5"},{"id":"claude-opus-5"},{"id":"claude-sonnet-5"}],"has_more":false}"""))
        return AiGateway(client, ModelResolver(client))
    }

    @Test fun gatewayRepairsFencedJson() = runTest {
        val g = gateway()
        server.enqueue(ok("```json\n{\"titles\": [\"a\", \"b\",],}\n```"))
        val o = g.json(settings, TaskWeight.LIGHT, "s", "u", null) { emptyList() }
        assertThat(o.containsKey("titles")).isTrue()
    }

    @Test fun gatewayRetriesWithValidationFeedback() = runTest {
        val g = gateway()
        server.enqueue(ok("{\"titles\": []}"))
        server.enqueue(ok("{\"titles\": [\"x\"]}"))
        val o = g.json(settings, TaskWeight.LIGHT, "s", "u", null) { if ((it["titles"] as kotlinx.serialization.json.JsonArray).isEmpty()) listOf("titles 비어 있음") else emptyList() }
        assertThat(o.toString()).contains("x")
        server.takeRequest() // models
        server.takeRequest()
        val retry = server.takeRequest().body.readUtf8()
        assertThat(retry).contains("titles 비어 있음")
        assertThat(retry).contains("\"assistant\"")
        assertThat(g.lastMeta!!.retried).isTrue()
    }

    @Test fun gatewayFailsAfterSecondInvalidResponse() = runTest {
        val g = gateway()
        server.enqueue(ok("not json"))
        server.enqueue(ok("still not json"))
        expect(ErrorKind.AiParseFailure) { g.json(settings, TaskWeight.LIGHT, "s", "u", null) { emptyList() } }
    }

    @Test fun gatewayFallsBackToNextModelOn404() = runTest {
        val g = gateway()
        server.enqueue(err(404, "not_found_error", "model not found"))
        server.enqueue(ok("{\"ok\": true}", model = "claude-opus-5"))
        val o = g.json(settings.copy(aiQuality = AiQuality.BEST), TaskWeight.HEAVY, "s", "u", null) { emptyList() }
        assertThat(o.containsKey("ok")).isTrue()
        server.takeRequest()
        assertThat(server.takeRequest().body.readUtf8()).contains("claude-opus-5-5")
        assertThat(server.takeRequest().body.readUtf8()).contains("\"claude-opus-5\"")
    }

    private suspend fun expect(kind: ErrorKind, block: suspend () -> Unit) {
        try { block(); fail("expected $kind") } catch (e: AppException) { assertThat(e.kind).isEqualTo(kind) }
    }
}
