package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.ai.AnthropicClient
import com.neteinstein.goodcompany.core.data.ai.AnthropicIdeaGenerator
import com.neteinstein.goodcompany.core.data.ai.IdeaJsonParser
import com.neteinstein.goodcompany.core.data.ai.IdeaPrompts
import com.neteinstein.goodcompany.core.domain.model.AiFailure
import com.neteinstein.goodcompany.core.domain.repository.FriendProfile
import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnthropicIdeaGeneratorTest {
    private val request = IdeaRequest(
        apiKey = "sk-test",
        mode = IdeaRequest.Mode.WEEKLY_EVENTS,
        friends = listOf(FriendProfile("ana", "Ana", listOf("jazz"), listOf("crowds"), listOf("Leo (likes lego)"), listOf("Ana: jazz fest?"))),
        area = "Lisbon",
        todayIso = "2026-03-10",
    )

    private fun reply(stop: String, text: String) = """
        {"content":[{"type":"server_tool_use","id":"x","name":"web_search","input":{}},{"type":"text","text":${JsonPrimitive(text)}}],
         "stop_reason":"$stop"}
    """.trimIndent()

    @Test
    fun sendsExpectedRequestAndParsesIdeas() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { req ->
            assertEquals("sk-test", req.headers["x-api-key"])
            assertEquals(AnthropicIdeaGenerator.API_VERSION, req.headers["anthropic-version"])
            assertEquals(AnthropicIdeaGenerator.FALLBACK_BETA, req.headers["anthropic-beta"])
            bodies += (req.body as TextContent).text
            respond(
                reply(
                    "end_turn",
                    "Here you go:\n```json\n{\"ideas\":[{\"friendIds\":[\"ana\"],\"kind\":\"event\",\"title\":\"Jazz night\",\"description\":\"d\",\"url\":null,\"when\":\"Fri\"}]}\n```",
                ),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ideas = AnthropicIdeaGenerator(AnthropicClient(HttpClient(engine))).generate(request).getOrThrow()
        assertEquals("Jazz night", ideas.single().title)
        assertEquals("Fri", ideas.single().whenText)

        val body = Json.parseToJsonElement(bodies.single()).jsonObject
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("web_search_20260209", body["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue("temperature" !in body)
    }

    @Test
    fun resumesPauseTurnByResendingAssistantContent() = runTest {
        var calls = 0
        val engine = MockEngine { req ->
            calls++
            val body = Json.parseToJsonElement((req.body as TextContent).text).jsonObject
            val messages = body["messages"]!!.jsonArray
            if (calls == 1) {
                assertEquals(1, messages.size)
                respond(reply("pause_turn", "searching"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                assertEquals("assistant", messages.last().jsonObject["role"]!!.jsonPrimitive.content)
                respond(reply("end_turn", "{\"ideas\":[]}"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        assertTrue(AnthropicIdeaGenerator(AnthropicClient(HttpClient(engine))).generate(request).getOrThrow().isEmpty())
        assertEquals(2, calls)
    }

    @Test
    fun mapsErrorsToFriendlyMessages() = runTest {
        val engine = MockEngine { respond("""{"error":{"message":"invalid x-api-key"}}""", HttpStatusCode.Unauthorized) }
        val error = AnthropicIdeaGenerator(AnthropicClient(HttpClient(engine))).generate(request).exceptionOrNull()
        assertEquals(AiFailure.Reason.INVALID_KEY, (error as AiFailure).reason)
    }

    @Test
    fun refusalIsAFailure() = runTest {
        val engine = MockEngine { respond(reply("refusal", ""), HttpStatusCode.OK) }
        val refused = AnthropicIdeaGenerator(AnthropicClient(HttpClient(engine))).generate(request).exceptionOrNull()
        assertEquals(AiFailure.Reason.REFUSED, (refused as AiFailure).reason)
    }

    @Test
    fun parserIsTolerant() {
        assertEquals(1, IdeaJsonParser.parse("prose {\"ideas\":[{\"title\":\"A\"}]} trailing").size)
        assertEquals(1, IdeaJsonParser.parse("[{\"title\":\"A\",\"url\":\"null\"}]").size)
        assertTrue(IdeaJsonParser.parse("no json at all").isEmpty())
        assertTrue(IdeaJsonParser.parse("{\"ideas\":[{\"description\":\"no title\"}]}").isEmpty())
    }

    @Test
    fun promptIncludesProfileAndArea() {
        val prompt = IdeaPrompts.user(request)
        assertTrue("BCP-47 tag \"pt-PT\"" in IdeaPrompts.user(request.copy(languageTag = "pt-PT")))
        listOf("Lisbon", "id=ana", "likes: jazz", "dislikes: crowds", "Leo (likes lego)", "Ana: jazz fest?").forEach {
            assertTrue(it in prompt, "missing $it")
        }
    }
}
