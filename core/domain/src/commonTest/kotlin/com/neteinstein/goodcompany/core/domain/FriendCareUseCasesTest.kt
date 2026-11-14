package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.repository.LanguageProvider
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MarkIdeaProposedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MissingApiKeyException
import com.neteinstein.goodcompany.core.domain.usecase.NextCircleColorUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetClosenessUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetHomeCircleFilterUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.SuggestGiftsUseCase
import com.neteinstein.goodcompany.core.testing.FakeAiAssistant
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakePersonLinkRepository
import com.neteinstein.goodcompany.core.testing.FakePlanRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendCareUseCasesTest {
    private val clock = FixedClock()
    private val ids = SequentialIds()
    private val ana = FriendId("ana")
    private val friends = FakeFriendRepository(
        listOf(
            friend("ana", "Ana Silva") { copy(phoneNumbers = listOf("+351 912 345 678")) },
            friend("rui", "Rui Costa"),
            friend("leo", "Leo") { copy(likes = listOf("LEGO")) },
        ),
    )
    private val links = FakePersonLinkRepository(listOf(PersonLink("l", ana, FriendId("leo"), LinkType.CHILD)), friends)
    private val groups = FakeGroupRepository()
    private val hangouts = FakeHangoutRepository()
    private val interactions = FakeInteractionRepository()
    private val settings = FakeSettingsRepository()
    private val snooze = SnoozeCatchUpUseCase(friends, groups, clock)

    @Test
    fun realFriendFollowsTheLevel() = runTest {
        assertEquals(Closeness.ACQUAINTANCE, friends.get(ana)!!.closeness)
        SetClosenessUseCase(friends)(ana, Closeness.INNER)
        assertEquals(Closeness.INNER, friends.get(ana)!!.closeness)
        assertTrue(friends.get(ana)!!.isCloseFriend)
        SetClosenessUseCase(friends)(ana, Closeness.ACQUAINTANCE)
        assertTrue(!friends.get(ana)!!.isCloseFriend)
        SetClosenessUseCase(friends)(ana, Closeness.GOOD) // every level but Acquaintances is a real friend
        assertTrue(friends.get(ana)!!.isCloseFriend)
    }

    @Test
    fun snoozeAndLogCall() = runTest {
        snooze.friend(ana, SnoozeOption.WEEKEND)
        assertEquals(LocalDate(2026, 3, 14), friends.get(ana)!!.snoozedUntil)
        snooze.clearFriend(ana)
        assertNull(friends.get(ana)!!.snoozedUntil)
        LogCallUseCase(interactions, snooze, clock, ids)(ana)
        assertEquals(InteractionType.CALL, interactions.interactions.value.single().type)
        assertEquals(LocalDate(2026, 3, 17), friends.get(ana)!!.snoozedUntil)
    }

    @Test
    fun notificationActionsDoTheRightThing() = runTest {
        val handle =
            HandleNotificationActionUseCase(
                CheckInUseCase(hangouts, clock, ids),
                LogCallUseCase(interactions, snooze, clock, ids),
                snooze,
                groups,
                ConfirmPlanUseCase(FakePlanRepository(), friends, CheckInUseCase(hangouts, clock, ids), clock),
            )
        handle(NotificationAction.decode(NotificationAction(NotificationAction.Kind.MET, "ana").encode())!!)
        assertEquals(listOf(ana), hangouts.hangouts.value.single().friendIds)
        handle(NotificationAction(NotificationAction.Kind.SNOOZE_WEEK, "rui"))
        assertEquals(LocalDate(2026, 3, 17), friends.get(FriendId("rui"))!!.snoozedUntil)

        groups.upsert(FriendGroup("g", "Crew", memberIds = listOf(ana, FriendId("rui"))))
        handle(NotificationAction(NotificationAction.Kind.MET, "g", isGroup = true))
        assertEquals(listOf(ana, FriendId("rui")), hangouts.hangouts.value.last().friendIds)
        handle(NotificationAction(NotificationAction.Kind.SNOOZE_WEEKEND, "g", isGroup = true))
        assertEquals(LocalDate(2026, 3, 14), groups.groups.value.single().snoozedUntil)
    }

    @Test
    fun actionEncodingRoundTripsAndRejectsGarbage() {
        val a = NotificationAction(NotificationAction.Kind.CALLED, "sample-ana|x", isGroup = false)
        assertEquals(a, NotificationAction.decode(a.encode()))
        assertNull(NotificationAction.decode("nope"))
        assertNull(NotificationAction.decode("BOOM|f|x"))
    }

    @Test
    fun vcfImportAddsNewAndOnlyFillsGaps() = runTest {
        val vcf = "BEGIN:VCARD\nFN:ana silva\nTEL:912345678\nBDAY:1990-05-04\nEND:VCARD\n" +
            "BEGIN:VCARD\nFN:Zoe\nTEL:+44 7700 900000\nEND:VCARD\n" +
            "BEGIN:VCARD\nFN:Rui Costa\nEND:VCARD\n"
        val result = ImportContactsFileUseCase(friends, ids)(vcf)
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(1, result.skipped)
        val anaNow = friends.get(ana)!!
        assertEquals(Birthday(5, 4, 1990), anaNow.birthday)
        assertEquals(1, anaNow.phoneNumbers.size) // same number, different formatting → not duplicated
        val zoe = friends.friends.value.single { it.name == "Zoe" }
        assertNull(zoe.contactId) // manual friend, safe from device sync
    }

    @Test
    fun aiHelpersNeedAKeyAndUseTheUserLanguage() = runTest {
        val ai = FakeAiAssistant(gifts = Result.success(listOf(" LEGO castle ", "lego castle", "Vinyl", "Kind of blue")))
        val pt = LanguageProvider { "pt-PT" }
        val draft = DraftBirthdayMessageUseCase(friends, interactions, FakeHangoutRepository(), settings, ai, pt)
        assertIs<MissingApiKeyException>(draft(ana).exceptionOrNull())
        settings.update { it.copy(aiApiKey = "k") }
        assertIs<MissingApiKeyException>(draft(ana).exceptionOrNull()) // a key alone isn't enough while AI is off (F1)
        assertTrue(ai.calls.isEmpty())
        settings.update { it.copy(aiEnabled = true) }
        assertEquals("Happy birthday!", draft(ana).getOrThrow())
        assertEquals("message:ana:pt-PT", ai.calls.single())

        friends.upsert(friends.get(ana)!!.copy(gifts = listOf(Gift("g", "Kind of Blue"))))
        val suggest = SuggestGiftsUseCase(friends, interactions, FakeHangoutRepository(), settings, ai, pt, links)
        val gifts = suggest(ana).getOrThrow()
        assertEquals(listOf("LEGO castle", "lego castle", "Vinyl"), gifts) // trims and drops ones you already have
        assertEquals("gifts:ana:-:pt-PT", ai.calls.last())
        // A kid (F9) is asked about as their parent's kid, from the kid's own page.
        suggest(FriendId("leo"))
        assertEquals("gifts:ana:Leo:pt-PT", ai.calls.last())
    }

    @Test
    fun circlesCrudAndValidation() = runTest {
        val save = SaveGroupUseCase(groups, ids)
        assertFailsWith<IllegalArgumentException> { save(null, "Nobody", "🎲", CircleColor.BLUE, emptyList(), null) }
        assertFailsWith<IllegalArgumentException> { save(null, " ", "🎲", CircleColor.BLUE, listOf(ana, FriendId("rui")), null) }
        val g = save(null, " Crew ", "", CircleColor.TEAL, listOf(ana, FriendId("rui"), ana), 0)
        assertEquals("Crew", g.name)
        assertEquals("👥", g.emoji)
        assertEquals(CircleColor.TEAL, g.color)
        assertEquals(2, g.memberIds.size)
        assertNull(g.meetEveryDays)
        snooze.group(g.id, SnoozeOption.ONE_WEEK)
        save(g.id, "Crew", "⚽", CircleColor.RUST, g.memberIds, 14)
        val status = ObserveGroupsUseCase(groups, friends, hangouts, clock)().first().single()
        assertEquals("⚽", status.group.emoji)
        assertEquals(CircleColor.RUST, status.group.color)
        assertEquals(14, status.group.meetEveryDays)
        assertEquals(SnoozeOption.ONE_WEEK.until(clock.today()), status.group.snoozedUntil) // an edit keeps the snooze

        // A circle of one is fine (D1).
        val solo = save(null, "Mentor", "🧭", CircleColor.SLATE, listOf(ana), null)
        assertEquals(listOf(ana), solo.memberIds)

        // Deleting the circle Home is filtered by puts Home back on All; Undo puts the circle back.
        SetHomeCircleFilterUseCase(settings)(g.id)
        val removed = DeleteGroupUseCase(groups, settings)(g.id)!!
        assertNull(settings.settings.first().homeCircleFilter)
        assertEquals(listOf(solo.id), groups.groups.value.map { it.id })
        RestoreGroupUseCase(groups)(removed)
        assertEquals(setOf(g.id, solo.id), groups.groups.value.map { it.id }.toSet())
        // Deleting another circle leaves the filter alone.
        SetHomeCircleFilterUseCase(settings)(g.id)
        DeleteGroupUseCase(groups, settings)(solo.id)
        assertEquals(g.id, settings.settings.first().homeCircleFilter)
        assertNull(DeleteGroupUseCase(groups, settings)("gone"))
    }

    @Test
    fun newCirclesTakeTheNextFreeColour() = runTest {
        val next = NextCircleColorUseCase(groups)
        assertEquals(CircleColor.PURPLE, next())
        groups.upsert(FriendGroup("a", "A", color = CircleColor.PURPLE))
        groups.upsert(FriendGroup("b", "B", color = CircleColor.TEAL))
        assertEquals(CircleColor.BLUE, next())
        CircleColor.entries.forEachIndexed { i, c -> groups.upsert(FriendGroup("c$i", "C$i", color = c)) }
        assertEquals(CircleColor.forIndex(groups.groups.value.size), next())
        assertEquals(CircleColor.PURPLE, CircleColor.parse("NOPE"))
        assertEquals(CircleColor.SLATE, CircleColor.parse("SLATE"))
    }

    @Test
    fun markIdeaProposed() = runTest {
        val ideas = FakeIdeaRepository()
        ideas.addAll(listOf(Idea("i", listOf(ana), IdeaKind.EVENT, "Jazz", "", createdAt = clock.now())))
        MarkIdeaProposedUseCase(ideas, clock)("i")
        assertEquals(clock.now(), ideas.ideas.value.single().proposedAt)
    }
}
