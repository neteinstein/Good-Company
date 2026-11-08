package com.neteinstein.goodcompany.core.data.feeds

import com.neteinstein.goodcompany.core.domain.repository.FeedFetcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

/**
 * Plain GET straight from the device (no server of ours). On the web build many sites don't send CORS headers, so
 * the browser refuses to hand over the body; that surfaces as a failure and the friend page says the feed couldn't
 * be read.
 */
class KtorFeedFetcher(private val http: HttpClient) : FeedFetcher {
    override suspend fun fetch(url: String): String {
        val response = http.get(url) {
            header(HttpHeaders.Accept, "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/html;q=0.8, */*;q=0.5")
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val body = response.bodyAsText()
        check(body.length <= MAX_BYTES) { "too large" }
        return body
    }

    private companion object {
        const val MAX_BYTES = 3_000_000
    }
}
