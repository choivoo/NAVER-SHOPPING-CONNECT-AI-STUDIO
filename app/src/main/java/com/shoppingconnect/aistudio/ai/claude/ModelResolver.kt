package com.shoppingconnect.aistudio.ai.claude

import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.data.settings.AiQuality
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.ModelChoice
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How demanding an agent task is — drives model tier and effort under the cost policy. */
enum class TaskWeight { HEAVY, LIGHT }

data class ModelPlan(val candidates: List<String>, val effort: String?)

/**
 * Chooses the model per task without hard-coding a single name:
 *  1. Discovers the models the key can actually use via GET /v1/models (cached).
 *  2. Auto Best → Claude Opus 5.5 when available, else the newest Opus, else the newest Sonnet.
 *  3. Falls back through a candidate list when a model returns 404 (not available to this org).
 */
class ModelResolver(private val client: ClaudeClient) {
    private val mutex = Mutex()
    private var cached: List<String>? = null
    private var cachedAt = 0L

    data class Parsed(val family: String, val major: Int, val minor: Int, val id: String)

    suspend fun available(settings: AppSettings, force: Boolean = false): List<String> = mutex.withLock {
        val now = System.currentTimeMillis()
        cached?.takeIf { !force && now - cachedAt < CACHE_MS }?.let { return it }
        val ids = runCatching { client.listModels(settings).map { it.id } }
            .onFailure { AppLog.w("ModelResolver", "model discovery unavailable, using known-good fallbacks", it) }
            .getOrNull()
            .orEmpty()
        if (ids.isNotEmpty()) { cached = ids; cachedAt = now }
        ids
    }

    suspend fun plan(settings: AppSettings, weight: TaskWeight): ModelPlan {
        val available = available(settings)
        val opus = bestOf(available, "opus")
        val sonnet = bestOf(available, "sonnet")
        val opusChain = (listOfNotNull(opus) + FALLBACK_OPUS).distinct()
        val sonnetChain = (listOfNotNull(sonnet) + FALLBACK_SONNET).distinct()

        val candidates = when (settings.modelChoice) {
            ModelChoice.CUSTOM -> listOf(settings.customModel.trim()).filter { it.isNotEmpty() } + opusChain
            ModelChoice.SONNET -> sonnetChain
            ModelChoice.OPUS -> opusChain + sonnetChain
            ModelChoice.AUTO_BEST -> when (settings.aiQuality) {
                AiQuality.BEST -> opusChain + sonnetChain
                AiQuality.BALANCED -> if (weight == TaskWeight.HEAVY) opusChain + sonnetChain else sonnetChain + opusChain
                AiQuality.ECONOMY -> sonnetChain + opusChain
            }
        }.distinct()

        val effort = settings.effortOverride.takeIf { settings.expertMode && it.isNotBlank() } ?: when {
            weight == TaskWeight.LIGHT -> "low"
            settings.aiQuality == AiQuality.BEST -> "high"
            else -> "medium"
        }
        return ModelPlan(candidates, effort)
    }

    companion object {
        private const val CACHE_MS = 6 * 60 * 60 * 1000L
        /** Known IDs used only when discovery is unavailable (e.g. behind a proxy without /v1/models). */
        val FALLBACK_OPUS = listOf("claude-opus-5-5", "claude-opus-5")
        val FALLBACK_SONNET = listOf("claude-sonnet-5")

        private val idRegex = Regex("^claude-(opus|sonnet|haiku)-(\\d+)(?:-(\\d{1,2}))?(?:-\\d{8})?$")

        fun parse(id: String): Parsed? {
            val m = idRegex.matchEntire(id) ?: return null
            return Parsed(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0, id)
        }

        /** Newest model of a family; undated aliases win over dated snapshots of the same version. */
        fun bestOf(ids: List<String>, family: String): String? = ids.mapNotNull { parse(it) }
            .filter { it.family == family }
            .sortedWith(compareByDescending<Parsed> { it.major }.thenByDescending { it.minor }.thenBy { it.id.length })
            .firstOrNull()?.id
    }
}
