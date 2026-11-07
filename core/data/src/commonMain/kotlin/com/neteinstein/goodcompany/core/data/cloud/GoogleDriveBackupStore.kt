package com.neteinstein.goodcompany.core.data.cloud

import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupInfo
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupStore
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Google Drive v3 over plain HTTPS (Ktor), authorised with the user's OAuth access token for the `drive.file`
 * scope. Keeps one visible JSON file in My Drive; Drive only lets this app see files it created itself.
 */
class GoogleDriveBackupStore(
    private val http: HttpClient,
    private val baseUrl: String = "https://www.googleapis.com",
) : CloudBackupStore {
    override suspend fun latest(accessToken: String, fileName: String): CloudBackupInfo? {
        val response = call {
            http.get("$baseUrl/drive/v3/files") {
                bearerAuth(accessToken)
                parameter("q", "name = '${fileName.replace("'", "\\'")}' and trashed = false")
                parameter("orderBy", "modifiedTime desc")
                parameter("pageSize", 1)
                parameter("spaces", "drive")
                parameter("fields", "files(id,modifiedTime)")
            }
        }
        val files = parse(response)["files"]?.jsonArray.orEmpty()
        return files.firstOrNull()?.jsonObject?.let(::info)
    }

    override suspend fun save(accessToken: String, fileName: String, content: String): CloudBackupInfo {
        val existing = latest(accessToken, fileName)
        val response = if (existing != null) {
            call {
                http.patch("$baseUrl/upload/drive/v3/files/${existing.id}") {
                    bearerAuth(accessToken)
                    parameter("uploadType", "media")
                    parameter("fields", FIELDS)
                    setBody(TextContent(content, JSON))
                }
            }
        } else {
            call {
                http.post("$baseUrl/upload/drive/v3/files") {
                    bearerAuth(accessToken)
                    parameter("uploadType", "multipart")
                    parameter("fields", FIELDS)
                    multipartRelated(fileName, content)
                }
            }
        }
        return info(parse(response))
    }

    override suspend fun download(accessToken: String, id: String): String {
        val response = call {
            http.get("$baseUrl/drive/v3/files/$id") {
                bearerAuth(accessToken)
                parameter("alt", "media")
            }
        }
        check(response)
        return response.bodyAsText()
    }

    /** Metadata + content in one `multipart/related` request, as Drive's "multipart upload" expects. */
    private fun HttpRequestBuilder.multipartRelated(fileName: String, content: String) {
        val metadata = buildJsonObject {
            put("name", fileName)
            put("mimeType", "application/json")
        }
        val body = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString()).append("\r\n")
            append("--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content).append("\r\n")
            append("--$BOUNDARY--\r\n")
        }
        setBody(TextContent(body, ContentType("multipart", "related").withParameter("boundary", BOUNDARY)))
    }

    private suspend fun call(block: suspend () -> HttpResponse): HttpResponse = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw CloudBackupFailure(CloudBackupFailure.Reason.NETWORK, e.message)
    }

    private suspend fun parse(response: HttpResponse): JsonObject {
        check(response)
        return runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }
            .getOrElse { throw CloudBackupFailure(CloudBackupFailure.Reason.OTHER, "Unexpected Drive response") }
    }

    private suspend fun check(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        throw CloudBackupFailure(failureFor(response.status, text), "Drive ${response.status.value}")
    }

    private fun info(file: JsonObject) = CloudBackupInfo(
        id = file["id"]?.jsonPrimitive?.contentOrNull ?: throw CloudBackupFailure(CloudBackupFailure.Reason.OTHER, "No file id"),
        modifiedAt = file["modifiedTime"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )

    companion object {
        private const val FIELDS = "id,modifiedTime"
        private const val BOUNDARY = "good-company-backup-boundary"
        private val JSON = ContentType.Application.Json.withCharset(Charsets.UTF_8)

        internal fun failureFor(status: HttpStatusCode, body: String): CloudBackupFailure.Reason = when {
            status == HttpStatusCode.Unauthorized -> CloudBackupFailure.Reason.UNAUTHORIZED
            status == HttpStatusCode.Forbidden && body.contains("storageQuotaExceeded") -> CloudBackupFailure.Reason.STORAGE_FULL
            status == HttpStatusCode.Forbidden -> CloudBackupFailure.Reason.FORBIDDEN
            else -> CloudBackupFailure.Reason.OTHER
        }
    }
}
