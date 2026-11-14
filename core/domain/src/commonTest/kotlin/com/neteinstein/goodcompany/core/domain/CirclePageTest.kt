package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.CoverShrinker
import com.neteinstein.goodcompany.core.domain.repository.coverOf
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.CirclePages
import com.neteinstein.goodcompany.core.domain.usecase.CircleUpcoming
import com.neteinstein.goodcompany.core.domain.usecase.FamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The circle page (issue #88): status, "Coming up", the timeline, circle reminders and covers. */
class CirclePageTest {
    private val names = listOf("ana", "rui", "eva", "joao", "rita", "tiago", "outsider")
    private val people = names.map { friend(it) }
    private fun ids(vararg n: String) = n.map { FriendId(it) }
    private val pair = FriendGroup("pair", "Pair", memberIds = ids("ana", "rui"))
    private val six = FriendGroup("six", "Six", memberIds = ids("ana", "rui", "eva", "joao", "rita", "tiago"))
    private val today = LocalDate(2026, 3, 10)

    private fun page(
        group: FriendGroup,
        hangouts: List<Hangout> = emptyList(),
        plans: List<Plan> = emptyList(),
        reminders: List<Reminder> = emptyList(),
        attachments: List<Attachment> = emptyList(),
    ) =
        CirclePages.build(group.id, listOf(pair, six), people, hangouts, plans, reminders, attachments, today, TimeZone.UTC)

    @Test
    fun aCircleOfTwoNeedsBoth_aCircleOfSixNeedsThree() {
        val hangouts = listOf(
            Hangout("h1", ids("ana"), at(2026, 3, 1)),
            Hangout("h2", ids("ana", "rui"), at(2026, 3, 2)),
            Hangout("h3", ids("ana", "rui", "eva"), at(2026, 3, 3)),
        )
        assertEquals(listOf("h3", "h2"), page(pair, hangouts)!!.timeline.map { (it as TimelineEntry.Meetup).hangout.id })
        assertEquals(listOf("h3"), page(six, hangouts)!!.timeline.map { (it as TimelineEntry.Meetup).hangout.id })
    }

    @Test
    fun membersComeFirst_thenAnyoneElse_withCounts() {
        val h = Hangout("h", ids("outsider", "rui", "ana", "eva"), at(2026, 3, 1))
        val p = page(six, listOf(h))!!
        val meetup = p.timeline.single() as TimelineEntry.Meetup
        assertEquals(listOf("ana", "rui", "eva", "outsider"), meetup.others.map { it.id.value })
        assertEquals(3, p.membersAt(meetup))
        assertFalse(p.everyoneAt(meetup))
        val all = page(pair, listOf(h))!!
        assertTrue(all.everyoneAt(all.timeline.single() as TimelineEntry.Meetup))
    }

    @Test
    fun aShareLoggedAsAMeetupShowsOnce_otherSharesStandAlone() {
        val h = Hangout("h", ids("ana", "rui"), at(2026, 3, 1))
        val onMeetup =
            Attachment(
                "a1",
                circleId = "pair",
                kind = AttachmentKind.TEXT,
                text = "Fun",
                at = at(2026, 3, 1),
                countsAsInteraction = true,
                logId = "h",
            )
        val alone = Attachment("a2", circleId = "pair", kind = AttachmentKind.LINK, url = "https://example.org", at = at(2026, 3, 5))
        val elsewhere = Attachment("a3", circleId = "six", kind = AttachmentKind.TEXT, at = at(2026, 3, 6))
        val timeline = page(pair, listOf(h), attachments = listOf(onMeetup, alone, elsewhere))!!.timeline
        assertEquals(listOf("share-a2", "hangout-h"), timeline.map { it.key })
        assertEquals(onMeetup, (timeline[1] as TimelineEntry.Meetup).attachment)
    }

    @Test
    fun comingUp_isTheCirclesOpenPlansAndRemindersNotDone_undatedLast() {
        val plans = listOf(
            Plan("p1", emptyList(), "Dinner", LocalDate(2026, 3, 20), createdAt = at(2026, 3, 1), circleId = "pair"),
            Plan("p2", emptyList(), "Done", LocalDate(2026, 3, 12), status = PlanStatus.HAPPENED, createdAt = at(2026, 3, 1), circleId = "pair"),
            Plan("p3", emptyList(), "Other circle", LocalDate(2026, 3, 12), createdAt = at(2026, 3, 1), circleId = "six"),
        )
        val reminders = listOf(
            Reminder("r1", "Book", dueAt = at(2026, 3, 15), createdAt = at(2026, 3, 1), circleId = "pair"),
            Reminder("r2", "Someday", createdAt = at(2026, 3, 1), circleId = "pair"),
            Reminder("r3", "Done", dueAt = at(2026, 3, 11), done = true, createdAt = at(2026, 3, 1), circleId = "pair"),
            Reminder("r4", "Not the circle", dueAt = at(2026, 3, 11), friendIds = ids("ana"), createdAt = at(2026, 3, 1)),
        )
        val upcoming = page(pair, plans = plans, reminders = reminders)!!.upcoming
        assertEquals(
            listOf("r1", "p1", "r2"),
            upcoming.map {
                when (it) {
                    is CircleUpcoming.PlanItem -> it.plan.id
                    is CircleUpcoming.ReminderItem -> it.reminder.id
                }
            },
        )
        assertNull(upcoming.last().date)
    }

    @Test
    fun aMissingCircle_hasNoPage() {
        assertNull(page(FriendGroup("gone", "Gone")))
    }

    @Test
    fun theFamilyCircleHasAPage() = runTest {
        val g = TestGraph(listOf(friend("ana") { copy(relationToMe = LinkType.SIBLING) }, friend("rui")))
        val p = g.observeCirclePage(FamilyCircle.ID).first()
        assertNotNull(p)
        assertEquals(listOf("ana"), p.members.map { it.id.value })
    }

    @Test
    fun theObservedPageFollowsTheCircleAway() = runTest {
        val g = TestGraph(people)
        g.groupRepo.upsert(pair)
        assertNotNull(g.observeCirclePage("pair").first())
        g.groupRepo.delete("pair")
        assertNull(g.observeCirclePage("pair").first())
    }

    // --- Circle reminders ---

    @Test
    fun aCircleReminderReachesTheMembers_includingSomeoneWhoJoinedLater() {
        val r = Reminder("r", "Book the hut", friendIds = ids("outsider"), dueAt = at(2026, 3, 11), createdAt = at(2026, 3, 1), circleId = "pair")
        assertEquals(ids("outsider", "ana", "rui"), r.peopleIds(listOf(pair)))
        val grown = pair.copy(memberIds = pair.memberIds + FriendId("eva"))
        assertTrue(r.isFor(FriendId("eva"), listOf(grown)))
        val item = AgendaBuilder.build(people, listOf(r), emptyList(), today, TimeZone.UTC, groups = listOf(grown))
            .filterIsInstance<AgendaItem.ReminderDue>().single()
        assertEquals(listOf("outsider", "ana", "rui", "eva"), item.friends.map { it.id.value })
    }

    @Test
    fun aDeletedCirclesReminderKeepsItsOwnPeople() {
        val r = Reminder("r", "Call", friendIds = ids("ana"), createdAt = at(2026, 3, 1), circleId = "gone")
        assertEquals(ids("ana"), r.peopleIds(listOf(pair)))
        assertFalse(r.isFor(FriendId("rui"), listOf(pair)))
    }

    @Test
    fun addAndEditKeepTheCircle() = runTest {
        val g = TestGraph(people)
        val added = g.addReminder("Book", ids("ana"), null, circleId = "pair")
        assertEquals("pair", g.reminderRepo.reminders.first().single().circleId)
        val edited = g.editReminder(added, " Book the hut ", ids("ana", "ana"), at(2026, 3, 20), circleId = null)
        assertEquals("Book the hut", edited.title)
        assertEquals(ids("ana"), edited.friendIds)
        assertNull(g.reminderRepo.reminders.first().single().circleId)
    }

    // --- Covers ---

    @Test
    fun coverSetReplaceRemoveAndRestore() = runTest {
        val g = TestGraph(people)
        g.groupRepo.upsert(pair)
        assertNull(g.circleCovers.set("pair", byteArrayOf(1, 2, 3)).getOrThrow())
        val first = g.coverRepo.covers.first()["pair"]
        assertNotNull(first)
        assertEquals(first, g.circleCovers.set("pair", byteArrayOf(4, 5)).getOrThrow())
        val removed = g.circleCovers.remove("pair")
        assertNotNull(removed)
        assertNull(g.coverRepo.covers.first()["pair"])
        assertNull(g.circleCovers.remove("pair"))
        g.circleCovers.restore("pair", removed)
        assertEquals(removed, g.coverRepo.covers.first()["pair"])
        assertTrue(g.circleCovers.set("pair", null).isFailure)
        assertTrue(g.circleCovers.set("pair", ByteArray(0)).isFailure)
    }

    @Test
    fun theCircleCarriesItsCover() = runTest {
        val g = TestGraph(people)
        g.groupRepo.upsert(pair)
        g.circleCovers.set("pair", byteArrayOf(9))
        assertEquals(g.coverRepo.covers.first()["pair"], g.circles.groups.first().single { it.id == "pair" }.cover)
    }

    @Test
    fun deletingACircleTakesItsCover_andUndoBringsItBack() = runTest {
        val g = TestGraph(people)
        g.groupRepo.upsert(pair)
        g.circleCovers.set("pair", byteArrayOf(7, 7))
        val deleted = g.deleteGroup("pair")
        assertNotNull(deleted?.cover)
        assertTrue(g.coverRepo.covers.first().isEmpty())
        g.restoreGroup(deleted!!)
        assertEquals(deleted.cover, g.coverRepo.covers.first()["pair"])
    }

    @Test
    fun coverOf_keepsSmallImages_shrinksLargeOnes_andDropsWhatStaysTooLarge() = runTest {
        val small = ByteArray(10) { 1 }
        val large = ByteArray(ContactPhoto.COVER_MAX_BYTES + 1) { 2 }
        assertEquals(ContactPhoto.cover(small), CoverShrinker.None.coverOf(small))
        assertNull(CoverShrinker.None.coverOf(large)) // nothing shrank it
        assertNotNull(CoverShrinker { it.copyOf(100) }.coverOf(large))
        assertNull(CoverShrinker { error("can't decode") }.coverOf(large))
        assertNull(CoverShrinker.None.coverOf(null))
        // A cover may be larger than an avatar.
        val between = ByteArray(ContactPhoto.MAX_BYTES + 1)
        assertNull(ContactPhoto.of(between))
        assertNotNull(ContactPhoto.cover(between))
    }

    @Test
    fun coversAndCircleRemindersTravelInABackupFile_andOlderFilesHaveNone() = runTest {
        val from = TestGraph(people)
        from.groupRepo.upsert(pair)
        from.circleCovers.set("pair", byteArrayOf(3, 1, 4))
        from.addReminder("Book", emptyList(), null, circleId = "pair")
        val json = from.exportBackup()

        val to = TestGraph()
        assertIs<RestoreResult.Restored>(to.restoreBackup(json))
        assertEquals(from.coverRepo.covers.first(), to.coverRepo.covers.first())
        assertEquals("pair", to.backupRepo.restored!!.reminders.single().circleId)

        val old = """{"format":"good-company-backup","version":1,"reminders":[{"id":"r","title":"Call","createdAt":"2026-03-01T00:00:00Z"}]}"""
        val again = TestGraph()
        assertIs<RestoreResult.Restored>(again.restoreBackup(old))
        assertNull(again.backupRepo.restored!!.reminders.single().circleId)
        assertTrue(again.coverRepo.covers.first().isEmpty())
    }

    // --- Sample data ---

    @Test
    fun theSampleSurfCircleGetsTheBundledCover_andLosesItWithTheSample() = runTest {
        val g = TestGraph()
        g.loadSampleData()
        assertEquals(g.sampleCover, g.coverRepo.covers.first()[SampleData.COVER_CIRCLE_ID])
        // The user's own cover on a circle of theirs stays.
        g.coverRepo.set("mine", ContactPhoto("bWluZQ=="))
        g.removeSampleData()
        assertEquals(setOf("mine"), g.coverRepo.covers.first().keys)
    }

    @Test
    fun reloadingTheSampleKeepsACoverTheUserChanged() = runTest {
        val g = TestGraph()
        g.loadSampleData()
        val changed = ContactPhoto("Y2hhbmdlZA==")
        g.coverRepo.set(SampleData.COVER_CIRCLE_ID, changed)
        g.loadSampleData()
        assertEquals(changed, g.coverRepo.covers.first()[SampleData.COVER_CIRCLE_ID])
    }
}
