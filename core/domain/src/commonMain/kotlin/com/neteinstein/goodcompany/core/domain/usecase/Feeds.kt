package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FeedItem
import com.neteinstein.goodcompany.core.domain.model.FriendFeed
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.FeedFetcher
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A parsed RSS 2.0 or Atom feed. */
data class ParsedFeed(val title: String, val siteUrl: String?, val items: List<FeedItem>)

/**
 * Tiny, dependency-free RSS/Atom reader: titles, links, ids and dates only. It's for "Ana posted something new",
 * not a full feed reader, so it ignores content, enclosures and namespaces it doesn't need.
 */
object FeedParser {
    const val MAX_ITEMS = 5

    /** Null when [xml] isn't an RSS or Atom document. */
    fun parse(xml: String): ParsedFeed? {
        val doc = xml.trimStart()
        return when {
            Regex("<rss[\\s>]|<rdf:RDF[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(doc) -> parseRss(doc)
            Regex("<feed[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(doc) -> parseAtom(doc)
            else -> null
        }
    }

    /** Feed URLs advertised by an HTML page (`<link rel="alternate" type="application/rss+xml" href="…">`), absolute. */
    fun discover(html: String, pageUrl: String): List<String> = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
        .map { it.value }
        .filter { tag ->
            val type = attr(tag, "type")?.lowercase().orEmpty()
            attr(tag, "rel")?.lowercase()?.split(' ')?.contains("alternate") == true &&
                (type.contains("rss") || type.contains("atom"))
        }
        .mapNotNull { attr(it, "href") }
        .map { resolve(pageUrl, decode(it)) }
        .distinct()
        .toList()

    /** "ana.blog/" → "https://ana.blog/". */
    fun normalizeUrl(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty() || url.contains(' ')) return null
        val withScheme = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(url)) url else "https://$url"
        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
        return withScheme.takeIf { host.contains('.') }
    }

    /** Places blogs usually keep their feed, tried when a page doesn't advertise one. */
    fun commonFeedUrls(pageUrl: String): List<String> {
        val root = pageUrl.substringBefore("://") + "://" + pageUrl.substringAfter("://").substringBefore('/')
        val base = pageUrl.trimEnd('/')
        return (listOf("$base/feed", "$base/rss") + listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml").map { root + it })
            .distinct()
    }

    private fun parseRss(doc: String): ParsedFeed {
        val channel = block(doc, "channel") ?: doc
        val head = channel.substringBefore("<item")
        val items = blocks(doc, "item").map { item ->
            val link = text(item, "link")
            val title = text(item, "title") ?: text(item, "description")?.take(120) ?: link.orEmpty()
            FeedItem(
                id = text(item, "guid") ?: link ?: title,
                title = title,
                link = link,
                publishedAt = (text(item, "pubDate") ?: text(item, "dc:date"))?.let { parseDate(it) },
            )
        }
        return ParsedFeed(text(head, "title").orEmpty(), text(head, "link"), newest(items))
    }

    private fun parseAtom(doc: String): ParsedFeed {
        val head = doc.substringBefore("<entry")
        val items = blocks(doc, "entry").map { entry ->
            val link = atomLink(entry)
            val title = text(entry, "title") ?: link.orEmpty()
            FeedItem(
                id = text(entry, "id") ?: link ?: title,
                title = title,
                link = link,
                publishedAt = (text(entry, "published") ?: text(entry, "updated"))?.let { parseDate(it) },
            )
        }
        return ParsedFeed(text(head, "title").orEmpty(), atomLink(head), newest(items))
    }

    private fun newest(items: List<FeedItem>) = items
        .filter { it.title.isNotBlank() }
        .sortedByDescending { it.publishedAt ?: Instant.DISTANT_PAST }
        .take(MAX_ITEMS)

    private fun atomLink(xml: String): String? {
        val links = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }.toList()
        val alt = links.firstOrNull { (attr(it, "rel") ?: "alternate") == "alternate" } ?: links.firstOrNull()
        return alt?.let { attr(it, "href") }?.let(::decode)
    }

    private fun blocks(xml: String, tag: String): List<String> =
        Regex("<$tag(\\s[^>]*)?>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(xml).map {
            it.groupValues[2]
        }.toList()

    private fun block(xml: String, tag: String): String? = blocks(xml, tag).firstOrNull()

    private fun text(xml: String, tag: String): String? = block(xml, tag)?.let(::clean)?.takeIf { it.isNotEmpty() }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(2)

    private fun clean(raw: String): String {
        val noCdata = raw.replace(Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
        val decoded = decode(noCdata)
        // Titles can carry HTML after decoding (e.g. "&lt;b&gt;").
        return decoded.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
    }

    private fun decode(s: String): String = s
        .replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            code?.takeIf { it in 1..0x10FFFF }?.let { codePointToString(it) } ?: m.value
        }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&nbsp;", " ").replace("&amp;", "&")

    private fun codePointToString(cp: Int): String = if (cp < 0x10000) {
        cp.toChar().toString()
    } else {
        val v = cp - 0x10000
        charArrayOf(((v shr 10) + 0xD800).toChar(), ((v and 0x3FF) + 0xDC00).toChar()).concatToString()
    }

    private fun resolve(base: String, href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> base.substringBefore("://") + ":" + href
        href.startsWith("/") -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + href
        else -> base.substringBeforeLast('/') + "/" + href
    }

    private val US_ZONES = mapOf("EST" to -5, "EDT" to -4, "CST" to -6, "CDT" to -5, "MST" to -7, "MDT" to -6, "PST" to -8, "PDT" to -7)

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** RFC 822 ("Sat, 07 Nov 2026 10:00:00 +0000" / "GMT") or ISO 8601 ("2026-11-07T10:00:00Z"). Null if neither. */
    fun parseDate(raw: String): Instant? {
        val s = raw.trim()
        runCatching { return Instant.parse(s) }
        Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(s)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching { LocalDateTime(y.toInt(), mo.toInt(), d.toInt(), 0, 0).toInstant(TimeZone.UTC) }.getOrNull()
        }
        val m =
            Regex("(\\d{1,2})\\s+([A-Za-z]{3})[A-Za-z]*\\s+(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*([+-]\\d{4}|[A-Za-z]{1,4})?").find(s)
                ?: return null
        val (day, mon, yearRaw, hh, mm, ss, zone) = m.destructured
        val month = MONTHS.indexOf(mon.lowercase()) + 1
        if (month == 0) return null
        val year = yearRaw.toInt().let { if (it < 100) 2000 + it else it }
        val offset = when {
            zone.startsWith("+") || zone.startsWith("-") -> {
                val sign = if (zone[0] == '-') -1 else 1
                UtcOffset(hours = sign * zone.substring(1, 3).toInt(), minutes = sign * zone.substring(3, 5).toInt())
            }
            else -> UtcOffset(hours = US_ZONES[zone.uppercase()] ?: 0)
        }
        return runCatching {
            LocalDateTime(year, month, day.toInt(), hh.toInt(), mm.toInt(), ss.ifEmpty { "0" }.toInt()).toInstant(offset)
        }.getOrNull()
    }
}

/** Why a feed couldn't be added. */
class FeedNotFoundException(url: String) : Exception("No feed at $url")

/** Follows a friend's blog: finds the feed behind [rawUrl] (the feed itself, an advertised one, or a usual path) and stores it. */
class AddFeedUseCase(private val friends: FriendRepository, private val fetcher: FeedFetcher, private val clock: AppClock) {
    suspend operator fun invoke(id: FriendId, rawUrl: String): Result<FriendFeed> = runCatching {
        val url = FeedParser.normalizeUrl(rawUrl) ?: throw FeedNotFoundException(rawUrl)
        friends.get(id) ?: throw NoSuchElementException("friend")
        val (feedUrl, parsed) = locate(url) ?: throw FeedNotFoundException(url)
        // Re-read after the network calls so edits made meanwhile aren't lost.
        val friend = friends.get(id) ?: throw NoSuchElementException("friend")
        val feed = FriendFeed(
            url = feedUrl,
            title = parsed.title.ifBlank { feedUrl.substringAfter("://").substringBefore('/') },
            siteUrl = parsed.siteUrl,
            checkedAt = clock.now(),
            items = parsed.items,
        )
        friends.upsert(friend.copy(feeds = friend.feeds.filterNot { it.url == feedUrl } + feed))
        feed
    }

    private suspend fun locate(url: String): Pair<String, ParsedFeed>? {
        val body = runCatching { fetcher.fetch(url) }.getOrNull()
        body?.let { FeedParser.parse(it) }?.let { return url to it }
        val candidates = body?.let { FeedParser.discover(it, url) }.orEmpty() + FeedParser.commonFeedUrls(url)
        for (candidate in candidates.distinct()) {
            val parsed = runCatching { FeedParser.parse(fetcher.fetch(candidate)) }.getOrNull() ?: continue
            return candidate to parsed
        }
        return null
    }
}

/**
 * Re-reads every followed feed that wasn't checked in the last [MIN_INTERVAL_HOURS] hours (on app start).
 * Network failures are skipped silently; returns how many new posts arrived.
 */
class RefreshFeedsUseCase(private val friends: FriendRepository, private val fetcher: FeedFetcher, private val clock: AppClock) {
    suspend operator fun invoke(force: Boolean = false): Int {
        val now = clock.now()
        var fresh = 0
        friends.friends.first().filter { it.feeds.isNotEmpty() }.forEach { friend ->
            val updated = friend.feeds.mapNotNull { feed ->
                val checked = feed.checkedAt
                val due = force || checked == null || now - checked >= MIN_INTERVAL_HOURS.hours
                if (!due) return@mapNotNull null
                val parsed = runCatching { FeedParser.parse(fetcher.fetch(feed.url)) }.getOrNull() ?: return@mapNotNull null
                val known = feed.items.map { it.id }.toSet()
                fresh += parsed.items.count { it.id !in known }
                feed.copy(
                    title = feed.title.ifBlank { parsed.title },
                    checkedAt = now,
                    items = (parsed.items + feed.items).distinctBy { it.id }
                        .sortedByDescending { it.publishedAt ?: Instant.DISTANT_PAST }
                        .take(FeedParser.MAX_ITEMS),
                )
            }.associateBy { it.url }
            if (updated.isEmpty()) return@forEach
            // Re-read so edits made while fetching aren't lost; a feed removed meanwhile stays removed.
            val current = friends.get(friend.id) ?: return@forEach
            friends.upsert(current.copy(feeds = current.feeds.map { updated[it.url] ?: it }))
        }
        return fresh
    }

    companion object {
        const val MIN_INTERVAL_HOURS = 20
    }
}
