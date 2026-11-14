package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** What the log sheet records: a meetup ([MEETUP], a hangout), a contact at a distance, or a journal entry. */
enum class LogKind(val interactionType: InteractionType?) {
    MEETUP(InteractionType.MEETUP),
    CALL(InteractionType.CALL),
    MESSAGE(InteractionType.MESSAGE),
    EMAIL(InteractionType.EMAIL),
    VIDEO_CALL(InteractionType.VIDEO_CALL),
    JOURNAL(null),
    ;

    companion object {
        /**
         * The kind named by `LogInteractionRoute.type`: a [LogKind] name or an [InteractionType] name ("WHATSAPP" logs
         * a message). Null or unknown → null (the sheet then preselects [MEETUP]).
         */
        fun parse(name: String?): LogKind? = when (name) {
            null -> null
            InteractionType.WHATSAPP.name -> MESSAGE
            else -> entries.firstOrNull { it.name == name }
        }
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
    /** Contacts only: where the log came from (the share screen passes [InteractionSource.SHARE]). */
    val source: InteractionSource = InteractionSource.MANUAL,
    val durationSeconds: Int? = null,
)

/** What a log wrote, so the caller can show it or undo it. */
sealed interface LogResult {
    data class Met(val hangout: Hangout) : LogResult

    data class Contacts(val interactions: List<Interaction>) : LogResult

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
    suspend operator fun invoke(request: LogRequest): LogResult {
        val people = request.friendIds.distinct()
        require(people.isNotEmpty()) { "Pick at least one friend" }
        val at = instantFor(request.date)
        val note = request.note.trim()
        return when (val kind = request.kind) {
            LogKind.MEETUP -> LogResult.Met(
                checkIn(people, request.placeName, request.location, note, at, request.activity),
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
                    )
                }
                interactions.addAll(rows)
                LogResult.Contacts(rows)
            }
        }
    }

    private fun instantFor(date: LocalDate?): Instant {
        val today = clock.today()
        require(date == null || date <= today) { "Can't log the future" }
        return if (date == null || date == today) clock.now() else date.atTime(NOON).toInstant(clock.timeZone)
    }

    private companion object {
        val NOON = LocalTime(12, 0)
    }
}

/** Puts back what a log wrote (snackbar "Undo"). */
class UndoLogUseCase(
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
) {
    suspend operator fun invoke(result: LogResult) {
        when (result) {
            is LogResult.Met -> hangouts.delete(result.hangout.id)
            is LogResult.Contacts -> result.interactions.forEach { interactions.delete(it.id) }
            is LogResult.Journal -> result.entries.forEach { interactions.deleteJournal(it.id) }
        }
    }
}

/**
 * The meetup activities in the order the log sheet shows them (F3): only the ones turned on in Settings
 * ([AppSettings.activities]), the most used first (ties keep the user's order), so the first six are the ones on show
 * and the rest sit behind "More". An old meetup whose activity is now off keeps it; it just isn't offered.
 */
class ObserveActivitiesUseCase(private val hangouts: HangoutRepository, private val settings: SettingsRepository) {
    operator fun invoke(): Flow<List<HangoutActivity>> =
        combine(hangouts.hangouts, settings.settings.map { it.activities }.distinctUntilChanged()) { hs, enabled -> ranked(hs, enabled) }

    companion object {
        const val SHOWN = 6

        fun ranked(hangouts: List<Hangout>, enabled: List<HangoutActivity> = HangoutActivity.entries): List<HangoutActivity> {
            val counts = hangouts.mapNotNull { it.activity }.groupingBy { it }.eachCount()
            return enabled.distinct().sortedByDescending { counts[it] ?: 0 }
        }
    }
}

/** Saves the activities the log sheet offers, in this order (F3). Ignored when it would leave none. */
class SetActivitiesUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(activities: List<HangoutActivity>) {
        val list = activities.distinct()
        if (list.isEmpty()) return
        settings.update { it.copy(activities = list) }
    }
}
