package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** What the Log popup records: a meetup ([MEETUP], a hangout), a contact at a distance, or a journal entry. */
enum class LogKind(val interactionType: InteractionType?) {
    MEETUP(InteractionType.MEETUP),
    CALL(InteractionType.CALL),
    MESSAGE(InteractionType.MESSAGE),
    EMAIL(InteractionType.EMAIL),
    VIDEO_CALL(InteractionType.VIDEO_CALL),
    LONG_CHAT(InteractionType.LONG_CHAT),
    GAME(InteractionType.GAME),
    OTHER(InteractionType.OTHER),
    JOURNAL(null),
    ;

    /** A contact at a distance: written as an [Interaction], with no place or activity. */
    val isRemote: Boolean get() = interactionType?.isRemote == true

    companion object {
        /**
         * The kind named by `WeMetRoute.type`: a [LogKind] name or an [InteractionType] name ("WHATSAPP" logs
         * a message). Null or unknown → null (the popup then preselects [MEETUP]).
         */
        fun parse(name: String?): LogKind? = when (name) {
            null -> null
            InteractionType.WHATSAPP.name -> MESSAGE
            else -> entries.firstOrNull { it.name == name }
        }

        /**
         * The "How?" order with nothing logged and nothing set in Settings (issues #151, #152): the popup's old row (in
         * person first), then email and the journal.
         */
        val DEFAULT_ORDER: List<LogKind> = listOf(MEETUP, CALL, VIDEO_CALL, MESSAGE, LONG_CHAT, GAME, OTHER, EMAIL, JOURNAL)
    }
}

/** One "Log" sheet save. [date] null or today means now; a past date logs it at noon that day. */
data class LogRequest(
    val friendIds: List<FriendId>,
    val kind: LogKind,
    val date: LocalDate? = null,
    val note: String = "",
    /** Meetups only. */
    val placeName: String? = null,
    val location: GeoPoint? = null,
    val activity: HangoutActivity? = null,
    /** Meetups only: one of your own activities, by name (issue #121), when [activity] is null. */
    val activityName: String? = null,
    /** Contacts only: where the log came from (the share screen passes [InteractionSource.SHARE]). */
    val source: InteractionSource = InteractionSource.MANUAL,
    val durationSeconds: Int? = null,
    /** [LogKind.OTHER] only: what it was ("Sent a postcard"), trimmed. */
    val label: String = "",
    /**
     * Issue #116: the time of day it happened (a quick chip or a picked time). Null = now for today, noon for an
     * earlier day. A time later than now today is taken as now: nothing is logged in the future.
     */
    val time: LocalTime? = null,
)

/** Issue #116: the quick times of day on the Log popup's time row, besides "Now" and "Pick a time". */
enum class LogTimeOfDay(val time: LocalTime) {
    MORNING(LocalTime(9, 0)),
    LUNCH(LocalTime(13, 0)),
    AFTERNOON(LocalTime(16, 0)),
    EVENING(LocalTime(20, 0)),
    ;

    companion object {
        /** The chip for [time], if it is one of them. */
        fun of(time: LocalTime?): LogTimeOfDay? = entries.firstOrNull { it.time == time }
    }
}

/**
 * When a log happened (issue #116): [date] (null = today) at [time]; without a time, now for today and noon for an
 * earlier day (the rule from before times existed). Never later than [now], and never on a future day.
 */
fun logInstant(date: LocalDate?, time: LocalTime?, now: Instant, zone: TimeZone): Instant {
    val today = now.toLocalDate(zone)
    val day = date ?: today
    require(day <= today) { "Can't log the future" }
    return when {
        time != null -> minOf(day.atTime(time).toInstant(zone), now)
        day == today -> now
        else -> day.atTime(LocalTime(12, 0)).toInstant(zone)
    }
}

/** What a log wrote, so the caller can show it or undo it. */
sealed interface LogResult {
    /**
     * [plan] is the plan this meetup answered ("Did you meet?"), as it was before that occurrence was closed: Undo
     * puts it back. Set by the sheet that closed it, not by [LogInteractionUseCase].
     */
    data class Met(val hangout: Hangout, val plan: Plan? = null) : LogResult

    /** [plan] as for [Met]: a plan answered with a call, a chat or a game (issue #101). */
    data class Contacts(val interactions: List<Interaction>, val plan: Plan? = null) : LogResult

    data class Journal(val entries: List<JournalEntry>) : LogResult
}

/**
 * The single "Log" sheet: a meetup becomes a [Hangout] (through [CheckInUseCase]), a call/message/email/video call
 * one [Interaction] per person sharing a batch id, and a journal entry one [JournalEntry] per person (never contact).
 */
class LogInteractionUseCase(
    private val checkIn: CheckInUseCase,
    private val interactions: InteractionRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(request: LogRequest): LogResult =
        logAt(request, logInstant(request.date, request.time, clock.now(), clock.timeZone))

    /** Writes [request] at exactly [at] (editing a timeline entry keeps its time of day, issue #102). */
    suspend fun logAt(request: LogRequest, at: Instant): LogResult {
        val people = request.friendIds.distinct()
        require(people.isNotEmpty()) { "Pick at least one friend" }
        val note = request.note.trim()
        return when (val kind = request.kind) {
            LogKind.MEETUP -> LogResult.Met(
                checkIn(people, request.placeName, request.location, note, at, request.activity, request.activityName),
            )
            LogKind.JOURNAL -> {
                require(note.isNotEmpty()) { "A journal entry needs text" }
                val entries = people.map { JournalEntry(ids.next(), it, at, note) }
                entries.forEach { interactions.upsertJournal(it) }
                LogResult.Journal(entries)
            }
            else -> {
                val type = requireNotNull(kind.interactionType)
                val batch = if (people.size > 1) ids.next() else null
                val rows = people.map {
                    Interaction(
                        friendId = it,
                        type = type,
                        at = at,
                        id = ids.next(),
                        note = note,
                        source = request.source,
                        durationSeconds = request.durationSeconds,
                        batchId = batch,
                        label = request.label.trim().take(LABEL_MAX).takeIf { kind == LogKind.OTHER }.orEmpty(),
                    )
                }
                interactions.addAll(rows)
                LogResult.Contacts(rows)
            }
        }
    }

    companion object {
        /** The longest "Other" label kept: it is a short name, not a note. */
        const val LABEL_MAX = 40
    }
}

/** Puts back what a log wrote (snackbar "Undo"). */
class UndoLogUseCase(
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val plans: PlanRepository,
) {
    suspend operator fun invoke(result: LogResult) {
        when (result) {
            is LogResult.Met -> {
                hangouts.delete(result.hangout.id)
                result.plan?.let { plans.upsert(it) }
            }
            is LogResult.Contacts -> {
                result.interactions.forEach { interactions.delete(it.id) }
                result.plan?.let { plans.upsert(it) }
            }
            is LogResult.Journal -> result.entries.forEach { interactions.deleteJournal(it.id) }
        }
    }
}

/**
 * The meetup activities in the order the Log popup shows them (F3): the built-in ones turned on in Settings
 * ([AppSettings.activities]) and your own ([AppSettings.customActivities], issue #121), the most used first (ties keep
 * that order), so the first [SHOWN] are the ones on show and the rest sit behind "See all" (issue #151). An old meetup whose activity is
 * now off or removed keeps it; it just isn't offered.
 */
class ObserveActivitiesUseCase(private val hangouts: HangoutRepository, private val settings: SettingsRepository) {
    operator fun invoke(): Flow<List<ActivityChoice>> = combine(
        hangouts.hangouts,
        settings.settings.map { it.activities to it.customActivities }.distinctUntilChanged(),
    ) { hs, (enabled, custom) -> ranked(hs, enabled, custom) }

    companion object {
        const val SHOWN = 4

        fun ranked(
            hangouts: List<Hangout>,
            enabled: List<HangoutActivity> = HangoutActivity.entries,
            custom: List<String> = emptyList(),
        ): List<ActivityChoice> {
            val counts = hangouts.mapNotNull { it.activityChoice }.groupingBy { it }.eachCount()
            val all = enabled.distinct().map { ActivityChoice.BuiltIn(it) } + custom.distinct().map { ActivityChoice.Custom(it) }
            return all.sortedByDescending { counts[it] ?: 0 }
        }
    }
}

/**
 * One "How?" option on the Log popup (issue #151). [name] is one of your own How options (issue #152, Settings → How):
 * it is logged as [LogKind.OTHER] with the name as its label. Null = the built-in [kind] with its own label.
 */
data class LogKindOption(val kind: LogKind, val name: String? = null) {
    init {
        require(name == null || kind == LogKind.OTHER) { "Only Other carries a name of your own" }
    }

    companion object {
        /** Your own How option called [name]. */
        fun custom(name: String) = LogKindOption(LogKind.OTHER, name)
    }
}

/**
 * The Log popup's "How?" options, most used first (issue #151): the built-in kinds turned on in Settings
 * ([AppSettings.logKinds]) and your own ([AppSettings.customLogKinds], issue #152), the first [SHOWN] on show and the rest
 * behind "See all". Usage is what you logged yourself: each meetup, each manual contact (a contact with several people,
 * one [Interaction.batchId], counts once) and each journal entry; call-log and chat-import rows don't count. An "Other"
 * contact whose label is one of your own How options counts for that option, not for "Other". Ties, and everything with
 * nothing logged, keep the configured order: the built-in ones in your order, then your own.
 */
class ObserveLogKindsUseCase(
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<List<LogKindOption>> = combine(
        hangouts.hangouts,
        interactions.interactions,
        interactions.journal,
        settings.settings.map { it.logKinds to it.customLogKinds }.distinctUntilChanged(),
    ) { hs, ins, js, (enabled, custom) ->
        ranked(usage(hs, ins, js, custom), configured(enabled, custom))
    }.distinctUntilChanged()

    companion object {
        const val SHOWN = 4

        /** The order with nothing logged and nothing set: see [LogKind.DEFAULT_ORDER]. */
        val DEFAULT_ORDER: List<LogKind> get() = LogKind.DEFAULT_ORDER

        /** The options Settings turned on, in order: the built-in ones, then your own. */
        fun configured(enabled: List<LogKind> = LogKind.DEFAULT_ORDER, custom: List<String> = emptyList()): List<LogKindOption> =
            enabled.distinct().map { LogKindOption(it) } + custom.distinct().map { LogKindOption.custom(it) }

        /**
         * How many times each option was logged by hand. An "Other" contact labelled with one of [custom] (ignoring case
         * and accents) counts for that option, under the name as it is in [custom].
         */
        fun usage(
            hangouts: List<Hangout>,
            interactions: List<Interaction>,
            journal: List<JournalEntry>,
            custom: List<String> = emptyList(),
        ): Map<LogKindOption, Int> {
            val counts = mutableMapOf<LogKindOption, Int>()
            fun count(option: LogKindOption) {
                counts[option] = (counts[option] ?: 0) + 1
            }
            val own = custom.associateBy { Tags.normalize(it) }
            if (hangouts.isNotEmpty()) counts[LogKindOption(LogKind.MEETUP)] = hangouts.size
            interactions.filterNot { it.source.isAutomatic }
                .distinctBy { it.batchId ?: it.id.ifBlank { "${it.friendId.value}-${it.type}-${it.at}" } }
                .forEach { i ->
                    val kind = LogKind.parse(i.type.name) ?: return@forEach
                    val name = if (kind == LogKind.OTHER && i.label.isNotBlank()) own[Tags.normalize(i.label)] else null
                    count(LogKindOption(kind, name))
                }
            if (journal.isNotEmpty()) counts[LogKindOption(LogKind.JOURNAL)] = journal.size
            return counts
        }

        /** [configured] (on, in order), the most used first; ties keep that order. */
        fun ranked(
            usage: Map<LogKindOption, Int>,
            configured: List<LogKindOption> = configured(),
        ): List<LogKindOption> = configured.distinct().sortedByDescending { usage[it] ?: 0 }
    }
}

/** Saves the built-in "How?" options the Log popup offers, in this order (issue #152). Ignored when it would leave none. */
class SetLogKindsUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(kinds: List<LogKind>) {
        val list = kinds.distinct()
        if (list.isEmpty()) return
        settings.update { it.copy(logKinds = list) }
    }
}

/**
 * Ticks or unticks whether a way of keeping in touch restarts the rhythm of people on the default (Settings → How,
 * decision 2026-11-21 "Catch-up · what counts"). In person and journal entries can't change.
 */
class SetCatchUpKindUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(option: LogKindOption, counts: Boolean) {
        settings.update { s ->
            val name = option.name
            when {
                name != null -> {
                    val list = s.catchUpCustomKinds
                    val others = list.filterNot { Tags.normalize(it) == Tags.normalize(name) }
                    // Ticking one already ticked keeps the name as it is.
                    s.copy(
                        catchUpCustomKinds = if (!counts) {
                            others
                        } else if (others.size < list.size) {
                            list
                        } else {
                            list + name
                        },
                    )
                }
                option.kind.isRemote -> s.copy(catchUpKinds = if (counts) s.catchUpKinds + option.kind else s.catchUpKinds - option.kind)
                else -> s
            }
        }
    }
}

/** Saves the activities the Log popup offers, in this order (F3). Ignored when it would leave none. */
class SetActivitiesUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(activities: List<HangoutActivity>) {
        val list = activities.distinct()
        if (list.isEmpty()) return
        settings.update { it.copy(activities = list) }
    }
}

/**
 * A meetup or a contact (a call, a chat…, issue #101) just logged in the app, with the people it was with (for
 * "Logged time with Rui ✨ · Undo"). Journal entries never come here.
 */
data class LoggedMeetup(val result: LogResult, val people: List<Friend>)

/**
 * The last meetup or contact logged from the "Caught up" panel or the Log popup (F2), until the app-wide snackbar has shown it.
 * The sheet closes on save, so the snackbar that offers Undo lives outside it.
 */
class LoggedMeetupInbox {
    private val state = MutableStateFlow<LoggedMeetup?>(null)
    val pending: StateFlow<LoggedMeetup?> = state.asStateFlow()

    fun offer(meetup: LoggedMeetup) {
        state.value = meetup
    }

    /** Done with [meetup] (shown or undone); a newer one that arrived meanwhile stays. */
    fun clear(meetup: LoggedMeetup) {
        state.compareAndSet(meetup, null)
    }
}
