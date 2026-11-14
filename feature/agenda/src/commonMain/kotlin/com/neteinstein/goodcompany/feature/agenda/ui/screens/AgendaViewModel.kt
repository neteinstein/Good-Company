package com.neteinstein.goodcompany.feature.agenda.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetReminderDoneUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface AgendaMessage {
    data object ReminderSaved : AgendaMessage

    data object ReminderDeleted : AgendaMessage

    data class AiError(val error: Throwable) : AgendaMessage

    data object PlanLogged : AgendaMessage

    data object PlanClosed : AgendaMessage
}

data class AgendaUiState(
    val agenda: List<AgendaItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val isLoading: Boolean = true,
    val showReminderEditor: Boolean = false,
    val recentlyDeleted: Reminder? = null,
    val message: AgendaMessage? = null,
    val hasAiKey: Boolean = false,
    /** Friend whose birthday-wish sheet is open. */
    val wishFor: Friend? = null,
    val wishDraft: String? = null,
    val isDraftingWish: Boolean = false,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
) {
    /** Agenda grouped by day, preserving order. */
    val agendaByDay: List<Pair<LocalDate, List<AgendaItem>>>
        get() = agenda.groupBy { it.date }.toList()

    /** Next 7 days with how many agenda items fall on each — the week strip. */
    val week: List<Pair<LocalDate, Int>>
        get() = (0 until 7).map { offset ->
            val day = today.plus(offset, kotlinx.datetime.DateTimeUnit.DAY)
            day to agenda.count { it.date == day }
        }

    val openReminders: Int get() = reminders.count { !it.done }
}

class AgendaViewModel(
    observeAgenda: ObserveAgendaUseCase,
    observeReminders: ObserveRemindersUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val addReminder: AddReminderUseCase,
    private val setReminderDone: SetReminderDoneUseCase,
    private val deleteReminder: DeleteReminderUseCase,
    private val restoreReminder: RestoreReminderUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(AgendaUiState())

    val state: StateFlow<AgendaUiState> = combine(
        observeAgenda(),
        observeReminders(),
        observeFriends(),
        observeSettings(),
        local,
    ) { agenda, reminders, friends, settings, ui ->
        ui.copy(
            hasAiKey = settings.hasAiKey,
            agenda = agenda,
            reminders = reminders,
            friends = friends.map { it.friend },
            isLoading = false,
            today = clock.today(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    fun onNewReminder() = local.update { it.copy(showReminderEditor = true) }

    fun onDismissReminderEditor() = local.update { it.copy(showReminderEditor = false) }

    /** [date] null = no due date. [hour] is local time. */
    fun onSaveReminder(title: String, friendIds: List<FriendId>, date: LocalDate?, hour: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val due = date?.atTime(LocalTime(hour.coerceIn(0, 23), 0))?.toInstant(clock.timeZone)
            addReminder(title, friendIds, due)
            local.update { it.copy(showReminderEditor = false, message = AgendaMessage.ReminderSaved) }
        }
    }

    fun onToggleDone(reminder: Reminder) {
        viewModelScope.launch { setReminderDone(reminder, !reminder.done) }
    }

    fun onDeleteReminder(id: String) {
        val reminder = state.value.reminders.firstOrNull { it.id == id }
        viewModelScope.launch {
            deleteReminder(id)
            local.update { it.copy(recentlyDeleted = reminder, message = AgendaMessage.ReminderDeleted) }
        }
    }

    fun onUndoDelete() {
        val reminder = local.value.recentlyDeleted ?: return
        viewModelScope.launch {
            restoreReminder(reminder)
            local.update { it.copy(recentlyDeleted = null) }
        }
    }

    fun onOpenWish(id: FriendId) {
        val friend = state.value.friends.firstOrNull { it.id == id } ?: return
        local.update { it.copy(wishFor = friend, wishDraft = null) }
    }

    fun onDismissWish() = local.update { it.copy(wishFor = null, wishDraft = null) }

    fun onPersonaliseWish() {
        val friend = local.value.wishFor ?: return
        if (local.value.isDraftingWish) return
        viewModelScope.launch {
            local.update { it.copy(isDraftingWish = true) }
            val result = draftBirthdayMessage(friend.id)
            local.update {
                result.fold(
                    { text -> it.copy(isDraftingWish = false, wishDraft = text) },
                    { e -> it.copy(isDraftingWish = false, message = AgendaMessage.AiError(e)) },
                )
            }
        }
    }

    /** "Did you meet?" answered from the agenda. A yes from the screen opens the "We met" panel instead (issue #47). */
    fun onConfirmPlan(id: String, happened: Boolean) {
        viewModelScope.launch {
            confirmPlan(id, happened)
            local.update { it.copy(message = if (happened) AgendaMessage.PlanLogged else AgendaMessage.PlanClosed) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, recentlyDeleted = null) }
}
