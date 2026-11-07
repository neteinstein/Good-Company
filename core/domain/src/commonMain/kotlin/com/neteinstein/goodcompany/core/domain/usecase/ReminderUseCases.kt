package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class ObserveRemindersUseCase(private val reminders: ReminderRepository) {
    /** Open first (soonest due first, undated last), then done. */
    operator fun invoke(): Flow<List<Reminder>> = reminders.reminders.map { list ->
        list.sortedWith(
            compareBy<Reminder> { it.done }
                .thenBy { it.dueAt == null }
                .thenBy { it.dueAt }
                .thenByDescending { it.createdAt },
        )
    }
}

class AddReminderUseCase(
    private val reminders: ReminderRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(title: String, friendIds: List<FriendId>, dueAt: Instant?): Reminder {
        require(title.isNotBlank()) { "Title required" }
        val reminder = Reminder(ids.next(), title.trim(), friendIds.distinct(), dueAt, done = false, createdAt = clock.now())
        reminders.upsert(reminder)
        return reminder
    }
}

class SetReminderDoneUseCase(private val reminders: ReminderRepository) {
    suspend operator fun invoke(reminder: Reminder, done: Boolean) = reminders.upsert(reminder.copy(done = done))
}

class DeleteReminderUseCase(private val reminders: ReminderRepository) {
    suspend operator fun invoke(id: String) = reminders.delete(id)
}

/** Puts back a reminder deleted a moment ago (snackbar "Undo"). */
class RestoreReminderUseCase(private val reminders: ReminderRepository) {
    suspend operator fun invoke(reminder: Reminder) = reminders.upsert(reminder)
}

class ObserveAgendaUseCase(
    private val friends: FriendRepository,
    private val reminders: ReminderRepository,
    private val hangouts: HangoutRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<AgendaItem>> =
        combine(friends.friends, reminders.reminders, hangouts.hangouts) { fs, rs, hs ->
            AgendaBuilder.build(fs, rs, hs, clock.today(), clock.timeZone)
        }
}

/**
 * Keeps the OS notification schedule in sync with the data: re-plans whenever friends, reminders,
 * hangouts or settings change and hands the result to the platform scheduler. Runs until cancelled.
 */
class KeepNotificationsScheduledUseCase(
    private val friends: FriendRepository,
    private val reminders: ReminderRepository,
    private val hangouts: HangoutRepository,
    private val settings: SettingsRepository,
    private val scheduler: NotificationScheduler,
    private val clock: AppClock,
) {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend operator fun invoke() {
        combine(friends.friends, reminders.reminders, hangouts.hangouts, settings.settings) { fs, rs, hs, s ->
            NotificationPlanner.plan(fs, rs, hs, clock.now(), clock.timeZone, s.reminderHour)
        }
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { scheduler.replaceAll(it) }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
