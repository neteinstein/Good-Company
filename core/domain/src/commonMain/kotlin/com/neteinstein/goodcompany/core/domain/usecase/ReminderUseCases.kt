package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.NotificationSettings
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ReminderRepeat
import com.neteinstein.goodcompany.core.domain.model.ReminderSnooze
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification
import com.neteinstein.goodcompany.core.domain.repository.CalendarDecisionRepository
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.NotificationRenderer
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ObserveRemindersUseCase(private val reminders: ReminderRepository) {
    /** Open first (soonest due first, undated last), then done. */
    operator fun invoke(): Flow<List<Reminder>> = reminders.reminders.map { list ->
        list.sortedWith(
            compareBy<Reminder> { it.done }
                .thenBy { it.nextAt == null }
                .thenBy { it.nextAt }
                .thenByDescending { it.completedAt }
                .thenByDescending { it.createdAt },
        )
    }
}

class AddReminderUseCase(
    private val reminders: ReminderRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    /**
     * A new reminder about [friendIds] and/or the circle [circleId] (issue #88: it then belongs to the circle). A
     * [repeat] needs a date; without one it is saved as not repeating.
     */
    suspend operator fun invoke(
        title: String,
        friendIds: List<FriendId>,
        dueAt: Instant?,
        circleId: String? = null,
        repeat: ReminderRepeat = ReminderRepeat.NONE,
        important: Boolean = false,
    ): Reminder {
        require(title.isNotBlank()) { "Title required" }
        val reminder = Reminder(
            ids.next(),
            title.trim(),
            friendIds.distinct(),
            dueAt,
            done = false,
            createdAt = clock.now(),
            circleId = circleId,
            important = important,
            repeat = if (dueAt == null) ReminderRepeat.NONE else repeat,
        )
        reminders.upsert(reminder)
        return reminder
    }
}

/**
 * Changes a reminder's title, people, circle, time, repeat and important flag (issue #99: from everywhere a reminder
 * shows). A new time clears the snooze; the rest of it (done, created) is kept.
 */
class EditReminderUseCase(private val reminders: ReminderRepository) {
    suspend operator fun invoke(
        reminder: Reminder,
        title: String,
        friendIds: List<FriendId>,
        dueAt: Instant?,
        circleId: String?,
        repeat: ReminderRepeat = reminder.repeat,
        important: Boolean = reminder.important,
    ): Reminder {
        require(title.isNotBlank()) { "Title required" }
        val edited = reminder.copy(
            title = title.trim(),
            friendIds = friendIds.distinct(),
            dueAt = dueAt,
            circleId = circleId,
            snoozedUntil = reminder.snoozedUntil.takeIf { dueAt == reminder.dueAt },
            repeat = if (dueAt == null) ReminderRepeat.NONE else repeat,
            important = important,
        )
        reminders.upsert(edited)
        return edited
    }
}

/**
 * "Done" (issue #99). A one-off reminder closes ([Reminder.done], [Reminder.completedAt]); a repeating one rolls to its
 * next date instead, open and unsnoozed. Never asks "did you meet?" and logs nothing, even when it names people.
 * Returns the reminder as saved; null when [id] is unknown (a notification for a deleted reminder).
 */
class CompleteReminderUseCase(private val reminders: ReminderRepository, private val clock: AppClock) {
    suspend operator fun invoke(reminder: Reminder): Reminder {
        val now = clock.now()
        val next = reminder.takeIf { it.isRepeating }?.nextDueAfter(now, clock.timeZone)
        val saved = if (next != null) {
            reminder.copy(dueAt = next, snoozedUntil = null, done = false, completedAt = null)
        } else {
            reminder.copy(done = true, completedAt = now, snoozedUntil = null)
        }
        reminders.upsert(saved)
        return saved
    }

    suspend operator fun invoke(id: String): Reminder? = reminders.reminders.first().firstOrNull { it.id == id }?.let { invoke(it) }
}

/** Takes a done reminder back to the open list (tapping it in the "Done" group). */
class ReopenReminderUseCase(private val reminders: ReminderRepository) {
    suspend operator fun invoke(reminder: Reminder) = reminders.upsert(reminder.copy(done = false, completedAt = null))
}

/**
 * "Snooze" (issue #99): 1 hour, tonight, tomorrow, next week or a picked time. Only [Reminder.snoozedUntil] moves; the
 * due date stays, so a repeating reminder keeps its rhythm. A time in the past is taken as an hour from now.
 */
class SnoozeReminderUseCase(private val reminders: ReminderRepository, private val clock: AppClock) {
    suspend operator fun invoke(reminder: Reminder, snooze: ReminderSnooze): Reminder {
        val saved = reminder.copy(snoozedUntil = until(snooze), done = false, completedAt = null)
        reminders.upsert(saved)
        return saved
    }

    suspend operator fun invoke(id: String, snooze: ReminderSnooze): Reminder? =
        reminders.reminders.first().firstOrNull { it.id == id }?.let { invoke(it, snooze) }

    /** When [snooze] ends, from now. */
    fun until(snooze: ReminderSnooze): Instant {
        val now = clock.now()
        val zone = clock.timeZone
        val today = clock.today()
        fun LocalDate.at(hour: Int) = atTime(LocalTime(hour, 0)).toInstant(zone)
        val at = when (snooze) {
            ReminderSnooze.OneHour -> now + 1.hours
            ReminderSnooze.Tonight -> today.at(TONIGHT_HOUR)
            ReminderSnooze.Tomorrow -> today.plus(1, DateTimeUnit.DAY).at(MORNING_HOUR)
            ReminderSnooze.NextWeek -> today.plus(DAYS_PER_WEEK, DateTimeUnit.DAY).at(MORNING_HOUR)
            is ReminderSnooze.Until -> snooze.at
        }
        return if (at > now) at else now + 1.hours
    }

    /** Whether "Tonight" still makes sense (it is before [TONIGHT_HOUR]). */
    fun tonightAvailable(): Boolean = clock.now() < clock.today().atTime(LocalTime(TONIGHT_HOUR, 0)).toInstant(clock.timeZone)

    companion object {
        const val TONIGHT_HOUR = 20
        const val MORNING_HOUR = 9
        private const val DAYS_PER_WEEK = 7
    }
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
    private val settings: SettingsRepository,
    private val groups: GroupRepository,
    private val plans: PlanRepository,
    private val interactions: InteractionRepository,
    private val links: PersonLinkRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<AgendaItem>> = combine(
        friends.friends,
        reminders.reminders,
        hangouts.hangouts,
        settings.settings,
        combine(groups.groups, plans.plans, interactions.interactions, links.links, ::Inputs),
    ) { fs, rs, hs, s, (gs, ps, ints, ls) ->
        AgendaBuilder.build(
            fs,
            rs,
            hs,
            clock.today(),
            clock.timeZone,
            rules = CadenceRules.from(s),
            groups = gs,
            plans = ps,
            interactions = ints,
            links = ls,
        )
    }
}

/**
 * Keeps the OS notification schedule in sync with the data: re-plans whenever friends, reminders,
 * hangouts or settings change and hands the result to the platform scheduler. Runs until cancelled; a failed
 * re-plan is skipped (the next change tries again) instead of stopping the schedule (issue #98).
 * [once] re-plans a single time, for background triggers (Android's daily job, notification quick actions).
 */
class KeepNotificationsScheduledUseCase(
    private val friends: FriendRepository,
    private val reminders: ReminderRepository,
    private val hangouts: HangoutRepository,
    private val settings: SettingsRepository,
    private val groups: GroupRepository,
    private val plans: PlanRepository,
    private val interactions: InteractionRepository,
    private val links: PersonLinkRepository,
    private val calendarEvents: ObserveCalendarEventsUseCase,
    private val calendarDecisions: CalendarDecisionRepository,
    private val scheduler: NotificationScheduler,
    private val renderer: NotificationRenderer,
    private val clock: AppClock,
) {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend operator fun invoke() {
        schedules()
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { schedule ->
                try {
                    apply(schedule)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One bad re-plan mustn't stop the schedule for the rest of the session.
                    onFailure(e)
                }
            }
    }

    /** Re-plans once from the current data. */
    suspend fun once() = apply(schedules().first())

    /** Called when a re-plan fails; the app logs it. */
    var onFailure: (Throwable) -> Unit = {}

    private suspend fun apply(schedule: Schedule) {
        val publicText = if (schedule.private) renderer.privateText() else null
        scheduler.replaceAll(schedule.planned.map { renderer.render(it).planned(it, publicText) })
    }

    private fun schedules(): Flow<Schedule> =
        combine(
            friends.friends,
            reminders.reminders,
            hangouts.hangouts,
            settings.settings,
            combine(
                combine(groups.groups, plans.plans, interactions.interactions, links.links, ::Inputs),
                combine(calendarEvents(), calendarDecisions.decisions, ::CalendarInputs),
                ::Pair,
            ),
        ) { fs, rs, hs, s, (inputs, cal) ->
            val (gs, ps, ints, ls) = inputs
            // Never sent anywhere: the calendar only shapes local notifications on this device (D3).
            val calendar = if (s.calendarSuggestions) CalendarSuggester.candidates(cal.events, fs, hs, cal.decisions, clock.timeZone) else emptyList()
            val planned = NotificationPlanner.plan(
                fs,
                rs,
                hs,
                clock.now(),
                clock.timeZone,
                s.reminderHour,
                rules = CadenceRules.from(s),
                groups = gs,
                plans = ps,
                interactions = ints,
                policy = NudgePolicy.from(s),
                links = ls,
                calendar = calendar,
                notifications = s.notifications,
            )
            Schedule(planned, s.privateNotifications)
        }

    private data class Schedule(val planned: List<PlannedNotification>, val private: Boolean)

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}

/** The calendar events and answers the notification schedule reads (D3, on this device only). */
private data class CalendarInputs(val events: List<CalendarEvent>, val decisions: List<CalendarDecision>)

/** The circles, plans, interactions and links both the agenda and the notification schedule are built from. */
private data class Inputs(
    val groups: List<FriendGroup>,
    val plans: List<Plan>,
    val interactions: List<Interaction>,
    val links: List<PersonLink>,
)

/** The platform-facing parts the planner decided (channel, persistence, slot), on top of the rendered text. */
internal fun RenderedNotification.planned(from: PlannedNotification, publicText: String?) = copy(
    publicText = publicText,
    channel = from.channel,
    persistent = from.persistent,
    until = from.until,
    slot = from.slot,
    whileOpen = from.whileOpen,
)

/** Settings → Notifications → "Send a test notification" (issue #98): shows one right away, as the others would. */
class SendTestNotificationUseCase(
    private val settings: SettingsRepository,
    private val scheduler: NotificationScheduler,
    private val renderer: NotificationRenderer,
    private val clock: AppClock,
) {
    suspend operator fun invoke() {
        val s = settings.settings.first()
        val planned = PlannedNotification("test", clock.now(), NotificationContent.Test)
        val publicText = if (s.privateNotifications) renderer.privateText() else null
        scheduler.showNow(renderer.render(planned).planned(planned, publicText))
    }
}

/** Today's "Turn on notifications" card (issue #98): "Not now" hides it for good on this device. */
class DismissNotificationPrimerUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke() = settings.update { it.copy(notificationPrimerDismissed = true) }
}

/** Settings → Notifications (issue #98): the master switch, a switch per type and "Keep important ones on screen". */
class UpdateNotificationSettingsUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(transform: (NotificationSettings) -> NotificationSettings) =
        settings.update { it.copy(notifications = transform(it.notifications)) }
}
