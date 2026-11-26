package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.repository.CalendarDecisionRepository
import com.neteinstein.goodcompany.core.domain.repository.CalendarReader
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
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

/**
 * The friends an event is with, and how each was found. [ambiguous]: people named only by a first name that someone
 * else matched too ("Almoço com a Ana" with two Anas), so nobody can tell which one it is; they need an answer
 * ([CalendarDecisionKind.WHO]), a plan or a logged meetup that day. [names]: those first names, as the title writes them.
 */
data class CalendarMatch(
    val friends: List<Friend>,
    val by: Set<CalendarMatchKind>,
    val ambiguous: List<Friend> = emptyList(),
    val names: List<String> = emptyList(),
)

/**
 * Ties calendar events to friends (D3): an attendee whose email is one of the friend's [Friend.emails], their full name
 * in the title, or their first name (3+ letters, a whole word) when nobody else's first name is the same
 * (decision 2026-11-26). A first name that a full name or email already explains ("Lunch with Ana Silva") doesn't also
 * match the other Anas; one that several people share and nothing explains makes them [CalendarMatch.ambiguous]. Pure.
 */
object CalendarMatcher {
    private const val MIN_FIRST_NAME = 3

    fun match(event: CalendarEvent, friends: List<Friend>): CalendarMatch? {
        val emails = event.attendees.map { it.email.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val title = event.title.lowercase()
        val sure = mutableListOf<Friend>()
        val by = mutableSetOf<CalendarMatchKind>()
        val firstOnly = mutableListOf<Pair<Friend, Set<String>>>()
        friends.forEach { f ->
            val byEmail = emails.isNotEmpty() && f.emails.any { it.address.trim().lowercase() in emails }
            val byFullName = names(f).any { it.contains(' ') && (it in title || it.replace(" ", "") in title) }
            val firsts = names(f).map { it.substringBefore(' ') }.filter { it.length >= MIN_FIRST_NAME && isWord(it, title) }.toSet()
            when {
                byEmail || byFullName -> {
                    sure += f
                    if (byEmail) by += CalendarMatchKind.EMAIL
                    if (byFullName) by += CalendarMatchKind.NAME
                }
                firsts.isNotEmpty() -> firstOnly += f to firsts
            }
        }
        // A first name in the title is explained by whoever matched surely and has it.
        val explained = sure.flatMap { f -> names(f).map { it.substringBefore(' ') } }.toSet()
        val open = firstOnly.mapNotNull { (f, firsts) -> (firsts - explained).takeIf { it.isNotEmpty() }?.let { f to it } }
        val count = open.flatMap { it.second }.groupingBy { it }.eachCount()
        val ambiguous = mutableListOf<Friend>()
        val names = linkedSetOf<String>()
        open.forEach { (f, firsts) ->
            val shared = firsts.filter { (count[it] ?: 0) > 1 }
            if (shared.isEmpty()) {
                sure += f
                by += CalendarMatchKind.NAME
            } else {
                ambiguous += f
                names += shared
            }
        }
        if (sure.isEmpty() && ambiguous.isEmpty()) return null
        return CalendarMatch(sure, by, ambiguous, names.map { asWritten(it, event.title) })
    }

    /** The names a friend goes by: the one given in the app and the contact's (F19). */
    private fun names(f: Friend): List<String> = listOf(f.displayName, f.name).map { it.lowercase().trim() }.filter { it.isNotEmpty() }.distinct()

    private fun isWord(word: String, text: String) = wordRegex(word).containsMatchIn(text)

    private fun wordRegex(word: String, ignoreCase: Boolean = false) =
        Regex("(^|[^\\p{L}])${Regex.escape(word)}($|[^\\p{L}])", if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())

    /** [word] as the title writes it ("Ana", not "ana"). */
    private fun asWritten(word: String, title: String): String =
        wordRegex(word, ignoreCase = true).find(title)?.value?.trim { !it.isLetter() } ?: word.replaceFirstChar { it.uppercase() }
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

/** One past event worth logging. "Log it" opens the Log popup with [friends], [date] and [activity]. */
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
 * "Who's Ana?" (decision 2026-11-26): an event whose title names a first name several people share, with nothing that
 * says which of them it is. Answered once for the whole series ([isSeries]) or for this occurrence.
 */
data class CalendarQuestion(
    val event: CalendarEvent,
    /** The people it could be, in the friends' order. */
    val candidates: List<Friend>,
    /** The shared first names, as the title writes them. */
    val names: List<String>,
    val date: LocalDate,
) {
    val key: String get() = CalendarDecision.whoKey(event)

    val isSeries: Boolean get() = event.seriesId != null
}

/**
 * Turns calendar events into suggestions (D3, F2). Pure. An event is suggested once it has ended, within the last
 * [LOOK_BACK_DAYS] (so turning the feature on also looks back two months), when it matches at least one friend, and
 * unless the user said "No" to it, "Never" to its series, or already logged a meetup with one of those friends that day.
 *
 * A first name several people share (decision 2026-11-26) is settled, in this order, by a plan that day with one of
 * them, a meetup logged with one of them that day, or the user's answer to "Who's Ana?" ([CalendarDecisionKind.WHO], for
 * the series). Until then nobody is guessed: the event counts only the people it names surely, and [questions] asks.
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
        plans: List<Plan> = emptyList(),
    ): List<CalendarSuggestion> = candidates(events, friends, hangouts, decisions, zone, plans)
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
        plans: List<Plan> = emptyList(),
    ): List<CalendarSuggestion> {
        val loggedOn = loggedOn(hangouts, zone)
        return matched(events, friends, decisions, zone)
            .mapNotNull { (e, match, date) ->
                val who = resolve(match, e, date, decisions, plans, loggedOn) ?: match.friends
                if (who.isEmpty() || who.any { (it.id to date) in loggedOn }) return@mapNotNull null
                CalendarSuggestion(e, who, match.by, date, ActivityGuesser.guess(e.title), lookingBack = false)
            }
            .distinctBy { it.key }
            .toList()
    }

    /**
     * The "Who's Ana?" questions still open: events from the last [LOOK_BACK_DAYS] up to the end of tomorrow, one per
     * series. Coming ones first (soonest first), then ended ones (newest first). Pure.
     */
    fun questions(
        events: List<CalendarEvent>,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        decisions: List<CalendarDecision>,
        plans: List<Plan>,
        now: Instant,
        zone: TimeZone,
    ): List<CalendarQuestion> {
        val loggedOn = loggedOn(hangouts, zone)
        val tomorrow = now.toLocalDate(zone).plus(1, DateTimeUnit.DAY)
        val open = matched(events, friends, decisions, zone)
            .filter { (e, match, date) -> match.ambiguous.isNotEmpty() && e.end > now - LOOK_BACK_DAYS.days && date <= tomorrow }
            .filter { (e, match, date) -> resolve(match, e, date, decisions, plans, loggedOn) == null }
            .map { (e, match, date) -> CalendarQuestion(e, match.ambiguous, match.names, date) }
            .toList()
        val (coming, ended) = open.partition { it.event.end > now }
        return (coming.sortedBy { it.event.start } + ended.sortedByDescending { it.event.start }).distinctBy { it.key }
    }

    private data class Matched(val event: CalendarEvent, val match: CalendarMatch, val date: LocalDate)

    /** Events that may be meetups (not too long, not answered "No" or "Never") with whoever they name. */
    private fun matched(events: List<CalendarEvent>, friends: List<Friend>, decisions: List<CalendarDecision>, zone: TimeZone): Sequence<Matched> {
        val no = decisions.filter { it.kind == CalendarDecisionKind.NO }.map { it.key }.toSet()
        val never = decisions.filter { it.kind == CalendarDecisionKind.NEVER }.map { it.key }.toSet()
        return events
            .asSequence()
            .filter { it.end - it.start <= MAX_EVENT_DAYS.days }
            .filterNot { CalendarDecision.occurrenceKey(it) in no }
            .filterNot { e -> CalendarDecision.seriesKey(e)?.let { it in never } == true }
            .mapNotNull { e -> CalendarMatcher.match(e, friends)?.let { Matched(e, it, e.start.toLocalDate(zone)) } }
    }

    private fun loggedOn(hangouts: List<Hangout>, zone: TimeZone): Set<Pair<FriendId, LocalDate>> =
        hangouts.flatMap { h -> h.friendIds.map { it to h.at.toLocalDate(zone) } }.toSet()

    /**
     * Everyone the event is with once its shared first names are settled, or null while they aren't. A plan that day
     * with some of them wins, then a meetup logged that day, then the user's answer.
     */
    private fun resolve(
        match: CalendarMatch,
        event: CalendarEvent,
        date: LocalDate,
        decisions: List<CalendarDecision>,
        plans: List<Plan>,
        loggedOn: Set<Pair<FriendId, LocalDate>>,
    ): List<Friend>? {
        if (match.ambiguous.isEmpty()) return match.friends
        val planned = plans
            .filter { it.status != PlanStatus.CANCELLED && (it.date == date || it.occurrences(date, date).isNotEmpty()) }
            .flatMap { it.friendIds }.toSet()
        val picked = match.ambiguous.filter { it.id in planned }.ifEmpty { match.ambiguous.filter { (it.id to date) in loggedOn } }
        if (picked.isNotEmpty()) return match.friends + picked
        val answer = decisions.firstOrNull { it.kind == CalendarDecisionKind.WHO && it.key == CalendarDecision.whoKey(event) } ?: return null
        return match.friends + match.ambiguous.filter { it.id in answer.friendIds }
    }

    /** Friends with a matched event starting today or tomorrow, soonest first (the home "Before you meet" row). */
    fun meetingSoon(
        events: List<CalendarEvent>,
        friends: List<Friend>,
        decisions: List<CalendarDecision>,
        now: Instant,
        zone: TimeZone,
        plans: List<Plan> = emptyList(),
    ): List<Pair<FriendId, LocalDate>> {
        val today = now.toLocalDate(zone)
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        return candidates(events, friends, emptyList(), decisions, zone, plans)
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
    private val plans: PlanRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<CalendarSuggestion>> = combine(
        events(),
        friends.friends,
        combine(hangouts.hangouts, plans.plans, ::Pair),
        decisions.decisions,
        settings.settings,
    ) { es, fs, (hs, ps), ds, s ->
        if (!s.calendarSuggestions) {
            emptyList()
        } else {
            CalendarSuggester.suggestions(es, fs, hs, ds, clock.now(), clock.timeZone, s.calendarOnSince, ps)
        }
    }
}

/** The open "Who's Ana?" questions (decision 2026-11-26), coming events first. Empty while suggestions are off. */
class ObserveCalendarQuestionsUseCase(
    private val events: ObserveCalendarEventsUseCase,
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val decisions: CalendarDecisionRepository,
    private val settings: SettingsRepository,
    private val plans: PlanRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<CalendarQuestion>> = combine(
        events(),
        friends.friends,
        combine(hangouts.hangouts, plans.plans, ::Pair),
        decisions.decisions,
        settings.settings,
    ) { es, fs, (hs, ps), ds, s ->
        if (!s.calendarSuggestions) emptyList() else CalendarSuggester.questions(es, fs, hs, ds, ps, clock.now(), clock.timeZone)
    }
}

/**
 * Answers "Who's Ana?": [friendIds] are the ones it is (empty = none of them), kept for the whole series. Returns the
 * stored answer; Undo is [UndoCalendarDecisionUseCase].
 */
class AnswerCalendarQuestionUseCase(private val decisions: CalendarDecisionRepository, private val clock: AppClock) {
    suspend operator fun invoke(question: CalendarQuestion, friendIds: Collection<FriendId>): CalendarDecision {
        val chosen = question.candidates.map { it.id }.filter { it in friendIds }
        return CalendarDecision(question.key, CalendarDecisionKind.WHO, clock.now(), chosen).also { decisions.upsert(it) }
    }
}

/** "No" (this occurrence) or "Never for this series". Undo with [UndoCalendarDecisionUseCase]. */
class DecideCalendarSuggestionUseCase(private val decisions: CalendarDecisionRepository, private val clock: AppClock) {
    /** The stored decision, or null when [kind] is NEVER and the event doesn't repeat (or is WHO: see [AnswerCalendarQuestionUseCase]). */
    suspend operator fun invoke(suggestion: CalendarSuggestion, kind: CalendarDecisionKind): CalendarDecision? {
        val key = when (kind) {
            CalendarDecisionKind.NO -> suggestion.key
            CalendarDecisionKind.NEVER -> CalendarDecision.seriesKey(suggestion.event) ?: return null
            CalendarDecisionKind.WHO -> return null
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
