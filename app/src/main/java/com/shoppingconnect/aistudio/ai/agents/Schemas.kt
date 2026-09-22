package com.shoppingconnect.aistudio.ai.agents

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Minimal JSON-Schema builder compatible with Claude structured outputs (every object closed, all props required). */
object Schema {
    fun str(): JsonObject = buildJsonObject { put("type", "string") }
    fun int(): JsonObject = buildJsonObject { put("type", "integer") }
    fun num(): JsonObject = buildJsonObject { put("type", "number") }
    fun strArr(): JsonObject = arr(str())
    fun arr(items: JsonObject): JsonObject = buildJsonObject { put("type", "array"); put("items", items) }
    fun enumOf(values: List<String>): JsonObject = buildJsonObject {
        put("type", "string")
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }
    inline fun <reified E : Enum<E>> enumOf(): JsonObject = enumOf(enumValues<E>().map { it.name })

    fun obj(vararg props: Pair<String, JsonObject>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
        put("required", buildJsonArray { props.forEach { add(JsonPrimitive(it.first)) } })
        put("additionalProperties", false)
    }
}
