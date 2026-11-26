package com.neteinstein.goodcompany.feature.agenda.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.AnswerCalendarQuestionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CalendarQuestion
import com.neteinstein.goodcompany.core.domain.usecase.CalendarSuggestion
import com.neteinstein.goodcompany.core.domain.usecase.DecideCalendarSuggestionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarQuestionsUseCase
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

sealed interface CalendarSuggestionsMessage {
    /** The decision that Undo takes back. */
    val decision: CalendarDecision

    /** "No" or "Never for …" was saved; the snackbar offers Undo. */
    data class Decided(override val decision: CalendarDecision, val title: String) : CalendarSuggestionsMessage

    /** "Who's Ana?" was answered with [names] (empty = none of them); Undo asks again. */
    data class Answered(override val decision: CalendarDecision, val title: String, val names: List<String>) : CalendarSuggestionsMessage
}

data class CalendarSuggestionsUiState(
    val loading: Boolean = true,
    /** Ended since suggestions were turned on, newest first. */
    val recent: List<CalendarSuggestion> = emptyList(),
    /** Ended before suggestions were turned on (F2's look-back). */
    val lookingBack: List<CalendarSuggestion> = emptyList(),
    /** "Who's Ana?" (2026-11-26): the open question shown above the list, if any. */
    val question: CalendarQuestion? = null,
    val message: CalendarSuggestionsMessage? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
) {
    val isEmpty: Boolean get() = recent.isEmpty() && lookingBack.isEmpty() && question == null
}

/**
 * "From your calendar" (D3): past events with friends, each answered **Log it** (the screen opens the Log popup with
 * the people, the day and a guessed activity; once logged the suggestion goes away by itself), **No**, or **Never for
 * this series** (repeating events). Answers are kept, with Undo. An event naming a first name several people share asks
 * "Who's Ana?" above the list first (decision 2026-11-26).
 */
class CalendarSuggestionsViewModel(
    observeSuggestions: ObserveCalendarSuggestionsUseCase,
    observeQuestions: ObserveCalendarQuestionsUseCase,
    private val decide: DecideCalendarSuggestionUseCase,
    private val answerQuestion: AnswerCalendarQuestionUseCase,
    private val undo: UndoCalendarDecisionUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val message = MutableStateFlow<CalendarSuggestionsMessage?>(null)

    /** Questions put off with "Not now" while this screen is open. */
    private val notNow = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<CalendarSuggestionsUiState> = combine(observeSuggestions(), observeQuestions(), notNow, message) { all, qs, skipped, m ->
        CalendarSuggestionsUiState(
            loading = false,
            recent = all.filterNot { it.lookingBack },
            lookingBack = all.filter { it.lookingBack },
            question = qs.firstOrNull { it.key !in skipped },
            message = m,
            today = clock.today(),
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

    /** "Who's Ana?" answered: [friendIds] are the ones it is (empty = none of them). */
    fun onAnswerQuestion(question: CalendarQuestion, friendIds: Set<FriendId>) {
        viewModelScope.launch {
            val decision = answerQuestion(question, friendIds)
            val names = question.candidates.filter { it.id in decision.friendIds }.map { it.displayName }
            message.value = CalendarSuggestionsMessage.Answered(decision, question.event.title, names)
        }
    }

    fun onQuestionNotNow(question: CalendarQuestion) = notNow.update { it + question.key }

    fun onUndo() {
        val decided = message.value ?: return
        message.update { null }
        viewModelScope.launch { undo(decided.decision) }
    }

    fun onMessageShown() {
        message.value = null
    }
}
