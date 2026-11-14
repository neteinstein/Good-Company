package com.neteinstein.goodcompany.feature.agenda.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AnswerLastSeenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.HideLastSeenCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenAnswer
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenCard
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveLastSeenCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveStoryBubblesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveYearReviewCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.StoryBubble
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** One-off feedback for Today's snackbar; the screen turns it into localised text. */
sealed interface TodayMessage {
    data object ReminderSaved : TodayMessage

    data class CallLogged(val name: String) : TodayMessage

    data class Snoozed(val name: String, val until: LocalDate) : TodayMessage

    /** The "I'm with…" check-in was saved with these people (first names). */
    data class CheckedIn(val names: List<String>) : TodayMessage

    data object PlanClosed : TodayMessage

    data object PlanSkipped : TodayMessage

    data class AiError(val error: Throwable) : TodayMessage

    /** The last-seen card stored [answer] for [id] (issue #68); Undo puts back [previous]. */
    data class LastSeenAnswered(val id: FriendId, val name: String, val answer: LastSeenAnswer, val previous: LocalDate?) : TodayMessage
}

/**
 * Today (issue #65): what needs you this week, without listing everyone. The "I'm with…" check-in, the next
 * [WEEK_DAYS] days of the agenda, at most [MAX_CATCH_UPS] catch-ups (the rest on the full agenda), "Before you meet"
 * and who is drifting apart.
 */
data class TodayUiState(
    /** Agenda items in the next 7 days (today included); overdue catch-ups are in [catchUp] instead. */
    val week: List<AgendaItem> = emptyList(),
    /** At most [MAX_CATCH_UPS] people you're overdue to see, most overdue first. */
    val catchUp: List<Relationships.MeetupStatus> = emptyList(),
    /** Everyone overdue; "All N" shows when it is more than [catchUp]. */
    val catchUpTotal: Int = 0,
    /** "Before you meet": people you're seeing today or tomorrow, each with a catch-up story. */
    val stories: List<StoryBubble> = emptyList(),
    /** Real friends whose warmth is fading and who aren't overdue already (at most three). */
    val drifting: List<FriendSummary> = emptyList(),
    /** Everyone, for the reminder editor and the birthday-wish sheet. */
    val friends: List<Friend> = emptyList(),
    val isLoading: Boolean = true,
    val showReminderEditor: Boolean = false,
    val message: TodayMessage? = null,
    /** AI is on and a key is saved (F1): every AI button checks this. */
    val hasAiKey: Boolean = false,
    /** Friend whose birthday-wish sheet is open. */
    val wishFor: Friend? = null,
    val wishDraft: String? = null,
    val isDraftingWish: Boolean = false,
    /** "Your year in friendship" in December (issue #67), else null. */
    val reviewYear: Int? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
    /** "When did you last see…?" (issue #68): the person asked about now; null hides the card. */
    val lastSeenAsk: Friend? = null,
    /** Where [lastSeenAsk] is in this round ("1 of 12"): answers given since Today opened, plus one. */
    val lastSeenPosition: Int = 0,
    /** Answers given since Today opened plus everyone still to ask. */
    val lastSeenTotal: Int = 0,
    /** Answers given since Today opened (Undo takes one back). */
    val lastSeenAnswered: Int = 0,
) {
    /** There is someone to check in with: the "I'm with…" card shows. */
    val showCheckIn: Boolean get() = friends.isNotEmpty()

    /** No people yet: Today points to the People tab instead. */
    val isEmpty: Boolean get() = !isLoading && friends.isEmpty()

    /** [week] grouped by day, in order. */
    val weekByDay: List<Pair<LocalDate, List<AgendaItem>>> get() = week.groupBy { it.date }.toList()

    companion object {
        const val WEEK_DAYS = 7
        const val MAX_CATCH_UPS = 3
        const val MAX_DRIFTING = 3
    }
}

class TodayViewModel(
    observeAgenda: ObserveAgendaUseCase,
    observeMeetups: ObserveMeetupStatusUseCase,
    observeStories: ObserveStoryBubblesUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeReviewYear: ObserveYearReviewCardUseCase,
    observeLastSeen: ObserveLastSeenCardUseCase,
    private val addReminder: AddReminderUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val logCall: LogCallUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val answerLastSeen: AnswerLastSeenUseCase,
    private val hideLastSeenCard: HideLastSeenCardUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(TodayUiState())

    val state: StateFlow<TodayUiState> = combine(
        observeAgenda(),
        observeMeetups(),
        observeStories(),
        combine(observeFriends(), observeSettings(), observeReviewYear(), observeLastSeen(), ::Extras),
        local,
    ) { agenda, meetups, stories, extras, ui ->
        build(ui, agenda, meetups, stories, extras.friends, extras.settings).copy(reviewYear = extras.reviewYear).withLastSeen(extras.lastSeen)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun build(
        ui: TodayUiState,
        agenda: List<AgendaItem>,
        meetups: List<Relationships.MeetupStatus>,
        stories: List<StoryBubble>,
        friends: List<FriendSummary>,
        settings: AppSettings,
    ): TodayUiState {
        val today = clock.today()
        val lastDay = today.plus(TodayUiState.WEEK_DAYS - 1, DateTimeUnit.DAY)
        val overdue = meetups.filter { it.isOverdue(today) }.sortedBy { it.dueDate }
        val overdueIds = overdue.map { it.friend.id }.toSet()
        return ui.copy(
            week = agenda.filter { it !is AgendaItem.MeetupOverdue && it.date <= lastDay },
            catchUp = overdue.take(TodayUiState.MAX_CATCH_UPS),
            catchUpTotal = overdue.size,
            stories = stories,
            // The same rule as Home's "Drifting apart": real friends only, softer view off, not snoozed.
            drifting = friends
                .filter { settings.showWarmth }
                .filter { it.friend.isCloseFriend && !it.friend.catchUpPaused }
                .filter { it.warmth.level == Warmth.Level.FADING && it.friend.id !in overdueIds }
                .filter { s -> s.friend.snoozedUntil?.let { it < today } ?: true }
                .sortedBy { it.warmth.value }
                .take(TodayUiState.MAX_DRIFTING),
            friends = friends.map { it.friend },
            hasAiKey = settings.usableAiKey != null,
            isLoading = false,
            today = today,
        )
    }

    private class Extras(val friends: List<FriendSummary>, val settings: AppSettings, val reviewYear: Int?, val lastSeen: LastSeenCard)

    private fun TodayUiState.withLastSeen(card: LastSeenCard): TodayUiState {
        val ask = card.current
        return copy(
            lastSeenAsk = ask,
            lastSeenPosition = if (ask == null) 0 else lastSeenAnswered + 1,
            lastSeenTotal = if (ask == null) 0 else lastSeenAnswered + card.pending.size,
        )
    }

    /** An answer on the last-seen card: stores the approximate date and moves on to the next person, with Undo. */
    fun onLastSeenAnswer(id: FriendId, answer: LastSeenAnswer) {
        viewModelScope.launch {
            val previous = state.value.friends.firstOrNull { it.id == id }?.approxLastSeen
            answerLastSeen(id, answer) ?: return@launch
            local.update {
                it.copy(
                    lastSeenAnswered = it.lastSeenAnswered + 1,
                    message = TodayMessage.LastSeenAnswered(id, firstName(id), answer, previous),
                )
            }
        }
    }

    /** Undo on the answer's snackbar: the person is asked again. */
    fun onUndoLastSeen(message: TodayMessage.LastSeenAnswered) {
        viewModelScope.launch {
            answerLastSeen.undo(message.id, message.previous)
            local.update { it.copy(lastSeenAnswered = (it.lastSeenAnswered - 1).coerceAtLeast(0), message = null) }
        }
    }

    /** "Not now": the card goes away until tomorrow. */
    fun onLastSeenNotNow() {
        viewModelScope.launch { hideLastSeenCard() }
    }

    private fun firstName(id: FriendId): String = state.value.friends.firstOrNull { it.id == id }?.displayName?.substringBefore(' ').orEmpty()

    fun onNewReminder() = local.update { it.copy(showReminderEditor = true) }

    fun onDismissReminderEditor() = local.update { it.copy(showReminderEditor = false) }

    /** [date] null = no due date. [hour] is local time. */
    fun onSaveReminder(title: String, friendIds: List<FriendId>, date: LocalDate?, hour: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val due = date?.atTime(LocalTime(hour.coerceIn(0, 23), 0))?.toInstant(clock.timeZone)
            addReminder(title, friendIds, due)
            local.update { it.copy(showReminderEditor = false, message = TodayMessage.ReminderSaved) }
        }
    }

    fun onCalled(id: FriendId) {
        viewModelScope.launch {
            logCall(id)
            local.update { it.copy(message = TodayMessage.CallLogged(firstName(id))) }
        }
    }

    fun onSnooze(id: FriendId, option: SnoozeOption) {
        viewModelScope.launch {
            snooze.friend(id, option)
            local.update { it.copy(message = TodayMessage.Snoozed(firstName(id), option.until(clock.today()))) }
        }
    }

    /** The "I'm with…" sheet saved a meetup with these people. */
    fun onCheckedIn(names: List<String>) = local.update { it.copy(message = TodayMessage.CheckedIn(names)) }

    /** "Did you meet?" → no (a yes opens the "We met" panel). */
    fun onPlanDidNotHappen(item: AgendaItem.PlanToConfirm) {
        viewModelScope.launch {
            confirmPlan(item.plan.id, happened = false, day = item.occurrence)
            local.update { it.copy(message = TodayMessage.PlanClosed) }
        }
    }

    /** "Skip this one": takes one date out of a recurring plan; the series goes on. */
    fun onSkipPlan(item: AgendaItem.PlanUpcoming) {
        viewModelScope.launch {
            confirmPlan(item.plan.id, happened = false, day = item.occurrence)
            local.update { it.copy(message = TodayMessage.PlanSkipped) }
        }
    }

    fun onOpenWish(id: FriendId) {
        val friend = state.value.friends.firstOrNull { it.id == id } ?: return
        local.update { it.copy(wishFor = friend, wishDraft = null) }
    }

    fun onDismissWish() = local.update { it.copy(wishFor = null, wishDraft = null) }

    fun onPersonaliseWish() {
        val friend = local.value.wishFor ?: return
        if (local.value.isDraftingWish || !state.value.hasAiKey) return
        viewModelScope.launch {
            local.update { it.copy(isDraftingWish = true) }
            val result = draftBirthdayMessage(friend.id)
            local.update {
                result.fold(
                    { text -> it.copy(isDraftingWish = false, wishDraft = text) },
                    { e -> it.copy(isDraftingWish = false, message = TodayMessage.AiError(e)) },
                )
            }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
