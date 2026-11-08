package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.FeedItem
import com.neteinstein.goodcompany.core.domain.model.FriendFeed
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpStoryBuilder
import com.neteinstein.goodcompany.core.domain.usecase.FeedNotFoundException
import com.neteinstein.goodcompany.core.domain.usecase.FeedParser
import com.neteinstein.goodcompany.core.domain.usecase.FreshItem
import com.neteinstein.goodcompany.core.domain.usecase.MissingApiKeyException
import com.neteinstein.goodcompany.core.domain.usecase.ShareMatcher
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ShareAndFeedsTest {
    private val ana = friend("ana", "Ana Silva")
    private val rui = friend("rui", "Rui Costa") { copy(isCloseFriend = true) }
    private val bea = friend("bea", "Beatriz Rocha")

    // --- Sharing ---------------------------------------------------------------------------------------------------

    @Test
    fun inboxIgnoresEmptySharesAndClears() {
        val g = TestGraph()
        g.shareInbox.offer(SharedContent(text = "  "))
        assertNull(g.shareInbox.pending.value)
        g.shareInbox.offer(SharedContent(url = "https://instagram.com/p/1"))
        assertEquals("https://instagram.com/p/1", g.shareInbox.pending.value?.url)
        g.shareInbox.clear()
        assertNull(g.shareInbox.pending.value)
    }

    @Test
    fun draftPrefersTheFirstLineOfTextThenTheLink() {
        assertEquals("Got the job!", SharedContent(text = "\n Got the job! \nmore", url = "https://x.y").draft())
        assertEquals("https://x.y", SharedContent(text = "https://x.y", url = "https://x.y").draft())
        assertEquals("", SharedContent(image = SharedImage(byteArrayOf(1), "image/jpeg")).draft())
        assertEquals(10, SharedContent(text = "a".repeat(50)).draft(maxLength = 10).length)
        assertTrue(SharedContent().isEmpty)
        assertEquals(SharedImage(byteArrayOf(1, 2), "image/png"), SharedImage(byteArrayOf(1, 2), "image/png"))
    }

    @Test
    fun friendsNamedInTheShareComeFirst() {
        val content = SharedContent(text = "Look what Ana posted!", url = "https://instagram.com/beatrizrocha/p/1")
        assertEquals(listOf("ana", "bea", "rui"), ShareMatcher.rank(listOf(rui, bea, ana), content).map { it.id.value })
        assertEquals(setOf(ana.id, bea.id), ShareMatcher.mentions(listOf(rui, bea, ana), content))
        // Nobody named: real friends first, then A–Z.
        assertEquals(listOf("rui", "ana", "bea"), ShareMatcher.rank(listOf(bea, rui, ana), SharedContent(text = "hello")).map { it.id.value })
        assertTrue(ShareMatcher.mentions(listOf(ana), SharedContent(image = SharedImage(byteArrayOf(1), "image/png"))).isEmpty())
    }

    @Test
    fun savesALifeUpdate() = runTest {
        val g = TestGraph(listOf(ana))
        assertTrue(g.addLifeUpdate(ana.id, "  New job at Studio X "))
        assertFalse(g.addLifeUpdate(ana.id, " "))
        assertFalse(g.addLifeUpdate(FriendId("nobody"), "x"))
        val update = g.friendRepo.friends.value.single().lifeUpdates.single()
        assertEquals("New job at Studio X", update.text)
        assertEquals(g.clock.now(), update.addedAt)
    }

    @Test
    fun aiSuggestionNeedsAKeyAndSendsTextAndImage() = runTest {
        val g = TestGraph(listOf(ana))
        val content = SharedContent(text = "Big news!", url = "https://lnkd.in/x", image = SharedImage(byteArrayOf(1), "image/jpeg"))
        assertIs<MissingApiKeyException>(g.suggestUpdateFromShared(ana.id, content).exceptionOrNull())

        g.settingsRepo.update { AppSettings(aiApiKey = "k") }
        assertEquals("Started a new job", g.suggestUpdateFromShared(ana.id, content).getOrThrow())
        assertEquals("update:ana:Big news!\nhttps://lnkd.in/x:image/jpeg:en", g.assistant.calls.last())

        g.assistant.lifeUpdate = Result.success("  ")
        assertNull(g.suggestUpdateFromShared(ana.id, content).getOrThrow())
        assertTrue(g.suggestUpdateFromShared(FriendId("nobody"), content).isFailure)
    }

    // --- Feeds -----------------------------------------------------------------------------------------------------

    private val rss = """
        <?xml version="1.0"?>
        <rss version="2.0"><channel>
          <title>Ana &amp; the Mountains</title><link>https://ana.blog/</link>
          <item><title><![CDATA[Climbing in <b>Sintra</b>]]></title><link>https://ana.blog/sintra</link>
            <guid>p2</guid><pubDate>Sun, 08 Mar 2026 09:30:00 +0100</pubDate></item>
          <item><title>First post &#8212; hi</title><link>https://ana.blog/hello</link><pubDate>Mon, 2 Feb 26 10:00 GMT</pubDate></item>
          <item><description>no title here</description><guid>p0</guid></item>
        </channel></rss>
    """.trimIndent()

    private val atom = """
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Rui on films</title>
          <link rel="self" href="https://rui.example/atom.xml"/><link href="https://rui.example/"/>
          <entry><title>Past Lives</title><link rel="alternate" href="https://rui.example/past-lives"/><id>tag:1</id>
            <updated>2026-03-09T20:00:00Z</updated></entry>
        </feed>
    """.trimIndent()

    @Test
    fun parsesRssAndAtom() {
        val r = assertNotNull(FeedParser.parse(rss))
        assertEquals("Ana & the Mountains", r.title)
        assertEquals("https://ana.blog/", r.siteUrl)
        assertEquals(listOf("Climbing in Sintra", "First post — hi", "no title here"), r.items.map { it.title })
        assertEquals(at(2026, 3, 8, 8, 30), r.items[0].publishedAt)
        assertEquals("p2", r.items[0].id)
        assertEquals("https://ana.blog/hello", r.items[1].id)
        assertEquals(at(2026, 2, 2, 10), r.items[1].publishedAt)

        val a = assertNotNull(FeedParser.parse(atom))
        assertEquals("Rui on films", a.title)
        assertEquals("https://rui.example/", a.siteUrl)
        assertEquals(FeedItem("tag:1", "Past Lives", "https://rui.example/past-lives", at(2026, 3, 9, 20)), a.items.single())

        assertNull(FeedParser.parse("<html><body>hi</body></html>"))
    }

    @Test
    fun parsesDatesAndUrls() {
        assertEquals(at(2026, 3, 1, 0), FeedParser.parseDate("2026-03-01"))
        assertEquals(at(2026, 3, 1, 17), FeedParser.parseDate("Sun, 01 Mar 2026 12:00:00 EST"))
        assertNull(FeedParser.parseDate("someday"))
        assertEquals("https://ana.blog/", FeedParser.normalizeUrl(" ana.blog/ "))
        assertEquals("http://x.io", FeedParser.normalizeUrl("http://x.io"))
        assertNull(FeedParser.normalizeUrl("not a url"))
        assertNull(FeedParser.normalizeUrl("localhost"))
        val html = """<head><link rel="alternate" type="application/rss+xml" href="/feed.xml"><link rel="stylesheet" href="a.css">
            <link type='application/atom+xml' rel='alternate' href='https://cdn.x/atom'></head>"""
        assertEquals(listOf("https://ana.blog/feed.xml", "https://cdn.x/atom"), FeedParser.discover(html, "https://ana.blog/about"))
        assertTrue("https://ana.blog/feed" in FeedParser.commonFeedUrls("https://ana.blog/"))
    }

    @Test
    fun addsAFeedFromAPageAndRefreshesIt() = runTest {
        val g = TestGraph(listOf(ana))
        g.feedFetcher.pages["https://ana.blog"] = """<link rel="alternate" type="application/rss+xml" href="/rss">"""
        g.feedFetcher.pages["https://ana.blog/rss"] = rss
        val feed = g.addFeed(ana.id, "ana.blog").getOrThrow()
        assertEquals("https://ana.blog/rss", feed.url)
        assertEquals("Ana & the Mountains", feed.title)
        assertEquals(g.clock.now(), feed.checkedAt)
        assertEquals(3, g.friendRepo.friends.value.single().feeds.single().items.size)

        // Fresh posts lead the catch-up story.
        val fresh = CatchUpStoryBuilder.freshItems(g.friendRepo.friends.value.single())
        assertEquals(FreshItem.NewPost("Climbing in Sintra", "Ana & the Mountains", "https://ana.blog/sintra", at(2026, 3, 8, 8, 30)), fresh.first())

        // Not due yet → no request.
        g.feedFetcher.requests.clear()
        assertEquals(0, g.refreshFeeds())
        assertTrue(g.feedFetcher.requests.isEmpty())

        g.clock.advanceBy(21.hours)
        g.feedFetcher.pages["https://ana.blog/rss"] =
            rss.replace("<item><title><![CDATA", "<item><title>Brand new</title><guid>p3</guid></item><item><title><![CDATA")
        assertEquals(1, g.refreshFeeds())
        val items = g.friendRepo.friends.value.single().feeds.single().items
        assertEquals(4, items.size)
        assertTrue(items.any { it.title == "Brand new" })
        assertEquals("Climbing in Sintra", items.first().title)

        // Failures are skipped quietly.
        g.feedFetcher.pages.clear()
        assertEquals(0, g.refreshFeeds(force = true))
    }

    @Test
    fun addingFailsWithoutAFeed() = runTest {
        val g = TestGraph(listOf(ana))
        g.feedFetcher.pages["https://nothing.io"] = "<html></html>"
        assertIs<FeedNotFoundException>(g.addFeed(ana.id, "nothing.io").exceptionOrNull())
        assertIs<FeedNotFoundException>(g.addFeed(ana.id, "bad url").exceptionOrNull())
        assertTrue(g.addFeed(FriendId("nobody"), "x.io").isFailure)
        // Falls back to the usual paths.
        g.feedFetcher.pages["https://nothing.io/feed"] = atom
        assertEquals("https://nothing.io/feed", g.addFeed(ana.id, "nothing.io").getOrThrow().url)
    }

    @Test
    fun refreshesAFeedThatWasNeverChecked() = runTest {
        val g = TestGraph(listOf(friend("ana") { copy(feeds = listOf(FriendFeed("https://a/rss"))) }))
        g.feedFetcher.pages["https://a/rss"] = rss
        g.refreshFeeds()
        val feed = g.friendRepo.friends.value.single().feeds.single()
        assertEquals(3, feed.items.size)
        assertEquals("Ana & the Mountains", feed.title)
    }
}
