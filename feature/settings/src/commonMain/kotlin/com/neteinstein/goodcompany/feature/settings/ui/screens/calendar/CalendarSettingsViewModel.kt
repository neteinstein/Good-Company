package com.neteinstein.goodcompany.feature.settings.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.usecase.CalendarSettings
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarSuggestionsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCalendarChosenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCalendarSuggestionsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CalendarSettingsMessage {
    /** The system refused calendar access, so suggestions stay off. */
    data object AccessDenied : CalendarSettingsMessage
}

data class CalendarRow(val calendar: DeviceCalendar, val chosen: Boolean)

data class CalendarSettingsUiState(
    val loading: Boolean = true,
    /** False on the web: browsers can't read the device calendar. */
    val supported: Boolean = true,
    val enabled: Boolean = false,
    /** On, but the permission is missing (refused, or revoked in system settings). */
    val needsAccess: Boolean = false,
    val calendars: List<CalendarRow> = emptyList(),
    /** How many past meetups are waiting in "From your calendar". */
    val suggestionCount: Int = 0,
    val message: CalendarSettingsMessage? = null,
)

/**
 * Settings → Calendar suggestions (D3): the opt-in switch and the calendars the app may read (none ticked at first).
 * The screen asks for the system permission before calling [onEnabled] with true.
 */
class CalendarSettingsViewModel(
    observeSettings: ObserveCalendarSettingsUseCase,
    observeSuggestions: ObserveCalendarSuggestionsUseCase,
    private val setEnabled: SetCalendarSuggestionsUseCase,
    private val setChosen: SetCalendarChosenUseCase,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val message = MutableStateFlow<CalendarSettingsMessage?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val settings = refresh.flatMapLatest { observeSettings() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val count = refresh.flatMapLatest { observeSuggestions().map { it.size } }

    val state: StateFlow<CalendarSettingsUiState> = combine(settings, count, message) { s, n, m -> s.toUi(n, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarSettingsUiState())

    private fun CalendarSettings.toUi(count: Int, message: CalendarSettingsMessage?) = CalendarSettingsUiState(
        loading = false,
        supported = supported,
        enabled = enabled,
        needsAccess = enabled && !hasAccess,
        calendars = calendars.map { CalendarRow(it, it.id in chosen) },
        suggestionCount = count,
        message = message,
    )

    fun onEnabled(enabled: Boolean) {
        viewModelScope.launch { setEnabled(enabled) }
    }

    /** The answer to the system prompt the screen showed after the switch was turned on (or "Allow access"). */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch {
                setEnabled(true)
                refresh.update { it + 1 }
            }
        } else {
            message.value = CalendarSettingsMessage.AccessDenied
        }
    }

    fun onChosen(calendarId: String, chosen: Boolean) {
        viewModelScope.launch { setChosen(calendarId, chosen) }
    }

    fun onMessageShown() {
        message.value = null
    }
}
