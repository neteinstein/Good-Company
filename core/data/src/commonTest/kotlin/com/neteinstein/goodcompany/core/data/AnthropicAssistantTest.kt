package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.ai.AnthropicAssistant
import com.neteinstein.goodcompany.core.data.ai.AnthropicClient
import com.neteinstein.goodcompany.core.domain.model.AiFailure
import com.neteinstein.goodcompany.core.domain.repository.FriendProfile
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnthropicAssistantTest {
    private val ana = FriendProfile("ana", "Ana Silva", listOf("jazz"), listOf("crowds"), emptyList(), emptyList())
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun reply(text: String, stop: String = "end_turn") =
        """{"content":[{"type":"text","text":${JsonPrimitive(text)}}],"stop_reason":"$stop"}"""

    @Test
    fun birthdayMessageUsesStructuredOutputAndLanguage() = runTest {
        var body = ""
        val engine = MockEngine { req ->
            body = (req.body as TextContent).text
            respond(reply("""{"message":"Parabéns, Ana! 🎷"}"""), HttpStatusCode.OK, json)
        }
        val msg = AnthropicAssistant(AnthropicClient(HttpClient(engine))).birthdayMessage("k", ana, "pt-PT").getOrThrow()
        assertEquals("Parabéns, Ana! 🎷", msg)
        val sent = Json.parseToJsonElement(body).jsonObject
        val format = sent["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals("low", sent["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertTrue("pt-PT" in sent["system"]!!.jsonPrimitive.content)
        assertTrue("tools" !in sent)
    }

    @Test
    fun giftIdeasForAKid() = runTest {
        var body = ""
        val engine = MockEngine { req ->
            body = (req.body as TextContent).text
            respond(reply("""{"ideas":["LEGO castle"," ","Dino book"]}"""), HttpStatusCode.OK, json)
        }
        val ideas = AnthropicAssistant(AnthropicClient(HttpClient(engine))).giftIdeas("k", ana, "Leo", listOf("dinosaurs"), "en").getOrThrow()
        assertEquals(listOf("LEGO castle", "Dino book"), ideas)
        assertTrue("Leo, the child of Ana Silva (likes dinosaurs)" in body)
    }

    @Test
    fun failuresAreTyped() = runTest {
        val refused = MockEngine { respond(reply("", stop = "refusal"), HttpStatusCode.OK, json) }
        val r = AnthropicAssistant(AnthropicClient(HttpClient(refused))).birthdayMessage("k", ana, "en").exceptionOrNull() as AiFailure
        assertEquals(AiFailure.Reason.REFUSED, r.reason)
        val limited = MockEngine { respond("{}", HttpStatusCode.TooManyRequests, json) }
        val l = AnthropicAssistant(AnthropicClient(HttpClient(limited))).giftIdeas("k", ana, null, emptyList(), "en").exceptionOrNull() as AiFailure
        assertEquals(AiFailure.Reason.RATE_LIMITED, l.reason)
    }
}
