package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.cloud.GoogleDriveBackupStore
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class GoogleDriveBackupStoreTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()

    private fun store(handler: (HttpRequestData) -> Pair<HttpStatusCode, String>) = GoogleDriveBackupStore(
        HttpClient(
            MockEngine { req ->
                requests += req
                val (status, body) = handler(req)
                respond(body, status, json)
            },
        ),
    )

    @Test
    fun firstBackupCreatesTheFileWithAMultipartUpload() = runTest {
        val s = store { req ->
            when (req.method) {
                HttpMethod.Get -> HttpStatusCode.OK to """{"files":[]}"""
                else -> HttpStatusCode.OK to """{"id":"f1","modifiedTime":"2026-11-07T10:00:00.000Z"}"""
            }
        }
        val info = s.save("tok", "Good Company backup.json", """{"friends":[]}""")
        assertEquals("f1", info.id)
        assertEquals(Instant.parse("2026-11-07T10:00:00Z"), info.modifiedAt)
        val (list, upload) = requests
        assertEquals("Bearer tok", list.headers[HttpHeaders.Authorization])
        assertTrue(list.url.parameters["q"]!!.contains("name = 'Good Company backup.json'"))
        assertEquals(HttpMethod.Post, upload.method)
        assertEquals("multipart", upload.url.parameters["uploadType"])
        val body = (upload.body as TextContent).text
        assertTrue(body.contains("\"name\":\"Good Company backup.json\""))
        assertTrue(body.contains("""{"friends":[]}"""))
        assertTrue(upload.body.contentType.toString().startsWith("multipart/related"))
    }

    @Test
    fun laterBackupsReplaceTheSameFile() = runTest {
        val s = store { req ->
            when (req.method) {
                HttpMethod.Get -> HttpStatusCode.OK to """{"files":[{"id":"f1","modifiedTime":"2026-11-01T10:00:00Z"}]}"""
                else -> HttpStatusCode.OK to """{"id":"f1","modifiedTime":"2026-11-07T10:00:00Z"}"""
            }
        }
        s.save("tok", "b.json", "{}")
        val upload = requests.last()
        assertEquals(HttpMethod.Patch, upload.method)
        assertTrue(upload.url.encodedPath.endsWith("/upload/drive/v3/files/f1"))
        assertEquals("media", upload.url.parameters["uploadType"])
    }

    @Test
    fun latestAndDownload() = runTest {
        val s = store { req ->
            if (req.url.parameters["alt"] ==
                "media"
            ) {
                HttpStatusCode.OK to """{"format":"good-company"}"""
            } else {
                HttpStatusCode.OK to """{"files":[]}"""
            }
        }
        assertNull(s.latest("tok", "b.json"))
        assertEquals("""{"format":"good-company"}""", s.download("tok", "f1"))
    }

    @Test
    fun errorsMapToReasons() = runTest {
        val expired = store { HttpStatusCode.Unauthorized to """{"error":{"code":401}}""" }
        assertEquals(CloudBackupFailure.Reason.UNAUTHORIZED, assertFailsWith<CloudBackupFailure> { expired.latest("t", "b") }.reason)
        val full = store { HttpStatusCode.Forbidden to """{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""" }
        assertEquals(CloudBackupFailure.Reason.STORAGE_FULL, assertFailsWith<CloudBackupFailure> { full.latest("t", "b") }.reason)
        val off = store { HttpStatusCode.Forbidden to """{"error":{"message":"Drive API has not been used"}}""" }
        assertEquals(CloudBackupFailure.Reason.FORBIDDEN, assertFailsWith<CloudBackupFailure> { off.download("t", "f") }.reason)
        val offline = GoogleDriveBackupStore(HttpClient(MockEngine { error("no network") }))
        assertEquals(CloudBackupFailure.Reason.NETWORK, assertFailsWith<CloudBackupFailure> { offline.latest("t", "b") }.reason)
    }
}
