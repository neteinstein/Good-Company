package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/**
 * What the home-screen widget shows: the next thing coming up (birthday, big day, plan, reminder) and the friend
 * most due a catch-up. [isPrivate] hides names when the user asked for privacy (app lock or private notifications).
 */
data class HomeWidgetSummary(
    val next: AgendaItem?,
    val catchUp: Relationships.MeetupStatus?,
    val isPrivate: Boolean,
    val today: LocalDate,
)

object HomeWidget {
    fun summarize(
        agenda: List<AgendaItem>,
        meetups: List<Relationships.MeetupStatus>,
        today: LocalDate,
        isPrivate: Boolean,
    ): HomeWidgetSummary = HomeWidgetSummary(
        next = agenda.firstOrNull { it.isComingUp() },
        catchUp = meetups.filter { it.isOverdue(today) }.minByOrNull { it.dueDate },
        isPrivate = isPrivate,
        today = today,
    )

    private fun AgendaItem.isComingUp() = when (this) {
        is AgendaItem.FriendBirthday, is AgendaItem.KidBirthday, is AgendaItem.FriendEvent,
        is AgendaItem.ReminderDue, is AgendaItem.PlanUpcoming,
        -> true
        is AgendaItem.MeetupOverdue, is AgendaItem.GroupOverdue, is AgendaItem.PlanToConfirm -> false
    }
}

class ObserveHomeWidgetUseCase(
    private val observeAgenda: ObserveAgendaUseCase,
    private val observeMeetups: ObserveMeetupStatusUseCase,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<HomeWidgetSummary> = combine(observeAgenda(), observeMeetups(), settings.settings) { agenda, meetups, s ->
        HomeWidget.summarize(agenda, meetups, clock.today(), isPrivate = s.appLock || s.privateNotifications)
    }
}
