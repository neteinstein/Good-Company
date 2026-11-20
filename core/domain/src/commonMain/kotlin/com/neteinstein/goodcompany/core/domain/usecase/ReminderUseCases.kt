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
    /** A new reminder about [friendIds] and/or the circle [circleId] (issue #88: it then belongs to the circle). */
    suspend operator fun invoke(title: String, friendIds: List<FriendId>, dueAt: Instant?, circleId: String? = null): Reminder {
        require(title.isNotBlank()) { "Title required" }
        val reminder = Reminder(ids.next(), title.trim(), friendIds.distinct(), dueAt, done = false, createdAt = clock.now(), circleId = circleId)
        reminders.upsert(reminder)
        return reminder
    }
}

/** Changes a reminder's title, people, circle and time (a reminder opened from a circle's "Coming up", issue #88). */
class EditReminderUseCase(private val reminders: ReminderRepository) {
    suspend operator fun invoke(reminder: Reminder, title: String, friendIds: List<FriendId>, dueAt: Instant?, circleId: String?): Reminder {
        require(title.isNotBlank()) { "Title required" }
        val edited = reminder.copy(title = title.trim(), friendIds = friendIds.distinct(), dueAt = dueAt, circleId = circleId)
        reminders.upsert(edited)
        return edited
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
