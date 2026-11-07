package com.neteinstein.goodcompany.core.data.ai

import com.neteinstein.goodcompany.core.domain.model.AiFailure
import com.neteinstein.goodcompany.core.domain.repository.AiAssistant
import com.neteinstein.goodcompany.core.domain.repository.FriendProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Short single-shot helpers (no tools) using structured outputs (`output_config.format` with a JSON schema),
 * which guarantees the first text block is valid JSON. Low effort keeps them fast and cheap.
 */
class AnthropicAssistant(
    private val client: AnthropicClient,
    private val model: String = AnthropicClient.DEFAULT_MODEL,
) : AiAssistant {
    override suspend fun birthdayMessage(apiKey: String, friend: FriendProfile, languageTag: String): Result<String> = runCatching {
        val json = ask(
            apiKey,
            system = "You write short, warm, natural birthday messages from a friend. No hashtags, at most one emoji, " +
                "2–3 sentences, sounds like a real person texting. Reference one thing they love if it fits naturally. " +
                "Write in the language with BCP-47 tag \"$languageTag\".",
            user = AssistantPrompts.profile(friend) + "\nWrite a birthday message for ${friend.name.substringBefore(' ')}.",
            schema = objectSchema("message" to stringSchema()),
        )
        json["message"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() } ?: throw AiFailure(AiFailure.Reason.OTHER, "empty")
    }

    override suspend fun giftIdeas(
        apiKey: String,
        friend: FriendProfile,
        kidName: String?,
        kidLikes: List<String>,
        languageTag: String,
    ): Result<List<String>> =
        runCatching {
            val who =
                kidName?.let {
                    "$it, the child of ${friend.name}" +
                        (kidLikes.takeIf { l -> l.isNotEmpty() }?.let { l -> " (likes ${l.joinToString()})" } ?: "")
                }
                    ?: friend.name
            val json = ask(
                apiKey,
                system = "You suggest thoughtful, specific, affordable gift ideas (each 2–6 words, no prices, no brands unless iconic). " +
                    "Respect dislikes. Write in the language with BCP-47 tag \"$languageTag\".",
                user = AssistantPrompts.profile(friend) + "\nSuggest 5 gift ideas for $who.",
                schema = objectSchema(
                    "ideas" to buildJsonObject {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                    },
                ),
            )
            json["ideas"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.trim().takeIf(String::isNotEmpty) }.orEmpty()
        }

    private suspend fun ask(apiKey: String, system: String, user: String, schema: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("system", system)
            put("fallbacks", "default")
            putJsonObject("output_config") {
                put("effort", "low")
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", schema)
                }
            }
            put(
                "messages",
                buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put("content", user)
                    }
                },
            )
        }
        val response = client.send(apiKey, body)
        when (AnthropicClient.stopReason(response)) {
            "refusal" -> throw AiFailure(AiFailure.Reason.REFUSED)
            "max_tokens" -> throw AiFailure(AiFailure.Reason.TRUNCATED)
        }
        return runCatching { Json.parseToJsonElement(AnthropicClient.text(response)).jsonObject }
            .getOrElse { throw AiFailure(AiFailure.Reason.OTHER, "invalid JSON") }
    }

    private fun stringSchema() = buildJsonObject { put("type", "string") }

    private fun objectSchema(vararg props: Pair<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        putJsonArray("required") { props.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.first)) } }
        put("additionalProperties", false)
    }

    private companion object {
        const val MAX_TOKENS = 4_000
    }
}

internal object AssistantPrompts {
    fun profile(f: FriendProfile): String = buildString {
        appendLine("Friend: ${f.name}")
        if (f.likes.isNotEmpty()) appendLine("Loves: ${f.likes.joinToString()}")
        if (f.dislikes.isNotEmpty()) appendLine("Dislikes: ${f.dislikes.joinToString()}")
        if (f.kids.isNotEmpty()) appendLine("Kids: ${f.kids.joinToString()}")
        if (f.recentMessages.isNotEmpty()) {
            appendLine("Recent chat (oldest first):")
            f.recentMessages.takeLast(10).forEach { appendLine("  $it") }
        }
    }
}
