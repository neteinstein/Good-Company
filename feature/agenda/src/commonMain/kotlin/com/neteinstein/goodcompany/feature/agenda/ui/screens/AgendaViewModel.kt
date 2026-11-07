package com.neteinstein.goodcompany.feature.agenda.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
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
import kotlinx.datetime.toInstant

data class AgendaUiState(
    val agenda: List<AgendaItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val isLoading: Boolean = true,
    val showReminderEditor: Boolean = false,
    val message: String? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
) {
    /** Agenda grouped by day, preserving order. */
    val agendaByDay: List<Pair<LocalDate, List<AgendaItem>>>
        get() = agenda.groupBy { it.date }.toList()
}

class AgendaViewModel(
    observeAgenda: ObserveAgendaUseCase,
    observeReminders: ObserveRemindersUseCase,
    observeFriends: ObserveFriendsUseCase,
    private val addReminder: AddReminderUseCase,
    private val setReminderDone: SetReminderDoneUseCase,
    private val deleteReminder: DeleteReminderUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(AgendaUiState())

    val state: StateFlow<AgendaUiState> = combine(observeAgenda(), observeReminders(), observeFriends(), local) { agenda, reminders, friends, ui ->
        ui.copy(
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
            local.update { it.copy(showReminderEditor = false, message = "Reminder saved") }
        }
    }

    fun onToggleDone(reminder: Reminder) {
        viewModelScope.launch { setReminderDone(reminder, !reminder.done) }
    }

    fun onDeleteReminder(id: String) {
        viewModelScope.launch { deleteReminder(id) }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
