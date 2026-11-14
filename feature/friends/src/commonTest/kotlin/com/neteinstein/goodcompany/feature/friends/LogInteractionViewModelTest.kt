package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.log.LogInteractionViewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogInteractionViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(listOf(friend("ana"), friend("rui")))

    private fun vm(ids: List<FriendId> = listOf(ana), type: String? = null) = LogInteractionViewModel(
        ids,
        type,
        g.observeFriends,
        g.observeActivities,
        g.logInteraction,
        g.currentLocation,
        g.clock,
    )

    @Test
    fun typeFromTheRoutePreselectsTheKind() = runViewModelTest {
        assertEquals(LogKind.MEETUP, collecting(vm().state).value.sheet.kind)
        assertEquals(LogKind.CALL, collecting(vm(type = "CALL").state).value.sheet.kind)
        assertEquals(LogKind.MESSAGE, collecting(vm(type = "WHATSAPP").state).value.sheet.kind)
        assertEquals(LogKind.MEETUP, collecting(vm(type = "nonsense").state).value.sheet.kind)
    }

    @Test
    fun savingAMeetupWritesAHangoutWithTheActivity() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(listOf("ana"), state.value.sheet.people.map { it.id.value })
        assertEquals(HangoutActivity.entries.size, state.value.sheet.activities.size)
        vm.onPeople(listOf(ana, rui, ana))
        vm.onActivity(HangoutActivity.COFFEE)
        vm.onPlaceName("Café")
        vm.onNote("  catch-up  ")
        vm.onSave()
        assertTrue(state.value.done)
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(setOf(ana, rui), h.friendIds.toSet())
        assertEquals(HangoutActivity.COFFEE, h.activity)
        assertEquals("catch-up", h.note)
    }

    @Test
    fun savingACallInThePastLogsItThatDayAndDropsMeetupFields() = runViewModelTest {
        val vm = vm(type = "CALL")
        val state = collecting(vm.state)
        val today = g.clock.today()
        vm.onDate(today.plus(DatePeriod(days = 3)))
        assertEquals(today, state.value.sheet.date)
        vm.onDate(today.plus(DatePeriod(days = -1)))
        vm.onActivity(HangoutActivity.DINNER)
        vm.onSave()
        assertTrue(state.value.done)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        val call = g.interactionRepo.interactions.value.single()
        assertEquals(InteractionType.CALL, call.type)
        assertTrue(call.at < g.clock.now())
    }

    @Test
    fun aJournalEntryNeedsText() = runViewModelTest {
        val vm = vm(type = "JOURNAL")
        val state = collecting(vm.state)
        assertFalse(state.value.sheet.canSave)
        vm.onSave()
        assertFalse(state.value.done)
        vm.onNote("Talked about Porto")
        vm.onSave()
        assertTrue(state.value.done)
        assertEquals("Talked about Porto", g.interactionRepo.journal.value.single().text)
    }

    @Test
    fun locationCanBeUsedAndCleared() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onUseLocation()
        vm.onClearLocation()
        assertNull(state.value.sheet.location)
        vm.onKind(LogKind.EMAIL)
        assertEquals(LogKind.EMAIL, state.value.sheet.kind)
    }
}
