package org.goodcompany.app.feature.settings

import org.goodcompany.app.core.domain.model.DeviceCalendar
import org.goodcompany.app.core.domain.usecase.ObserveCalendarSettingsUseCase
import org.goodcompany.app.core.testing.FakeCalendarReader
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.feature.settings.ui.screens.calendar.CalendarSettingsMessage
import org.goodcompany.app.feature.settings.ui.screens.calendar.CalendarSettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarSettingsViewModelTest {
    private val g = TestGraph().apply {
        calendarReader.calendars = listOf(DeviceCalendar("personal", "Personal", "iCloud"), DeviceCalendar("work", "Work"))
    }

    private fun vm(settings: ObserveCalendarSettingsUseCase = g.observeCalendarSettings) =
        CalendarSettingsViewModel(settings, g.observeCalendarSuggestions, g.setCalendarSuggestions, g.setCalendarChosen)

    @Test
    fun offByDefaultThenOnWithNothingTicked() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.enabled)
        assertTrue(state.value.calendars.isEmpty())
        vm.onPermissionResult(granted = true)
        assertTrue(state.value.enabled)
        assertEquals(listOf("personal", "work"), state.value.calendars.map { it.calendar.id })
        assertTrue(state.value.calendars.none { it.chosen })
        vm.onChosen("personal", true)
        assertEquals(listOf(true, false), state.value.calendars.map { it.chosen })
        vm.onEnabled(false)
        assertFalse(g.settingsRepo.settings.value.calendarSuggestions)
    }

    @Test
    fun refusedAccessStaysOffWithAMessage() = runViewModelTest {
        g.calendarReader.access = false
        val vm = vm()
        val state = collecting(vm.state)
        vm.onPermissionResult(granted = false)
        assertEquals(CalendarSettingsMessage.AccessDenied, state.value.message)
        assertFalse(state.value.enabled)
        vm.onMessageShown()
        assertNull(state.value.message)
    }

    @Test
    fun onWithoutAccessAsksForIt() = runViewModelTest {
        g.calendarReader.access = false
        g.setCalendarSuggestions(true)
        val state = collecting(vm().state)
        assertTrue(state.value.needsAccess)
    }

    @Test
    fun theWebSaysItIsNotSupported() = runViewModelTest {
        val web = ObserveCalendarSettingsUseCase(g.settingsRepo, FakeCalendarReader(isSupported = false))
        assertFalse(collecting(vm(web).state).value.supported)
    }
}
