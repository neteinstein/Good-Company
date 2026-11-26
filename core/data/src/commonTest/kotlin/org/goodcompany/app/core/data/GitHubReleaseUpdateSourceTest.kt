package org.goodcompany.app.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.data.update.GitHubReleaseUpdateSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GitHubReleaseUpdateSourceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun source(status: HttpStatusCode, body: String) = GitHubReleaseUpdateSource(
        HttpClient(
            MockEngine { req ->
                requests += req
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )

    private fun release(name: String = "0.1.4 (build #42)", assets: String = sideloadAsset) = """
        {"tag_name":"v0.1.4","name":"$name","body":"Release **0.1.4** (build **#42**, version `0.1.4`) from `abc1234`",
         "assets":[$assets]}
    """.trimIndent()

    private val playAsset = """{"name":"good-company-play.apk","size":10,"browser_download_url":"https://example.com/play.apk"}"""
    private val sideloadAsset = """{"name":"good-company-sideload.apk","size":4096,"browser_download_url":"https://example.com/sideload.apk"}"""

    @Test
    fun readsTheSideloadApkAndBuildNumberFromTheRollingRelease() = runTest {
        val update = source(HttpStatusCode.OK, release(assets = "$playAsset,$sideloadAsset")).latest()!!
        assertEquals(42L, update.versionCode)
        assertEquals("0.1.4", update.versionName)
        assertEquals("https://example.com/sideload.apk", update.apkUrl)
        assertEquals(4096L, update.sizeBytes)
        val request = requests.single()
        assertEquals("https://api.github.com/repos/neteinstein/Good-Company/releases/latest", request.url.toString())
        assertEquals("application/vnd.github+json", request.headers[HttpHeaders.Accept])
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun fallsBackToTheBuildNumberWhenNotesHaveNoVersion() = runTest {
        val body = """{"name":"Test build #7","body":"hi","assets":[$sideloadAsset]}"""
        assertEquals("#7", source(HttpStatusCode.OK, body).latest()!!.versionName)
    }

    @Test
    fun noUpdateWhenTheReleaseIsMissingOrIncomplete() = runTest {
        assertNull(source(HttpStatusCode.NotFound, "{}").latest())
        assertNull(source(HttpStatusCode.OK, release(assets = playAsset)).latest())
        assertNull(source(HttpStatusCode.OK, release(name = "Some release")).latest())
        assertNull(source(HttpStatusCode.OK, """{"name":"0.1.0 (build #1)"}""").latest())
    }

    @Test
    fun serverErrorsSurfaceAsFailures() = runTest {
        assertFailsWith<IllegalStateException> { source(HttpStatusCode.Forbidden, "{}").latest() }
    }
}
