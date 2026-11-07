package com.neteinstein.goodcompany.core.data.ai

import com.neteinstein.goodcompany.core.domain.model.AiFailure
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thin raw-HTTP client for the Claude Messages API (the official Kotlin/Java SDK is JVM-only, and this code also
 * runs on iOS and in the browser). Adds the API version, the refusal-fallback beta and the browser-access header.
 */
class AnthropicClient(private val http: HttpClient, private val baseUrl: String = "https://api.anthropic.com") {
    suspend fun send(apiKey: String, body: JsonObject): JsonObject {
        val response = runCatching {
            http.post("$baseUrl/v1/messages") {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", API_VERSION)
                header("anthropic-beta", FALLBACK_BETA)
                // Required for the wasmJs build, which calls the API straight from the browser (bring-your-own key).
                header("anthropic-dangerous-direct-browser-access", "true")
                setBody(body.toString())
            }
        }.getOrElse { throw AiFailure(AiFailure.Reason.OTHER, it.message) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw failureFor(response.status.value, text)
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun failureFor(status: Int, body: String): AiFailure {
        val apiMessage = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return when (status) {
            401, 403 -> AiFailure(AiFailure.Reason.INVALID_KEY, apiMessage)
            429 -> AiFailure(AiFailure.Reason.RATE_LIMITED, apiMessage)
            in 500..599 -> AiFailure(AiFailure.Reason.SERVICE_UNAVAILABLE, apiMessage)
            else -> AiFailure(AiFailure.Reason.OTHER, apiMessage ?: "HTTP $status")
        }
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        const val API_VERSION = "2026-10-21"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        fun stopReason(response: JsonObject): String? = response["stop_reason"]?.jsonPrimitive?.content

        fun text(response: JsonObject): String = response["content"]?.jsonArray.orEmpty()
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            .joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }

        fun content(response: JsonObject): JsonArray = response["content"]?.jsonArray ?: JsonArray(emptyList())
    }
}
