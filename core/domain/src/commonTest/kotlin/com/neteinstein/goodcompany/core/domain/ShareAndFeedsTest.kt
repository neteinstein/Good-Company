package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SaveShareRequest
import com.neteinstein.goodcompany.core.domain.usecase.ShareChoice
import com.neteinstein.goodcompany.core.domain.usecase.ShareGuess
import com.neteinstein.goodcompany.core.domain.usecase.ShareGuesser
import com.neteinstein.goodcompany.core.domain.usecase.ShareReason
import com.neteinstein.goodcompany.core.domain.usecase.ShareTarget
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.util.PhoneMatcher
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(listOf("ana", "bea", "rui"), ShareMatcher.rank(listOf(rui, bea, ana), content).map { it.friend.id.value })
        assertEquals(ShareReason.MENTIONED, ShareMatcher.rank(listOf(ana), content).single().reason)
        assertEquals(setOf(ana.id, bea.id), ShareMatcher.mentions(listOf(rui, bea, ana), content))
        // Nobody named: real friends first, then A–Z.
        assertEquals(
            listOf("rui", "ana", "bea"),
            ShareMatcher.rank(listOf(bea, rui, ana), SharedContent(text = "hello")).map { it.friend.id.value },
        )
        assertTrue(ShareMatcher.mentions(listOf(ana), SharedContent(image = SharedImage(byteArrayOf(1), "image/png"))).isEmpty())
        // The link's title counts too.
        assertEquals(ShareReason.MENTIONED, ShareMatcher.rank(listOf(bea), SharedContent(url = "https://x.y", title = "Beatriz Rocha's trail")).single().reason)
    }

    @Test
    fun aNumberInTheTextThenRecentPeopleRankNext() {
        val carla = friend("carla", "Carla Nunes") { copy(phoneNumbers = listOf("+351 912 345 678")) }
        val now = at(2026, 3, 10, 18)
        val last = mapOf(bea.id to at(2026, 3, 10, 9), ana.id to at(2026, 3, 1), rui.id to at(2026, 2, 1))
        val content = SharedContent(text = "Call me on 912-345-678 tomorrow")
        val ranked = ShareMatcher.rank(listOf(rui, ana, bea, carla), content, last, now)
        assertEquals(listOf("carla", "bea", "ana", "rui"), ranked.map { it.friend.id.value })
        assertEquals(listOf(ShareReason.NUMBER, ShareReason.SEEN_TODAY, ShareReason.RECENT, null), ranked.map { it.reason })
        // A number that is nobody's ranks nobody; short digit runs are not numbers.
        assertNull(ShareMatcher.rank(listOf(carla), SharedContent(text = "Room 12, 2026"), emptyMap(), now).single().reason)
        assertEquals(setOf("912345678"), PhoneMatcher.keysIn("ring +351 912 345 678 or 112"))
    }

    @Test
    fun guessesTheKindAndHowItCounts() {
        val photo = SharedContent(image = SharedImage(byteArrayOf(1), "image/jpeg"), takenOn = LocalDate(2026, 3, 8))
        assertEquals(ShareGuess(ShareChoice.INTERACTION, LogKind.MEETUP, LocalDate(2026, 3, 8)), ShareGuesser.guess(photo))
        assertEquals(AttachmentKind.PHOTO, photo.kind)

        val link = SharedContent(text = "https://wikiloc.com/t/1", url = "https://wikiloc.com/t/1", title = "Gerês trail")
        assertEquals(AttachmentKind.LINK, link.kind)
        assertEquals(ShareChoice.NOTE, ShareGuesser.guess(link).choice)
        assertEquals("Gerês trail", link.draft())

        val chat = SharedContent(text = "Ok so the job in Porto is confirmed, moving in March, need help with the van")
        assertEquals(AttachmentKind.TEXT, chat.kind)
        assertEquals(ShareGuess(ShareChoice.INTERACTION, LogKind.MESSAGE, null), ShareGuesser.guess(chat))
        assertTrue(ShareGuesser.isChatLike("[13/11/26, 13:02] Rui: hi\n" + "long line ".repeat(100)))
        assertTrue(ShareGuesser.isChatLike("Rui: are you coming?\n" + "x\n".repeat(10)))

        val recipe = SharedContent(text = (1..12).joinToString("\n") { "Step $it: stir the pot well and wait" })
        assertEquals(ShareChoice.NOTE, ShareGuesser.guess(recipe).choice)
        assertFalse(ShareGuesser.isChatLike("  "))
        // Text that only mentions a link stays text.
        assertEquals(AttachmentKind.TEXT, SharedContent(text = "Look at this https://x.y", url = "https://x.y").kind)
    }

    @Test
    fun savingAPhotoThatCountsLogsAMeetupAndKeepsAThumbnailAndTheReference() = runTest {
        val g = TestGraph(listOf(ana))
        val image = SharedImage(ByteArray(ContactPhoto.MAX_BYTES + 1) { 7 }, "image/jpeg")
        val ref = PhotoRef(PhotoPlatform.ANDROID, "content://picker/1")
        val result = g.saveShared(
            SaveShareRequest(
                ShareTarget.Person(ana.id),
                SharedContent(image = image, photoRef = ref),
                ShareChoice.INTERACTION,
                LogKind.MEETUP,
                date = LocalDate(2026, 3, 8),
                caption = " Lunch at Ramiro ",
            ),
        )
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals(listOf(ana.id), hangout.friendIds)
        assertEquals("Lunch at Ramiro", hangout.note)
        val a = assertNotNull(result.attachment)
        assertEquals(a, g.interactionRepo.attachments.value.single())
        assertEquals(AttachmentKind.PHOTO, a.kind)
        assertEquals(ref, a.photoRef)
        assertEquals(16, assertNotNull(a.thumbnail).bytes().size, "only a thumbnail is kept")
        assertEquals(hangout.id, a.logId)
        assertEquals(hangout.at, a.at)
        assertEquals(InteractionType.MEETUP, a.interactionType)
        assertTrue(a.countsAsInteraction)
        assertEquals(g.clock.now(), a.createdAt)
        assertEquals(hangout.at, Relationships.lastContact(g.hangoutRepo.hangouts.value, emptyList())[ana.id])

        g.undoSaveShare(result)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertTrue(g.interactionRepo.attachments.value.isEmpty())
    }

    @Test
    fun savingTextAsAMessageLogsAShareInteraction_andANoteLogsNothing() = runTest {
        val g = TestGraph(listOf(ana))
        val long = "x".repeat(Attachment.MAX_TEXT + 50)
        val counted = g.saveShared(SaveShareRequest(ShareTarget.Person(ana.id), SharedContent(text = long), ShareChoice.INTERACTION, LogKind.MESSAGE))
        val interaction = g.interactionRepo.interactions.value.single()
        assertEquals(InteractionSource.SHARE, interaction.source)
        assertEquals(InteractionType.MESSAGE, interaction.type)
        assertEquals(interaction.id, counted.attachment?.logId)
        assertEquals(Attachment.MAX_TEXT, counted.attachment?.text?.length, "text is capped")

        val note = g.saveShared(
            SaveShareRequest(
                ShareTarget.Person(ana.id),
                SharedContent(text = "https://r.example/ramen", url = "https://r.example/ramen", title = "Ramen"),
                ShareChoice.NOTE,
                date = LocalDate(2026, 3, 1),
            ),
        )
        val link = assertNotNull(note.attachment)
        assertEquals(AttachmentKind.LINK, link.kind)
        assertNull(link.text, "a link keeps only its address and title")
        assertEquals("Ramen", link.title)
        assertEquals("r.example", link.host)
        assertFalse(link.countsAsInteraction)
        assertNull(link.logId)
        assertEquals(at(2026, 3, 1), link.at)
        assertEquals(1, g.interactionRepo.interactions.value.size, "a note never logs contact")
        assertEquals(listOf(interaction.id), g.interactionRepo.interactions.value.map { it.id })

        // Newest first on the page; deleting puts back with Undo.
        assertEquals(listOf(counted.attachment, link), g.observeAttachments(ana.id).first())
        g.deleteAttachment(link)
        assertEquals(listOf(counted.attachment), g.observeAttachments(ana.id).first())
        g.restoreAttachment(link)
        assertEquals(2, g.observeAttachments(ana.id).first().size)
    }

    @Test
    fun aShareForACircleShowsOnEveryMembersPageAndLogsEveryone() = runTest {
        val g = TestGraph(listOf(ana, rui, bea))
        g.groupRepo.upsert(FriendGroup("crew", "Crew", memberIds = listOf(ana.id, rui.id)))
        val result = g.saveShared(
            SaveShareRequest(ShareTarget.Circle("crew"), SharedContent(text = "Dinner on Friday?"), ShareChoice.INTERACTION, LogKind.MESSAGE),
        )
        val a = assertNotNull(result.attachment)
        assertEquals("crew", a.circleId)
        assertNull(a.friendId)
        assertEquals(setOf(ana.id, rui.id), g.interactionRepo.interactions.value.map { it.friendId }.toSet())
        assertEquals(g.interactionRepo.interactions.value.first().batchId, a.logId)
        assertEquals(listOf(a), g.observeAttachments(rui.id).first())
        assertTrue(g.observeAttachments(bea.id).first().isEmpty())
        // The timeline shows the share on the logged contact.
        val timeline = g.observeTimeline(ana.id).first()
        assertEquals(a, (timeline.single() as TimelineEntry.Contact).attachment)
        assertFailsWith<IllegalArgumentException> {
            g.saveShared(SaveShareRequest(ShareTarget.Circle("nope"), SharedContent(text = "x"), ShareChoice.NOTE))
        }
    }

    @Test
    fun aShopLinkCanBecomeAGiftIdeaWithAPrice() = runTest {
        val g = TestGraph(listOf(ana))
        val content = SharedContent(text = "https://shop.example/lego", url = "https://shop.example/lego", title = "LEGO dinosaur")
        val result = g.saveShared(SaveShareRequest(ShareTarget.Person(ana.id), content, ShareChoice.GIFT_IDEA, price = " €35 "))
        val gift = g.friendRepo.friends.value.single().gifts.single()
        assertEquals("LEGO dinosaur", gift.title)
        assertEquals("https://shop.example/lego", gift.url)
        assertEquals("€35", gift.price)
        assertTrue(gift.isIdea)
        assertNull(result.attachment)
        assertTrue(g.interactionRepo.attachments.value.isEmpty())
        g.undoSaveShare(result)
        assertTrue(g.friendRepo.friends.value.single().gifts.isEmpty())
    }

    @Test
    fun shareTargetsCarryLastContactAndCirclesWithMembers() = runTest {
        val g = TestGraph(listOf(ana, rui))
        g.groupRepo.upsert(FriendGroup("crew", "Crew", memberIds = listOf(ana.id)))
        g.groupRepo.upsert(FriendGroup("empty", "Empty"))
        g.checkIn(listOf(ana.id), null, null, "", at(2026, 3, 9))
        val targets = g.observeShareTargets().first()
        assertEquals(listOf("crew"), targets.circles.map { it.id })
        assertEquals(mapOf(ana.id to at(2026, 3, 9)), targets.lastContact)
        assertEquals(2, targets.friends.size)
    }

    @Test
    fun theInboxRemembersWhoAPhotoFromAPageIsFor() {
        val g = TestGraph()
        g.shareInbox.offer(SharedContent(text = "hi"), ana.id)
        assertEquals(ana.id, g.shareInbox.preselected.value)
        g.shareInbox.clear()
        assertNull(g.shareInbox.preselected.value)
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
