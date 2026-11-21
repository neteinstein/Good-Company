package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogKindOption
import com.neteinstein.goodcompany.core.domain.usecase.LogTimeOfDay
import com.neteinstein.goodcompany.core.domain.usecase.ObserveLogKindsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntryRef
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.core.ui.components.LocationProblem
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.LogPrefill
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.LoggedMeetupViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.WeMetViewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class WeMetViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(listOf(friend("ana"), friend("rui")))

    private fun vm(
        ids: List<FriendId> = listOf(ana),
        circleId: String? = null,
        plan: PlanOccurrence? = null,
        prefill: LogPrefill = LogPrefill(),
    ) = WeMetViewModel(
        ids,
        circleId,
        plan,
        prefill,
        g.observeFriends,
        g.observeActivities,
        g.observeGroups,
        g.observePlaceBook,
        g.observePlans,
        g.observeLogKinds,
        g.logInteraction,
        g.confirmPlan,
        g.currentLocation,
        g.loggedMeetups,
        g.observeTimeline,
        g.editTimelineEntry,
        g.clock,
    )

    @Test
    fun todaysLogButton_startsWithNobody_picksPeople_andLogsALongChatThisMorning() = runViewModelTest {
        // Issue #116: Rui was in touch last, so he comes first in the picker.
        g.interactionRepo.addAll(listOf(Interaction(rui, InteractionType.CALL, at(2026, 3, 9), id = "c1")))
        val vm = vm(ids = emptyList())
        val state = collecting(vm.state)
        assertTrue(state.value.startsWithNobody)
        assertFalse(state.value.sheet.canSave)
        assertEquals(listOf(rui, ana), state.value.sheet.recentFirst.map { it.id })

        vm.onPeople(listOf(ana, rui))
        vm.onKind(LogKind.LONG_CHAT)
        vm.onTime(LogTimeOfDay.MORNING.time)
        assertEquals(LocalTime(9, 0), state.value.sheet.time)
        vm.onSave()

        assertTrue(state.value.done)
        val rows = g.interactionRepo.interactions.value.filter { it.type == InteractionType.LONG_CHAT }
        assertEquals(setOf(ana, rui), rows.map { it.friendId }.toSet())
        assertTrue(rows.all { it.at == at(2026, 3, 10, 9) })
    }

    @Test
    fun openedForSomeone_doesNotStartWithThePicker_andShowsTheLastTimeTogether() = runViewModelTest {
        g.hangoutRepo.add(Hangout("h", listOf(ana, rui), at(2026, 3, 1)))
        val vm = vm(ids = listOf(ana, rui))
        val state = collecting(vm.state)
        assertFalse(state.value.startsWithNobody)
        assertEquals(at(2026, 3, 1), state.value.sheet.lastTogether)
        vm.onKind(LogKind.CALL)
        assertNull(state.value.sheet.lastTogether)
    }

    @Test
    fun weMetThenSaveLogsTodayInTwoTaps() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val sheet = state.value.sheet
        assertEquals(g.clock.today(), sheet.date)
        assertEquals(listOf(ana), sheet.people.map { it.id })
        assertNull(sheet.activity)
        assertFalse(state.value.detailsOpen)
        assertTrue(sheet.canSave)

        vm.onSave()
        assertTrue(state.value.done)
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals(listOf(ana), hangout.friendIds)
        assertEquals(g.clock.now(), hangout.at)
        assertEquals("", hangout.note)
        assertEquals(listOf(ana), g.loggedMeetups.pending.value?.people?.map { it.id })
    }

    @Test
    fun fridaysLunchLoggedAWeekLater_withANote() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val friday = LocalDate(2026, 3, 6)
        vm.onDate(friday)
        vm.onActivity(ActivityChoice.BuiltIn(HangoutActivity.LUNCH))
        vm.onOpenDetails()
        assertTrue(state.value.detailsOpen)
        vm.onNote("Her new job at the studio")
        vm.onSave()
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals(at(2026, 3, 6), hangout.at)
        assertEquals(HangoutActivity.LUNCH, hangout.activity)
        assertEquals("Her new job at the studio", hangout.note)
    }

    @Test
    fun yesterdayWorks_andFutureDaysFallBackToToday() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val today = g.clock.today()
        vm.onDate(today.plus(DatePeriod(days = -1)))
        assertEquals(today.plus(DatePeriod(days = -1)), state.value.sheet.date)
        vm.onDate(today.plus(DatePeriod(days = 4)))
        assertEquals(today, state.value.sheet.date)
    }

    @Test
    fun theActivityIsOptionalAndTappingItAgainClearsIt() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(HangoutActivity.entries.size, state.value.sheet.activities.size)
        vm.onActivity(ActivityChoice.BuiltIn(HangoutActivity.COFFEE))
        assertEquals(ActivityChoice.BuiltIn(HangoutActivity.COFFEE), state.value.sheet.activity)
        vm.onActivity(null)
        assertNull(state.value.sheet.activity)
    }

    @Test
    fun aCircleListsItsMembersUnticked() = runViewModelTest {
        val circle = g.saveGroup(null, "Choir", "🎵", CircleColor.PURPLE, listOf(ana, rui), 14)
        val vm = vm(ids = emptyList(), circleId = circle.id)
        val state = collecting(vm.state)
        assertEquals("Choir", state.value.sheet.circle?.name)
        assertEquals(listOf(ana, rui), state.value.sheet.circleMembers.map { it.id })
        assertTrue(state.value.sheet.people.isEmpty())
        assertFalse(state.value.sheet.canSave)

        vm.onTogglePerson(rui) // Ana didn't come
        vm.onSave()
        assertEquals(listOf(rui), g.hangoutRepo.hangouts.value.single().friendIds)
    }

    @Test
    fun addPeopleAddsSomeoneElse() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onPeople(listOf(ana, rui, ana))
        assertEquals(listOf(ana, rui), state.value.sheet.people.map { it.id })
    }

    @Test
    fun aPlansDidYouMeetFillsThePanel_andSavingClosesThePlan() = runViewModelTest {
        val plan = g.savePlan(null, "Dinner at Rui’s", listOf(ana, rui), LocalDate(2026, 3, 7), "Porto")
        val vm = vm(ids = emptyList(), plan = PlanOccurrence(plan.id, plan.date))
        val state = collecting(vm.state)
        assertEquals(listOf(ana, rui), state.value.sheet.people.map { it.id })
        assertEquals(LocalDate(2026, 3, 7), state.value.sheet.date)
        assertEquals("Dinner at Rui’s", state.value.sheet.note)
        assertTrue(state.value.detailsOpen)
        vm.onSave()
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals("Porto", hangout.place?.name)
        assertEquals(PlanStatus.HAPPENED, g.planRepo.plans.value.single().status)
    }

    @Test
    fun undoOnTheSnackbarRemovesTheHangout_andReopensThePlan() = runViewModelTest {
        val plan = g.savePlan(null, "Lunch", listOf(ana), LocalDate(2026, 3, 6))
        val panel = vm(plan = PlanOccurrence(plan.id, plan.date))
        collecting(panel.state)
        panel.onSave()
        val snackbar = LoggedMeetupViewModel(g.loggedMeetups, g.undoLog)
        val meetup = snackbar.pending.value!!
        assertEquals(listOf(ana), meetup.people.map { it.id })

        snackbar.onUndo(meetup)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)
        assertNull(snackbar.pending.value)
    }

    @Test
    fun aShownSnackbarKeepsTheHangout() = runViewModelTest {
        val panel = vm()
        collecting(panel.state)
        panel.onSave()
        val snackbar = LoggedMeetupViewModel(g.loggedMeetups, g.undoLog)
        snackbar.onShown(snackbar.pending.value!!)
        assertNull(snackbar.pending.value)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
    }

    @Test
    fun whereAndNoteAreCollapsedUntilAsked_andThePlaceIsSaved() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.detailsOpen)
        vm.onOpenDetails()
        assertTrue(state.value.detailsOpen)
        vm.onPlaceName("Time Out")
        vm.onUseLocation()
        vm.onClearLocation()
        vm.onChangePlace()
        assertEquals("", state.value.sheet.place.name)
        vm.onPlaceName("Café Lisboa")
        vm.onSave()
        assertEquals("Café Lisboa", g.hangoutRepo.hangouts.value.single().place?.name)
    }

    @Test
    fun howStartsInPerson_andOffersEveryKind_mostUsedFirst() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(LogKind.MEETUP, state.value.sheet.kind)
        // Nothing logged: the default order, every kind (email and the journal too, issue #151).
        assertEquals(ObserveLogKindsUseCase.DEFAULT_ORDER, state.value.kinds.map { it.kind })
        g.interactionRepo.addAll(
            listOf(
                Interaction(ana, InteractionType.EMAIL, at(2026, 3, 1), id = "e1"),
                Interaction(ana, InteractionType.EMAIL, at(2026, 3, 2), id = "e2"),
                Interaction(rui, InteractionType.GAME, at(2026, 3, 3), id = "g1"),
            ),
        )
        assertEquals(listOf(LogKind.EMAIL, LogKind.GAME, LogKind.MEETUP, LogKind.CALL), state.value.kinds.take(4).map { it.kind })
        vm.onKind(LogKind.EMAIL)
        assertEquals(LogKind.EMAIL, state.value.sheet.kind)
        vm.onSave()
        assertEquals(InteractionType.EMAIL, g.interactionRepo.interactions.value.last().type)
    }

    @Test
    fun aJournalEntryOpensTheNote_andNeedsText() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onKind(LogKind.JOURNAL)
        assertTrue(state.value.detailsOpen)
        assertFalse(state.value.sheet.canSave)
        vm.onSave()
        assertFalse(state.value.done)
        vm.onActivity(ActivityChoice.BuiltIn(HangoutActivity.LUNCH))
        vm.onNote("Talked about Porto")
        vm.onSave()
        assertTrue(state.value.done)
        assertEquals("Talked about Porto", g.interactionRepo.journal.value.single().text)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
    }

    @Test
    fun aCalendarSuggestionOpensOnItsDayWithTheGuessedActivity() = runViewModelTest {
        val friday = g.clock.today().minus(DatePeriod(days = 4))
        val vm = vm(listOf(rui), prefill = LogPrefill(type = "MEETUP", date = friday, activity = HangoutActivity.LUNCH))
        val state = collecting(vm.state)
        assertEquals(friday, state.value.sheet.date)
        assertEquals(ActivityChoice.BuiltIn(HangoutActivity.LUNCH), state.value.sheet.activity)
        vm.onSave()
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(listOf(rui), h.friendIds)
        assertEquals(HangoutActivity.LUNCH, h.activity)
        // A future day is never prefilled (F2); one of your own activities is.
        val future = vm(prefill = LogPrefill(date = g.clock.today().plus(DatePeriod(days = 3)), activityName = "Surf"))
        val futureState = collecting(future.state)
        assertEquals(g.clock.today(), futureState.value.sheet.date)
        assertEquals(ActivityChoice.Custom("Surf"), futureState.value.sheet.activity)
    }

    @Test
    fun theTypeFromTheRoutePreselectsTheKind() = runViewModelTest {
        assertEquals(LogKind.CALL, collecting(vm(prefill = LogPrefill(type = "CALL")).state).value.sheet.kind)
        assertEquals(LogKind.MESSAGE, collecting(vm(prefill = LogPrefill(type = "WHATSAPP")).state).value.sheet.kind)
        assertEquals(LogKind.MEETUP, collecting(vm(prefill = LogPrefill(type = "nonsense")).state).value.sheet.kind)
    }

    @Test
    fun anUnknownIdFromAShortcutOpensWithNobodyAndIsNeverSaved() = runViewModelTest {
        val vm = vm(ids = listOf(FriendId("deleted")))
        val state = collecting(vm.state)
        assertTrue(state.value.sheet.people.isEmpty())
        assertFalse(state.value.sheet.canSave)
        vm.onTogglePerson(rui)
        vm.onSave()
        assertEquals(listOf(rui), g.hangoutRepo.hangouts.value.single().friendIds)
    }

    @Test
    fun useLocation_afterThePermission_fillsTheCoordinates_andSaysWhyWhenItCant() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onLocationPermissionResult(granted = false)
        assertEquals(LocationProblem.DENIED, state.value.sheet.place.locationProblem)
        g.location.point = null
        vm.onLocationPermissionResult(granted = true)
        assertEquals(LocationProblem.UNAVAILABLE, state.value.sheet.place.locationProblem)
        val here = GeoPoint(38.7139, -9.1394)
        g.location.point = here
        vm.onLocationPermissionResult(granted = true)
        assertEquals(here, state.value.sheet.place.location)
        assertTrue(state.value.detailsOpen)
    }

    @Test
    fun editModeOpensOnTheEntry_andSavingEditsItWithoutANewLogOrSnackbar() = runViewModelTest {
        val day = g.clock.today().minus(DatePeriod(days = 2))
        g.interactionRepo.addAll(listOf(Interaction(ana, InteractionType.CALL, g.clock.now() - 2.days, id = "c1", note = "Hi")))
        val vm = vm(prefill = LogPrefill(edit = TimelineEntryRef(ana, "interaction-c1")))
        val state = collecting(vm.state)
        val sheet = state.value.sheet
        assertTrue(sheet.editing)
        assertFalse(state.value.startsWithNobody)
        assertEquals(LogKind.CALL, sheet.kind)
        assertEquals(day, sheet.date)
        assertEquals("Hi", sheet.note)

        vm.onKind(LogKind.VIDEO_CALL)
        vm.onTogglePerson(rui)
        vm.onNote("Long one")
        vm.onSave()
        assertTrue(state.value.done)
        val rows = g.interactionRepo.interactions.value
        assertEquals(setOf(ana, rui), rows.map { it.friendId }.toSet())
        assertTrue(rows.all { it.type == InteractionType.VIDEO_CALL && it.note == "Long one" && it.batchId == "c1" })
        assertNull(g.loggedMeetups.pending.value)
    }

    @Test
    fun editModeOnAGoneEntryCloses() = runViewModelTest {
        val vm = vm(prefill = LogPrefill(edit = TimelineEntryRef(ana, "hangout-nope")))
        assertTrue(collecting(vm.state).value.done)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
    }

    @Test
    fun eachRemoteKindWritesAnInteraction_withNoPlaceOrActivity() = runViewModelTest {
        val expected = mapOf(
            LogKind.CALL to InteractionType.CALL,
            LogKind.VIDEO_CALL to InteractionType.VIDEO_CALL,
            LogKind.MESSAGE to InteractionType.MESSAGE,
            LogKind.LONG_CHAT to InteractionType.LONG_CHAT,
            LogKind.GAME to InteractionType.GAME,
            LogKind.OTHER to InteractionType.OTHER,
        )
        expected.forEach { (kind, type) ->
            val vm = vm()
            val state = collecting(vm.state)
            vm.onOpenDetails()
            vm.onPlaceName("Time Out")
            vm.onActivity(ActivityChoice.BuiltIn(HangoutActivity.LUNCH))
            vm.onKind(kind)
            vm.onNote("About the move")
            vm.onSave()
            assertTrue(state.value.done)
            val row = g.interactionRepo.interactions.value.last()
            assertEquals(type, row.type)
            assertEquals(ana, row.friendId)
            assertEquals("About the move", row.note)
        }
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertEquals(6, g.interactionRepo.interactions.value.size)
    }

    @Test
    fun yourOwnHowOption_logsAnOtherWithItsName_andLeavingItClearsTheName() = runViewModelTest {
        // Issue #152: your own How options come after the built-in ones turned on, and need no label.
        g.setLogKinds(listOf(LogKind.MEETUP, LogKind.CALL, LogKind.OTHER))
        g.customLogKinds.add("Voice note")
        val vm = vm()
        val state = collecting(vm.state)
        val voice = LogKindOption.custom("Voice note")
        assertEquals(listOf(LogKindOption(LogKind.MEETUP), LogKindOption(LogKind.CALL), LogKindOption(LogKind.OTHER), voice), state.value.kinds)

        vm.onKind(voice)
        assertEquals(voice, state.value.selected)
        assertEquals(LogKind.OTHER, state.value.sheet.kind)
        // Back to the built-in Other: the name goes, so it asks what it was.
        vm.onKind(LogKindOption(LogKind.OTHER))
        assertEquals(LogKindOption(LogKind.OTHER), state.value.selected)
        assertEquals("", state.value.sheet.label)
        vm.onKind(voice)
        vm.onSave()
        val row = g.interactionRepo.interactions.value.single()
        assertEquals(InteractionType.OTHER, row.type)
        assertEquals("Voice note", row.label)
    }

    @Test
    fun editingAnEntryLoggedWithYourOwnOption_showsThatOption() = runViewModelTest {
        g.customLogKinds.add("Letter")
        g.interactionRepo.addAll(listOf(Interaction(ana, InteractionType.OTHER, g.clock.now() - 2.days, id = "o1", label = "letter")))
        val vm = vm(prefill = LogPrefill(edit = TimelineEntryRef(ana, "interaction-o1")))
        val state = collecting(vm.state)
        assertEquals(LogKindOption.custom("Letter"), state.value.selected)
    }

    @Test
    fun otherTakesAShortLabel_thatOnlyOtherKeeps() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onKind(LogKind.OTHER)
        vm.onLabel("  Sent a postcard  ")
        assertEquals("  Sent a postcard  ", state.value.sheet.label)
        assertEquals(LogKind.OTHER, state.value.sheet.kind)
        vm.onSave()
        assertEquals("Sent a postcard", g.interactionRepo.interactions.value.single().label)

        val call = vm()
        collecting(call.state)
        call.onKind(LogKind.OTHER)
        call.onLabel("Board games")
        call.onKind(LogKind.CALL)
        call.onSave()
        assertEquals("", g.interactionRepo.interactions.value.last().label)
    }

    @Test
    fun aRemoteCatchUpGetsTheUndoSnackbarToo() = runViewModelTest {
        val panel = vm()
        collecting(panel.state)
        panel.onKind(LogKind.LONG_CHAT)
        panel.onSave()
        val snackbar = LoggedMeetupViewModel(g.loggedMeetups, g.undoLog)
        val logged = snackbar.pending.value!!
        assertEquals(listOf(ana), logged.people.map { it.id })
        snackbar.onUndo(logged)
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
    }

    @Test
    fun itHappenedOnACall_closesThePlan_andUndoReopensIt() = runViewModelTest {
        val plan = g.savePlan(null, "Catch up", listOf(ana), LocalDate(2026, 3, 6))
        val panel = vm(plan = PlanOccurrence(plan.id, plan.date))
        collecting(panel.state)
        panel.onKind(LogKind.VIDEO_CALL)
        panel.onSave()
        assertEquals(InteractionType.VIDEO_CALL, g.interactionRepo.interactions.value.single().type)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertEquals(PlanStatus.HAPPENED, g.planRepo.plans.value.single().status)

        val snackbar = LoggedMeetupViewModel(g.loggedMeetups, g.undoLog)
        snackbar.onUndo(snackbar.pending.value!!)
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)
    }
}
