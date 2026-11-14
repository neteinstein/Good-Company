package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.usecase.CircleUpcoming
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.core.ui.components.ReminderDraft
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.page.CirclePageMessage
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.page.CirclePageUndo
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.page.CirclePageViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CirclePageViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val surf = FriendGroup("surf", "Surf", "🏄", listOf(ana, rui), meetEveryDays = 21)
    private val g = TestGraph(
        listOf(friend("ana"), friend("rui"), friend("eva")),
        hangouts = listOf(Hangout("h1", listOf(ana, rui), at(2026, 3, 1)), Hangout("h2", listOf(ana), at(2026, 3, 2))),
    )

    private fun vm(id: String = "surf") = CirclePageViewModel(
        id,
        g.observeCirclePage,
        g.observeFriends,
        g.observeGroups,
        g.savePlan,
        g.addReminder,
        g.editReminder,
        g.snooze,
        g.deleteTimelineEntry,
        g.restoreTimelineEntry,
        g.circleCovers,
        g.clock,
    )

    @Test
    fun showsTheCircle_itsPeopleAndOnlyItsMeetups() = runViewModelTest {
        g.groupRepo.upsert(surf)
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.isLoading)
        assertFalse(state.value.isGone)
        val page = assertNotNull(state.value.page)
        assertEquals("Surf", page.group.name)
        assertEquals(listOf("ana", "rui"), page.members.map { it.id.value })
        assertEquals(listOf("hangout-h1"), page.timeline.map { it.key })
        assertEquals(listOf("Ana", "Eva", "Rui").sorted(), state.value.friends.map { it.name }.sorted())
    }

    @Test
    fun aMissingCircle_closesThePage() = runViewModelTest {
        val vm = vm("gone")
        val state = collecting(vm.state)
        assertTrue(state.value.isGone)
        assertNull(state.value.page)
    }

    @Test
    fun deletingAMeetup_hasUndo() = runViewModelTest {
        g.groupRepo.upsert(surf)
        val vm = vm()
        val state = collecting(vm.state)
        val meetup = state.value.page!!.timeline.single() as TimelineEntry.Meetup
        vm.onDeleteEntry(meetup)
        assertEquals(CirclePageMessage.EntryDeleted, state.value.message)
        assertIs<CirclePageUndo.Entry>(state.value.undo)
        assertTrue(state.value.page!!.timeline.isEmpty())
        assertEquals(listOf("h2"), g.hangoutRepo.hangouts.value.map { it.id })

        vm.onUndo()
        assertEquals(listOf("hangout-h1"), state.value.page!!.timeline.map { it.key })
        assertNull(state.value.undo)
    }

    @Test
    fun snooze_isRememberedOnTheCircle() = runViewModelTest {
        g.groupRepo.upsert(surf)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSnooze(SnoozeOption.ONE_WEEK)
        val message = assertIs<CirclePageMessage.Snoozed>(state.value.message)
        assertEquals("Surf", message.name)
        assertEquals(message.until, g.groupRepo.groups.value.single().snoozedUntil)
        vm.onMessageShown()
        assertNull(state.value.message)
    }

    @Test
    fun aNewReminder_belongsToTheCircle_andCanBeEdited() = runViewModelTest {
        g.groupRepo.upsert(surf)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onNewReminder()
        assertNotNull(state.value.reminderEditor)
        vm.onSaveReminder(ReminderDraft("  ", emptyList(), null, 18, "surf"))
        assertNotNull(state.value.reminderEditor, "a blank title isn't saved")
        vm.onSaveReminder(ReminderDraft("Book the hut", emptyList(), LocalDate(2026, 3, 20), 18, "surf"))
        assertNull(state.value.reminderEditor)
        assertEquals(CirclePageMessage.ReminderSaved, state.value.message)
        val saved = g.reminderRepo.reminders.first().single()
        assertEquals("surf", saved.circleId)
        assertEquals(at(2026, 3, 20, 18), saved.dueAt)
        val item = assertIs<CircleUpcoming.ReminderItem>(state.value.page!!.upcoming.single())

        vm.onOpenReminder(item.reminder)
        vm.onSaveReminder(ReminderDraft("Book the big hut", listOf(rui), null, 18, "surf"))
        val edited = g.reminderRepo.reminders.first().single()
        assertEquals("Book the big hut", edited.title)
        assertEquals(listOf(rui), edited.friendIds)
        assertNull(edited.dueAt)
    }

    @Test
    fun aPlanWithTheCircle_showsOnComingUp() = runViewModelTest {
        g.groupRepo.upsert(surf)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSavePlan(PlanInput("Dinner", emptyList(), LocalDate(2026, 3, 14), circleId = "surf"))
        assertTrue(g.planRepo.plans.value.isEmpty(), "no editor open")
        vm.onNewPlan()
        vm.onSavePlan(PlanInput("Dinner", emptyList(), LocalDate(2026, 3, 14), circleId = "surf"))
        assertEquals(CirclePageMessage.PlanSaved, state.value.message)
        assertNull(state.value.planEditor)
        assertIs<CircleUpcoming.PlanItem>(state.value.page!!.upcoming.single())
        vm.onDismissPlan()
    }

    @Test
    fun theCover_canBeSetAndRemovedWithUndo() = runViewModelTest {
        g.groupRepo.upsert(surf)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onCoverPicked(byteArrayOf(1, 2, 3))
        val cover: ContactPhoto = assertNotNull(state.value.page!!.group.cover)

        vm.onRemoveCover()
        assertEquals(CirclePageMessage.CoverRemoved, state.value.message)
        assertNull(state.value.page!!.group.cover)
        vm.onUndo()
        assertEquals(cover, state.value.page!!.group.cover)

        vm.onCoverPicked(null)
        assertEquals(CirclePageMessage.CoverUnreadable, state.value.message)
        assertEquals(cover, state.value.page!!.group.cover)
    }
}
