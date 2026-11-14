package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
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
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCircleUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRealFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.SuggestGiftsUseCase
import com.neteinstein.goodcompany.core.testing.FakeAiAssistant
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
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
            friend("ana", "Ana Silva") { copy(phoneNumbers = listOf("+351 912 345 678"), kids = listOf(Kid("leo", "Leo", likes = listOf("LEGO")))) },
            friend("rui", "Rui Costa"),
        ),
    )
    private val groups = FakeGroupRepository()
    private val hangouts = FakeHangoutRepository()
    private val interactions = FakeInteractionRepository()
    private val settings = FakeSettingsRepository()
    private val snooze = SnoozeCatchUpUseCase(friends, groups, clock)

    @Test
    fun markingRealFriendPutsThemInCloseCircle() = runTest {
        val set = SetRealFriendUseCase(friends)
        set(ana, true)
        assertEquals(Circle.CLOSE, friends.get(ana)!!.circle)
        SetCircleUseCase(friends)(ana, Circle.INNER)
        set.toggle(ana) // unmark
        assertNull(friends.get(ana)!!.circle)
        assertTrue(!friends.get(ana)!!.isCloseFriend)
        SetCircleUseCase(friends)(ana, Circle.GOOD) // a circle implies real friend
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
        assertEquals("Happy birthday!", draft(ana).getOrThrow())
        assertEquals("message:ana:pt-PT", ai.calls.single())

        friends.upsert(friends.get(ana)!!.copy(gifts = listOf(Gift("g", "Kind of Blue"))))
        val gifts = SuggestGiftsUseCase(friends, interactions, FakeHangoutRepository(), settings, ai, pt)(ana).getOrThrow()
        assertEquals(listOf("LEGO castle", "lego castle", "Vinyl"), gifts) // trims and drops ones you already have
        SuggestGiftsUseCase(friends, interactions, FakeHangoutRepository(), settings, ai, pt)(ana, kidId = "leo")
        assertEquals("gifts:ana:Leo:pt-PT", ai.calls.last())
    }

    @Test
    fun groupsCrudAndValidation() = runTest {
        val save = SaveGroupUseCase(groups, ids)
        assertFailsWith<IllegalArgumentException> { save(null, "Solo", "🎲", listOf(ana), null) }
        assertFailsWith<IllegalArgumentException> { save(null, " ", "🎲", listOf(ana, FriendId("rui")), null) }
        val g = save(null, " Crew ", "", listOf(ana, FriendId("rui"), ana), 0)
        assertEquals("Crew", g.name)
        assertEquals("👥", g.emoji)
        assertEquals(2, g.memberIds.size)
        assertNull(g.meetEveryDays)
        save(g.id, "Crew", "⚽", g.memberIds, 14)
        val status = ObserveGroupsUseCase(groups, friends, hangouts, clock)().first().single()
        assertEquals("⚽", status.group.emoji)
        assertEquals(14, status.group.meetEveryDays)
        DeleteGroupUseCase(groups)(g.id)
        assertTrue(groups.groups.value.isEmpty())
    }

    @Test
    fun markIdeaProposed() = runTest {
        val ideas = FakeIdeaRepository()
        ideas.addAll(listOf(Idea("i", listOf(ana), IdeaKind.EVENT, "Jazz", "", createdAt = clock.now())))
        MarkIdeaProposedUseCase(ideas, clock)("i")
        assertEquals(clock.now(), ideas.ideas.value.single().proposedAt)
    }
}
