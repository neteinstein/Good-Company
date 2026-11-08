package com.neteinstein.goodcompany.core.data.update

import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.repository.AppUpdateSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads the rolling `test-build` pre-release that CI replaces on every merge to main (see CI_CD.md). The release is
 * titled `Test build #<run>` and the run number is the build's `versionCode`; its sideload APK has a stable name.
 * No credentials are sent and nothing about the user is: it is a plain public GET.
 */
class GitHubReleaseUpdateSource(
    private val http: HttpClient,
    private val repository: String = DEFAULT_REPOSITORY,
    private val tag: String = DEFAULT_TAG,
    private val assetName: String = DEFAULT_ASSET,
) : AppUpdateSource {
    override suspend fun latest(): AppUpdate? {
        val response = http.get("https://api.github.com/repos/$repository/releases/tags/$tag") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return parseRelease(response.bodyAsText(), assetName)
    }

    companion object {
        const val DEFAULT_REPOSITORY = "neteinstein/Good-Company"
        const val DEFAULT_TAG = "test-build"
        const val DEFAULT_ASSET = "good-company-sideload-test.apk"

        private val buildNumber = Regex("""#(\d+)""")
        private val versionInNotes = Regex("""version `([^`]+)`""")

        /** Null when the release has no [assetName] or its title carries no build number. */
        internal fun parseRelease(body: String, assetName: String): AppUpdate? {
            val release = Json.parseToJsonElement(body).jsonObject
            val code = release.string("name")?.let { buildNumber.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: return null
            val asset = (release["assets"] as? JsonArray ?: JsonArray(emptyList()))
                .map { it.jsonObject }
                .firstOrNull { it.string("name") == assetName } ?: return null
            val url = asset.string("browser_download_url") ?: return null
            return AppUpdate(
                versionCode = code,
                versionName = release.string("body")?.let { versionInNotes.find(it)?.groupValues?.get(1) } ?: "#$code",
                apkUrl = url,
                sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull,
            )
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
