package com.neteinstein.goodcompany.feature.agenda.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.usecase.CalendarSuggestion
import com.neteinstein.goodcompany.core.domain.usecase.DecideCalendarSuggestionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarSuggestionsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UndoCalendarDecisionUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

sealed interface CalendarSuggestionsMessage {
    /** "No" or "Never for …" was saved; the snackbar offers Undo. */
    data class Decided(val decision: CalendarDecision, val title: String) : CalendarSuggestionsMessage
}

data class CalendarSuggestionsUiState(
    val loading: Boolean = true,
    /** Ended since suggestions were turned on, newest first. */
    val recent: List<CalendarSuggestion> = emptyList(),
    /** Ended before suggestions were turned on (F2's look-back). */
    val lookingBack: List<CalendarSuggestion> = emptyList(),
    val message: CalendarSuggestionsMessage? = null,
    val zone: TimeZone = TimeZone.UTC,
) {
    val isEmpty: Boolean get() = recent.isEmpty() && lookingBack.isEmpty()
}

/**
 * "From your calendar" (D3): past events with friends, each answered **Log it** (the screen opens the log sheet with
 * the people, the day and a guessed activity; once logged the suggestion goes away by itself), **No**, or **Never for
 * this series** (repeating events). Answers are kept, with Undo.
 */
class CalendarSuggestionsViewModel(
    observeSuggestions: ObserveCalendarSuggestionsUseCase,
    private val decide: DecideCalendarSuggestionUseCase,
    private val undo: UndoCalendarDecisionUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val message = MutableStateFlow<CalendarSuggestionsMessage?>(null)

    val state: StateFlow<CalendarSuggestionsUiState> = combine(observeSuggestions(), message) { all, m ->
        CalendarSuggestionsUiState(
            loading = false,
            recent = all.filterNot { it.lookingBack },
            lookingBack = all.filter { it.lookingBack },
            message = m,
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarSuggestionsUiState(zone = clock.timeZone))

    fun onNo(suggestion: CalendarSuggestion) = answer(suggestion, CalendarDecisionKind.NO)

    fun onNever(suggestion: CalendarSuggestion) = answer(suggestion, CalendarDecisionKind.NEVER)

    private fun answer(suggestion: CalendarSuggestion, kind: CalendarDecisionKind) {
        viewModelScope.launch {
            decide(suggestion, kind)?.let { message.value = CalendarSuggestionsMessage.Decided(it, suggestion.event.title) }
        }
    }

    fun onUndo() {
        val decided = message.value as? CalendarSuggestionsMessage.Decided ?: return
        message.update { null }
        viewModelScope.launch { undo(decided.decision) }
    }

    fun onMessageShown() {
        message.value = null
    }
}
