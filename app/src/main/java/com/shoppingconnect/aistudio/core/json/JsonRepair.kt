package com.shoppingconnect.aistudio.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/**
 * First-line repair for model output that is *almost* JSON:
 * strips markdown fences and prose around the object, removes trailing commas,
 * normalises smart quotes used as delimiters, and closes unbalanced brackets.
 */
object JsonRepair {
    fun extractObject(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        s = s.replace(Regex("^```(?:json|JSON)?\\s*"), "").replace(Regex("\\s*```\\s*$"), "")
        val start = s.indexOf('{')
        if (start < 0) return null
        val end = findMatchingEnd(s, start)
        return if (end > start) s.substring(start, end + 1) else s.substring(start)
    }

    private fun findMatchingEnd(s: String, start: Int): Int {
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{', '[' -> depth++
                '}', ']' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    fun repair(raw: String): String? {
        var s = extractObject(raw) ?: return null
        s = s.replace(Regex(",\\s*([}\\]])"), "$1")
        s = closeBrackets(s)
        return s
    }

    private fun closeBrackets(s: String): String {
        val stack = ArrayDeque<Char>()
        var inStr = false
        var esc = false
        for (c in s) {
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{' -> stack.addLast('}')
                '[' -> stack.addLast(']')
                '}', ']' -> if (stack.isNotEmpty()) stack.removeLast()
            }
        }
        val sb = StringBuilder(s)
        if (inStr) sb.append('"')
        var trimmed = sb.toString().trimEnd()
        if (trimmed.endsWith(",")) trimmed = trimmed.dropLast(1)
        val out = StringBuilder(trimmed)
        while (stack.isNotEmpty()) out.append(stack.removeLast())
        return out.toString()
    }

    /** Returns a parsed object, trying strict parse then repair. */
    fun parseObject(raw: String): JsonObject? {
        val candidates = listOfNotNull(extractObject(raw), repair(raw))
        for (c in candidates) {
            val el = runCatching { AppJson.parseToJsonElement(c) }.getOrNull()
            if (el is JsonObject) return el
        }
        return null
    }
}

fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
