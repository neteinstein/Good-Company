package com.neteinstein.goodcompany.core.data.ai

import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Extracts `{"ideas":[...]}` from the model's final text. Tolerates a ```json fence, leading prose,
 * and missing optional fields; returns an empty list rather than throwing on malformed output.
 */
internal object IdeaJsonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```")

    fun parse(text: String): List<GeneratedIdea> {
        val candidates = buildList {
            fence.findAll(text).toList().asReversed().forEach { add(it.groupValues[1]) }
            for ((open, close) in listOf('{' to '}', '[' to ']')) {
                val start = text.indexOf(open)
                val end = text.lastIndexOf(close)
                if (start in 0 until end) add(text.substring(start, end + 1))
            }
        }
        for (candidate in candidates) {
            val root = runCatching { json.parseToJsonElement(candidate.trim()) }.getOrNull() ?: continue
            val array = when (root) {
                is JsonObject -> root["ideas"] as? JsonArray
                is JsonArray -> root
                else -> null
            } ?: continue
            return array.mapNotNull { (it as? JsonObject)?.toIdea() }
        }
        return emptyList()
    }

    private fun JsonObject.toIdea(): GeneratedIdea? {
        val title = string("title") ?: return null
        return GeneratedIdea(
            friendIds = (this["friendIds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            kind = string("kind") ?: "",
            title = title,
            description = string("description") ?: "",
            url = string("url"),
            whenText = string("when"),
        )
    }

    private fun JsonObject.string(key: String): String? = when (val v: JsonElement? = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        else -> null
    }
}
