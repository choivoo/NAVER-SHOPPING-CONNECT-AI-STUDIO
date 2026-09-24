package com.shoppingconnect.aistudio.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shoppingconnect.aistudio.ai.claude.ChatTurn
import com.shoppingconnect.aistudio.ai.claude.ClaudeClient
import com.shoppingconnect.aistudio.ai.claude.ClaudeRequest
import com.shoppingconnect.aistudio.ai.claude.ModelResolver
import com.shoppingconnect.aistudio.ai.claude.TaskWeight
import com.shoppingconnect.aistudio.core.network.HttpClients
import com.shoppingconnect.aistudio.core.network.SafeFetcher
import com.shoppingconnect.aistudio.core.security.InMemorySecretStore
import com.shoppingconnect.aistudio.core.security.SecretKeyName
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.product.ProductExtractionService
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live network checks — run ONLY when the tester passes the inputs:
 *   -e claudeKey <key>   real Claude API (connection, model discovery, one tiny request)
 *   -e productUrl <url>  real product / Shopping Connect link extraction
 * The key is kept in memory only (InMemorySecretStore) and never written to the QA report or logs.
 */
@RunWith(AndroidJUnit4::class)
class DeviceNetworkTest {
    @Test fun claudeLive() = runBlocking {
        val key = QaLog.arg("claudeKey")
        if (key == null) { QaLog.row("Claude API", "NOT TESTED", "no API key passed (-e claudeKey)"); assumeTrue(false); return@runBlocking }
        val secrets = InMemorySecretStore().apply { put(SecretKeyName.CLAUDE_API_KEY, key) }
        val client = ClaudeClient(HttpClients.api(), secrets)
        val s = AppSettings()
        val resolver = ModelResolver(client)
        val models = resolver.available(s, force = true)
        val plan = resolver.plan(s, TaskWeight.HEAVY)
        QaLog.row("Claude model discovery", if (models.isNotEmpty()) "PASS" else "FAIL", "${models.size} models; Auto Best heavy → ${plan.candidates.first()} (effort ${plan.effort}); listed opus: ${models.filter { "opus" in it }.joinToString()}")
        val t0 = System.currentTimeMillis()
        val r = client.send(s, ClaudeRequest(plan.candidates.first(), "Reply with JSON only.", listOf(ChatTurn("user", "{\"ok\":true} 를 그대로 출력하세요.")), 200, "low"))
        QaLog.row("Claude request", "PASS", "served by model=${r.model}, ${System.currentTimeMillis() - t0}ms, tokens ${r.inputTokens}/${r.outputTokens}, text=${r.text.take(40)}")
    }

    @Test fun productUrlLive() = runBlocking {
        val url = QaLog.arg("productUrl")
        if (url == null) { QaLog.row("Real product URL", "NOT TESTED", "no URL passed (-e productUrl)"); assumeTrue(false); return@runBlocking }
        val svc = ProductExtractionService(SafeFetcher(HttpClients.fetch()))
        val r = runCatching { svc.extract(url) }
        r.onSuccess { p ->
            QaLog.row("Real product URL", "PASS", "source=${p.source} title=${p.title.take(40)} price=${p.price} brand=${p.brand} seller=${p.seller} images=${p.images.size} redirects=${p.redirectChain.size} unknown=${p.uncertainFields.joinToString()}")
        }.onFailure { e ->
            QaLog.row("Real product URL", "BLOCKED→FALLBACK", "extraction failed: ${e.message?.take(120)} — app offers manual input / search API")
        }
    }
}
