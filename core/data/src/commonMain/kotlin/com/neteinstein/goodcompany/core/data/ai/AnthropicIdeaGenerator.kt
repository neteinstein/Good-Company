package com.neteinstein.goodcompany.core.data.ai

import com.neteinstein.goodcompany.core.domain.model.AiFailure
import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.repository.IdeaGenerator
import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * [IdeaGenerator] backed by the Claude Messages API with the server-side `web_search` tool, so suggestions are
 * grounded in current, real events/news. `fallbacks: "default"` re-runs a safety-declined request on the
 * recommended fallback model; `pause_turn` (server tool loop limit) is resumed by re-sending the assistant turn.
 */
class AnthropicIdeaGenerator(
    private val client: AnthropicClient,
    private val model: String = AnthropicClient.DEFAULT_MODEL,
) : IdeaGenerator {
    override suspend fun generate(request: IdeaRequest): Result<List<GeneratedIdea>> = runCatching {
        val messages = mutableListOf<JsonObject>(
            buildJsonObject {
                put("role", "user")
                put("content", IdeaPrompts.user(request))
            },
        )
        repeat(MAX_CONTINUATIONS + 1) {
            val response = client.send(request.apiKey, body(messages))
            when (val stop = AnthropicClient.stopReason(response)) {
                "pause_turn" -> messages += buildJsonObject {
                    put("role", "assistant")
                    put("content", AnthropicClient.content(response))
                }
                "refusal" -> throw AiFailure(AiFailure.Reason.REFUSED)
                else -> {
                    val text = AnthropicClient.text(response)
                    if (stop == "max_tokens" && text.isBlank()) throw AiFailure(AiFailure.Reason.TRUNCATED)
                    return@runCatching IdeaJsonParser.parse(text)
                }
            }
        }
        throw AiFailure(AiFailure.Reason.TOO_SLOW)
    }

    private fun body(messages: List<JsonObject>) = buildJsonObject {
        put("model", model)
        put("max_tokens", MAX_TOKENS)
        put("system", IdeaPrompts.system)
        put("fallbacks", "default")
        putJsonObject("output_config") { put("effort", "medium") }
        put(
            "tools",
            buildJsonArray {
                addJsonObject {
                    put("type", "web_search_20260209")
                    put("name", "web_search")
                    put("max_uses", MAX_SEARCHES)
                }
            },
        )
        put("messages", JsonArray(messages))
    }

    companion object {
        const val DEFAULT_MODEL = AnthropicClient.DEFAULT_MODEL
        const val API_VERSION = AnthropicClient.API_VERSION
        const val FALLBACK_BETA = AnthropicClient.FALLBACK_BETA
        const val MAX_TOKENS = 16_000
        const val MAX_SEARCHES = 5
        const val MAX_CONTINUATIONS = 3
    }
}
