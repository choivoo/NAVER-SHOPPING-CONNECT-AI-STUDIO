package com.shoppingconnect.aistudio.ai.claude

import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.json.JsonRepair
import com.shoppingconnect.aistudio.data.settings.AppSettings
import kotlinx.serialization.json.JsonObject

data class AiCallMeta(val model: String, val inputTokens: Long, val outputTokens: Long, val repaired: Boolean, val retried: Boolean)

/**
 * Structured-output caller shared by every agent:
 *   model candidates (404 → next) → response → JSON extract → 1st: local repair → validation
 *   → 2nd: one retry that feeds the validation errors back to the model → else AiParseFailure.
 */
class AiGateway(
    private val client: ClaudeClient,
    private val resolver: ModelResolver,
) {
    var lastMeta: AiCallMeta? = null
        private set

    suspend fun text(settings: AppSettings, weight: TaskWeight, system: String, user: String, maxTokens: Int = 4000): String {
        val plan = resolver.plan(settings, weight)
        return withCandidates(plan) { model ->
            client.send(settings, ClaudeRequest(model, system, listOf(ChatTurn("user", user)), maxTokens, plan.effort)).also {
                lastMeta = AiCallMeta(it.model, it.inputTokens, it.outputTokens, false, false)
            }.text.trim()
        }
    }

    suspend fun json(
        settings: AppSettings,
        weight: TaskWeight,
        system: String,
        user: String,
        schema: JsonObject?,
        maxTokens: Int = settings.maxOutputTokens,
        validate: (JsonObject) -> List<String>,
    ): JsonObject {
        val plan = resolver.plan(settings, weight)
        return withCandidates(plan) { model ->
            val first = client.send(settings, ClaudeRequest(model, system, listOf(ChatTurn("user", user)), maxTokens, plan.effort, schema))
            var obj = JsonRepair.parseObject(first.text)
            var errors = if (obj == null) listOf("응답이 JSON 객체가 아닙니다") else validate(obj)
            if (errors.isEmpty() && obj != null) {
                lastMeta = AiCallMeta(first.model, first.inputTokens, first.outputTokens, repaired = false, retried = false)
                return@withCandidates obj
            }
            AppLog.w("AiGateway", "structured output invalid on first try: ${errors.take(3)}")
            val retry = client.send(
                settings,
                ClaudeRequest(
                    model, system,
                    listOf(
                        ChatTurn("user", user),
                        ChatTurn("assistant", first.text.take(12000)),
                        ChatTurn("user", "위 응답이 요구된 JSON 스키마를 만족하지 않습니다. 문제: ${errors.joinToString("; ")}\n설명 없이 수정된 JSON 객체 하나만 출력하세요."),
                    ),
                    maxTokens, plan.effort, schema,
                ),
            )
            obj = JsonRepair.parseObject(retry.text)
            errors = if (obj == null) listOf("JSON 아님") else validate(obj)
            if (errors.isNotEmpty() || obj == null) throw AppException(ErrorKind.AiParseFailure, errors.take(2).joinToString(", "))
            lastMeta = AiCallMeta(retry.model, first.inputTokens + retry.inputTokens, first.outputTokens + retry.outputTokens, repaired = true, retried = true)
            obj
        }
    }

    private suspend fun <T> withCandidates(plan: ModelPlan, block: suspend (String) -> T): T {
        var last: AppException? = null
        for (model in plan.candidates) {
            try {
                return block(model)
            } catch (e: AppException) {
                if (e.kind == ErrorKind.AiUnavailable && e.detail?.startsWith("모델") == true) {
                    AppLog.w("AiGateway", "model $model unavailable, trying next candidate")
                    last = e
                    continue
                }
                throw e
            }
        }
        throw last ?: AppException(ErrorKind.AiUnavailable)
    }
}
