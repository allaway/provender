package com.provender.ai

import kotlinx.serialization.json.Json

/**
 * Lenient parsing of model output (SPEC §4): strip code fences, cut to the outermost JSON
 * array, parse with a forgiving Json config. Returns null rather than throwing — the caller
 * owns retry/failure policy.
 */
object LlmJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Extracts the outermost `[...]` from raw model output, tolerating fences and prose. */
    fun extractJsonArray(raw: String): String? {
        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    inline fun <reified T> parseArray(raw: String): List<T>? {
        val payload = extractJsonArray(raw) ?: return null
        return runCatching { json.decodeFromString<List<T>>(payload) }.getOrNull()
    }
}
