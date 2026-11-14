package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.repository.CalendarDecisionRepository
import com.neteinstein.goodcompany.core.domain.repository.CalendarReader
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How an event was tied to a friend. */
enum class CalendarMatchKind { EMAIL, NAME }

/** The friends an event is with, and how each was found. */
data class CalendarMatch(val friends: List<Friend>, val by: Set<CalendarMatchKind>)

/**
 * Ties calendar events to friends (D3): an attendee whose email is one of the friend's [Friend.emails], or the friend
 * named in the title (the share screen's name matching, [ShareMatcher.isNamedIn]). Pure.
 */
object CalendarMatcher {
    fun match(event: CalendarEvent, friends: List<Friend>): CalendarMatch? {
        val emails = event.attendees.map { it.email.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val by = mutableSetOf<CalendarMatchKind>()
        val matched = friends.filter { f ->
            val byEmail = emails.isNotEmpty() && f.emails.any { it.address.trim().lowercase() in emails }
            val byName = ShareMatcher.isNamedIn(f, event.title)
            if (byEmail) by += CalendarMatchKind.EMAIL
            if (byName) by += CalendarMatchKind.NAME
            byEmail || byName
        }
        return if (matched.isEmpty()) null else CalendarMatch(matched, by)
    }
}

/** A first guess at the meetup's activity from the event title, in the app's five languages. Null = no idea. */
object ActivityGuesser {
    private val WORDS: List<Pair<HangoutActivity, List<String>>> = listOf(
        HangoutActivity.LUNCH to listOf("lunch", "brunch", "almoço", "almoco", "almuerzo", "comida", "déjeuner", "dejeuner", "prânz", "pranz"),
        HangoutActivity.DINNER to listOf("dinner", "supper", "jantar", "cena", "dîner", "diner", "cină", "cina"),
        HangoutActivity.DRINKS to listOf("drinks", "drink", "beer", "beers", "copos", "cerveja", "cañas", "copas", "apéro", "apero", "bere"),
        HangoutActivity.COFFEE to listOf("coffee", "café", "cafe", "cafezinho", "cafea"),
        HangoutActivity.RUNNING to listOf("run", "running", "corrida", "correr", "carrera", "course", "jogging", "alergare"),
        HangoutActivity.WALK to listOf("walk", "hike", "passeio", "caminhada", "paseo", "caminata", "balade", "randonnée", "plimbare"),
        HangoutActivity.SPORTS to listOf(
            "padel", "tennis", "ténis", "tenis", "football", "futebol", "fútbol", "gym", "ginásio", "climbing", "escalada", "yoga",
            "swim", "natação",
        ),
        HangoutActivity.MUSIC to listOf("concert", "concerto", "gig", "festival", "concierto"),
        HangoutActivity.CULTURE to listOf(
            "cinema", "movie", "film", "filme", "película", "theatre", "theater", "teatro", "théâtre", "museum", "museu", "museo",
            "musée", "muzeu", "exhibition", "exposição", "book club",
        ),
        HangoutActivity.TRIP to
            listOf("trip", "holiday", "vacation", "viagem", "férias", "viaje", "vacaciones", "voyage", "vacances", "excursie", "vacanță"),
        HangoutActivity.AT_HOME to listOf("at home", "em casa", "en casa", "à la maison", "acasă", "board games", "jogos"),
    )

    fun guess(title: String): HangoutActivity? {
        val t = title.lowercase()
        return WORDS.firstOrNull { (_, words) -> words.any { Regex("(^|[^\\p{L}])${Regex.escape(it)}($|[^\\p{L}])").containsMatchIn(t) } }?.first
    }
}

/** One past event worth logging. "Log it" opens the log sheet with [friends], [date] and [activity]. */
data class CalendarSuggestion(
    val event: CalendarEvent,
    val friends: List<Friend>,
    val matchedBy: Set<CalendarMatchKind>,
    val date: LocalDate,
    val activity: HangoutActivity?,
    /** Ended before suggestions were turned on: shown under "Looking back" (F2). */
    val lookingBack: Boolean,
) {
    val key: String get() = CalendarDecision.occurrenceKey(event)

    /** "Never for this series" is offered only for repeating events. */
    val isRepeating: Boolean get() = event.seriesId != null
}

/**
 * Turns calendar events into suggestions (D3, F2). Pure. An event is suggested once it has ended, within the last
 * [LOOK_BACK_DAYS] (so turning the feature on also looks back two months), when it matches at least one friend, and
 * unless the user said "No" to it, "Never" to its series, or already logged a meetup with one of those friends that day.
 */
object CalendarSuggester {
    const val LOOK_BACK_DAYS = 60

    /** How far ahead events are read: notifications for later today and the "Before you meet" row. */
    const val LOOK_AHEAD_DAYS = 14

    /** Events longer than this (holidays, conferences) aren't meetups to log. */
    private const val MAX_EVENT_DAYS = 3

    fun suggestions(
        events: List<CalendarEvent>,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        decisions: List<CalendarDecision>,
        now: Instant,
        zone: TimeZone,
        onSince: Instant?,
    ): List<CalendarSuggestion> = candidates(events, friends, hangouts, decisions, zone)
        .filter { it.event.end <= now && it.event.end > now - LOOK_BACK_DAYS.days }
        .map { it.copy(lookingBack = onSince != null && it.event.end < onSince) }
        .sortedByDescending { it.event.start }

    /**
     * Every matched, unanswered, unlogged event, whenever it happens (past or future), as a suggestion. The notification
     * planner uses the ones ending later; [suggestions] the ones that ended.
     */
    fun candidates(
        events: List<CalendarEvent>,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        decisions: List<CalendarDecision>,
        zone: TimeZone,
    ): List<CalendarSuggestion> {
        val no = decisions.filter { it.kind == CalendarDecisionKind.NO }.map { it.key }.toSet()
        val never = decisions.filter { it.kind == CalendarDecisionKind.NEVER }.map { it.key }.toSet()
        val loggedOn = hangouts.flatMap { h -> h.friendIds.map { it to h.at.toLocalDate(zone) } }.toSet()
        return events
            .asSequence()
            .filter { it.end - it.start <= MAX_EVENT_DAYS.days }
            .filterNot { CalendarDecision.occurrenceKey(it) in no }
            .filterNot { e -> CalendarDecision.seriesKey(e)?.let { it in never } == true }
            .mapNotNull { e ->
                val match = CalendarMatcher.match(e, friends) ?: return@mapNotNull null
                val date = e.start.toLocalDate(zone)
                if (match.friends.any { (it.id to date) in loggedOn }) return@mapNotNull null
                CalendarSuggestion(e, match.friends, match.by, date, ActivityGuesser.guess(e.title), lookingBack = false)
            }
            .distinctBy { it.key }
            .toList()
    }

    /** Friends with a matched event starting today or tomorrow, soonest first (the home "Before you meet" row). */
    fun meetingSoon(
        events: List<CalendarEvent>,
        friends: List<Friend>,
        decisions: List<CalendarDecision>,
        now: Instant,
        zone: TimeZone,
    ): List<Pair<FriendId, LocalDate>> {
        val today = now.toLocalDate(zone)
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        return candidates(events, friends, emptyList(), decisions, zone)
            .filter { it.event.end > now && it.date in today..tomorrow }
            .sortedBy { it.event.start }
            .flatMap { s -> s.friends.map { it.id to s.date } }
            .distinctBy { it.first }
    }
}

/**
 * The events from the chosen calendars around now ([CalendarSuggester.LOOK_BACK_DAYS] back,
 * [CalendarSuggester.LOOK_AHEAD_DAYS] ahead), read again whenever the calendar settings change. Empty while the
 * feature is off, no calendar is chosen or access was refused. Never stored.
 */
class ObserveCalendarEventsUseCase(
    private val settings: SettingsRepository,
    private val reader: CalendarReader,
    private val clock: AppClock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<CalendarEvent>> = settings.settings
        .map { s -> s.calendarSuggestions to s.calendarIds.toSet() }
        .distinctUntilChanged()
        .mapLatest { (on, ids) -> read(on, ids) }

    private suspend fun read(on: Boolean, ids: Set<String>): List<CalendarEvent> {
        if (!on || ids.isEmpty() || !reader.isSupported || !reader.hasAccess()) return emptyList()
        val now = clock.now()
        return runCatching {
            reader.events(ids, now - CalendarSuggester.LOOK_BACK_DAYS.days, now + CalendarSuggester.LOOK_AHEAD_DAYS.days)
        }.getOrDefault(emptyList())
    }
}

/** The "From your calendar" list: past meetups worth logging, newest first. */
class ObserveCalendarSuggestionsUseCase(
    private val events: ObserveCalendarEventsUseCase,
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val decisions: CalendarDecisionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<CalendarSuggestion>> =
        combine(events(), friends.friends, hangouts.hangouts, decisions.decisions, settings.settings) { es, fs, hs, ds, s ->
            if (!s.calendarSuggestions) {
                emptyList()
            } else {
                CalendarSuggester.suggestions(es, fs, hs, ds, clock.now(), clock.timeZone, s.calendarOnSince)
            }
        }
}

/** "No" (this occurrence) or "Never for this series". Undo with [UndoCalendarDecisionUseCase]. */
class DecideCalendarSuggestionUseCase(private val decisions: CalendarDecisionRepository, private val clock: AppClock) {
    /** The stored decision, or null when [kind] is NEVER and the event doesn't repeat. */
    suspend operator fun invoke(suggestion: CalendarSuggestion, kind: CalendarDecisionKind): CalendarDecision? {
        val key = when (kind) {
            CalendarDecisionKind.NO -> suggestion.key
            CalendarDecisionKind.NEVER -> CalendarDecision.seriesKey(suggestion.event) ?: return null
        }
        return CalendarDecision(key, kind, clock.now()).also { decisions.upsert(it) }
    }
}

class UndoCalendarDecisionUseCase(private val decisions: CalendarDecisionRepository) {
    suspend operator fun invoke(decision: CalendarDecision) = decisions.delete(decision.key)
}

/** What the calendar settings screen shows. */
data class CalendarSettings(
    val supported: Boolean,
    val enabled: Boolean,
    val hasAccess: Boolean,
    val calendars: List<DeviceCalendar>,
    val chosen: Set<String>,
)

/** The calendar settings, with the device's calendars read again whenever the settings change. */
class ObserveCalendarSettingsUseCase(private val settings: SettingsRepository, private val reader: CalendarReader) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<CalendarSettings> = settings.settings
        .map { it.calendarSuggestions to it.calendarIds.toSet() }
        .distinctUntilChanged()
        .mapLatest { (on, chosen) ->
            val access = reader.isSupported && on && reader.hasAccess()
            CalendarSettings(
                supported = reader.isSupported,
                enabled = on,
                hasAccess = access,
                calendars = if (access) runCatching { reader.calendars() }.getOrDefault(emptyList()) else emptyList(),
                chosen = chosen,
            )
        }
}

/**
 * Turns calendar suggestions on or off. Turning on starts a new "Looking back" window (F2); no calendar is ticked
 * until the user chooses (D3).
 */
class SetCalendarSuggestionsUseCase(private val settings: SettingsRepository, private val clock: AppClock) {
    suspend operator fun invoke(enabled: Boolean) {
        val current = settings.settings.first()
        if (current.calendarSuggestions == enabled) return
        settings.update { it.copy(calendarSuggestions = enabled, calendarOnSince = if (enabled) clock.now() else it.calendarOnSince) }
    }
}

class SetCalendarChosenUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(calendarId: String, chosen: Boolean) = settings.update { s ->
        val ids = if (chosen) (s.calendarIds + calendarId).distinct() else s.calendarIds - calendarId
        s.copy(calendarIds = ids)
    }
}
