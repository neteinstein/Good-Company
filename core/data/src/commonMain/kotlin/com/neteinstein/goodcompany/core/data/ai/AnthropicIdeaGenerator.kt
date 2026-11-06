package com.neteinstein.goodcompany.core.data.ai

import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.repository.IdeaGenerator
import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * [IdeaGenerator] backed by the Claude Messages API, called over raw HTTP with Ktor.
 *
 * Raw HTTP because the official Kotlin/Java SDK is JVM-only and this code must run on
 * iOS (Kotlin/Native) and the browser (Kotlin/Wasm) too. Uses the user's own API key.
 *
 * - Server-side `web_search` grounds suggestions in current, real events/news.
 * - `fallbacks: "default"` lets the API re-run a safety-declined request on its recommended fallback model.
 * - `pause_turn` (server tool loop limit) is resumed by re-sending the assistant turn.
 */
class AnthropicIdeaGenerator(
    private val http: HttpClient,
    private val baseUrl: String = "https://api.anthropic.com",
    private val model: String = DEFAULT_MODEL,
) : IdeaGenerator {
    override suspend fun generate(request: IdeaRequest): Result<List<GeneratedIdea>> = runCatching {
        val messages = mutableListOf<JsonObject>(
            buildJsonObject {
                put("role", "user")
                put("content", IdeaPrompts.user(request))
            },
        )
        repeat(MAX_CONTINUATIONS + 1) {
            val response = send(request.apiKey, messages)
            when (val stop = response["stop_reason"]?.jsonPrimitive?.content) {
                "pause_turn" -> {
                    messages += buildJsonObject {
                        put("role", "assistant")
                        put("content", response["content"] ?: JsonArray(emptyList()))
                    }
                }
                "refusal" -> throw AiException("The request was declined by the model's safety system.")
                else -> {
                    val text = response["content"]?.jsonArray.orEmpty()
                        .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                        .joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
                    if (stop == "max_tokens" && text.isBlank()) throw AiException("The response was cut off. Try again.")
                    return@runCatching IdeaJsonParser.parse(text)
                }
            }
        }
        throw AiException("The search took too long. Try again later.")
    }

    private suspend fun send(apiKey: String, messages: List<JsonObject>): JsonObject {
        val body = buildJsonObject {
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
        val response = http.post("$baseUrl/v1/messages") {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", API_VERSION)
            header("anthropic-beta", FALLBACK_BETA)
            // Required for the wasmJs build, which calls the API straight from the browser (bring-your-own key).
            header("anthropic-dangerous-direct-browser-access", "true")
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw AiException(errorMessage(response.status.value, text))
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun errorMessage(status: Int, body: String): String {
        val apiMessage = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return when (status) {
            401 -> "Your API key was rejected. Check it in Settings."
            429 -> "Rate limited by the AI service. Try again in a minute."
            in 500..599 -> "The AI service is having trouble ($status). Try again later."
            else -> apiMessage ?: "AI request failed ($status)."
        }
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        const val API_VERSION = "2026-10-21"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val MAX_TOKENS = 16_000
        const val MAX_SEARCHES = 5
        const val MAX_CONTINUATIONS = 3
    }
}

class AiException(message: String) : Exception(message)
