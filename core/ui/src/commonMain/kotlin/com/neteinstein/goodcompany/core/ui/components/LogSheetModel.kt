package com.neteinstein.goodcompany.core.ui.components

import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpPolicy
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.EditTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogRequest
import com.neteinstein.goodcompany.core.domain.usecase.LogResult
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetup
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetupInbox
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlaceBookUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlansUseCase
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** What's typed or picked in a log so far; the friends themselves come from the repository. */
data class LogDraft(
    val kind: LogKind = LogKind.MEETUP,
    val people: List<FriendId> = emptyList(),
    val date: LocalDate,
    val note: String = "",
    val placeName: String = "",
    val location: GeoPoint? = null,
    val isLocating: Boolean = false,
    /** Why "Use my location" found nothing (issue #114); cleared by the next try. */
    val locationProblem: LocationProblem? = null,
    /** "Change" was tapped on a place that named itself: a new name is typed instead. */
    val placeChanged: Boolean = false,
    val activity: ActivityChoice? = null,
    val saving: Boolean = false,
    val circleId: String? = null,
    /** [LogKind.OTHER] only: what it was ("Sent a postcard"). */
    val label: String = "",
    /** Editing a timeline entry (issue #102) rather than logging a new one. */
    val editing: Boolean = false,
    /** Issue #116: the time of day (a quick chip or a picked time); null = now today, noon on an earlier day. */
    val time: LocalTime? = null,
)

/**
 * The state and actions behind the "Log" sheet (issue #21) and the small "Caught up" panel (issues #47, #101), so both share one
 * copy of the logic. Each ViewModel owns one and hands it its `viewModelScope`. A saved meetup is offered to
 * [LoggedMeetupInbox] for the app-wide "Logged time with … · Undo" snackbar.
 *
 * [plan] answers that plan occurrence's "Did you meet?" (issue #43): the draft becomes a meetup on that day with the
 * plan's people, activity, place and title (a circle plan lists its members unticked, F20), and saving also closes
 * the occurrence through [ConfirmPlanUseCase] without a second check-in; Undo puts the plan back.
 */
class LogSheetModel(
    private val scope: CoroutineScope,
    initial: LogDraft,
    observeFriends: ObserveFriendsUseCase,
    observeActivities: ObserveActivitiesUseCase,
    observeCircles: ObserveGroupsUseCase,
    observePlaceBook: ObservePlaceBookUseCase,
    private val observePlans: ObservePlansUseCase,
    private val logInteraction: LogInteractionUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val currentLocation: GetCurrentLocationUseCase,
    private val loggedMeetups: LoggedMeetupInbox,
    private val clock: AppClock,
    /** The plan occurrence this log answers, if any. */
    val plan: PlanOccurrence? = null,
    /** Saves an edited timeline entry (issue #102). */
    private val editEntry: EditTimelineEntryUseCase? = null,
    /** What counts as catching up (Settings → How), for the hint under "How?". */
    catchUp: Flow<CatchUpPolicy> = flowOf(CatchUpPolicy()),
) {
    /** The timeline entry being edited, set by [startEditing]. */
    private var editing: TimelineEntry? = null

    private val mutableDraft = MutableStateFlow(initial.copy(people = initial.people.distinct(), date = minOf(initial.date, clock.today())))

    /** What's chosen so far. */
    val draft: StateFlow<LogDraft> = mutableDraft.asStateFlow()

    val sheet: StateFlow<LogSheetState> = combine(
        combine(observeFriends(), observePlaceBook(), catchUp, ::Triple),
        observeActivities(),
        observeCircles(),
        mutableDraft,
    ) {
            (friends, places, policy),
            activities,
            circles,
            d,
        ->
        val byId = friends.associateBy { it.friend.id }
        val circle = circles.firstOrNull { it.group.id == d.circleId }
        val everyone = friends.map { it.friend }
        val chosen = d.people.mapNotNull { byId[it]?.friend }
        LogSheetState(
            kind = d.kind,
            people = chosen,
            everyone = everyone.sortedBy { it.displayName.lowercase() },
            // Issue #116: the people picker lists whoever you were last in touch with first.
            recentFirst = friends.sortedWith(
                compareByDescending<FriendSummary> { it.lastContact }
                    .thenBy { it.friend.displayName.lowercase() },
            ).map { it.friend },
            time = d.time,
            now = clock.now(),
            zone = clock.timeZone,
            lastTogether = if (d.kind == LogKind.MEETUP) places.lastTogether(chosen.map { it.id }) else null,
            date = d.date,
            today = clock.today(),
            note = d.note,
            label = d.label,
            place = meetupPlaceState(d.placeName, d.location, d.isLocating, d.placeChanged, places, d.people, everyone, d.locationProblem),
            activity = d.activity,
            activities = activities,
            saving = d.saving,
            circles = circles.map { it.group }.sortedBy { it.name.lowercase() },
            circle = circle?.group,
            circleMembers = circle?.members.orEmpty(),
            editing = d.editing,
            catchUp = policy,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), initialSheet())

    init {
        if (plan != null) {
            scope.launch {
                val p = observePlans().first().firstOrNull { it.id == plan.planId } ?: return@launch
                prefill {
                    it.copy(
                        kind = LogKind.MEETUP,
                        people = p.friendIds,
                        date = plan.date ?: p.date,
                        note = p.title,
                        placeName = p.place.orEmpty(),
                        activity = p.activityChoice ?: it.activity,
                        circleId = p.circleId,
                    )
                }
            }
        }
    }

    private fun initialSheet() = LogSheetState(kind = mutableDraft.value.kind, date = mutableDraft.value.date, today = clock.today())

    /** Replaces the draft (e.g. once a plan has loaded); the date still can't be in the future. */
    fun prefill(
        transform: (LogDraft) -> LogDraft,
    ) = mutableDraft.update { d -> transform(d).let { it.copy(people = it.people.distinct(), date = minOf(it.date, clock.today())) } }

    /** Opens the sheet on [entry] as [request] has it (issue #102); saving then edits it instead of logging anew. */
    fun startEditing(entry: TimelineEntry, request: LogRequest) {
        editing = entry
        prefill {
            it.copy(
                kind = request.kind,
                people = request.friendIds,
                date = request.date ?: it.date,
                note = request.note,
                placeName = request.placeName.orEmpty(),
                location = request.location,
                activity = ActivityChoice.of(request.activity, request.activityName),
                label = request.label,
                circleId = null,
                editing = true,
                time = request.time,
            )
        }
    }

    fun onKind(kind: LogKind) = mutableDraft.update { it.copy(kind = kind) }

    fun onPeople(ids: List<FriendId>) = mutableDraft.update { it.copy(people = ids.distinct()) }

    /** Picks a circle (null = none): its members are listed unticked; whoever is already ticked stays ticked. */
    fun onCircle(id: String?) = mutableDraft.update { it.copy(circleId = id) }

    fun onTogglePerson(id: FriendId) = mutableDraft.update {
        if (id in
            it.people
        ) {
            it.copy(people = it.people - id)
        } else {
            it.copy(people = it.people + id)
        }
    }

    /** Issue #116: Now (null), a quick time of day or a picked time. */
    fun onTime(time: LocalTime?) = mutableDraft.update { it.copy(time = time) }

    /** Future days can't be picked; they fall back to today. */
    fun onDate(date: LocalDate) = mutableDraft.update { it.copy(date = minOf(date, clock.today())) }

    fun onNote(note: String) = mutableDraft.update { it.copy(note = note) }

    /** The "Other" label, at most [LogInteractionUseCase.LABEL_MAX] characters. */
    fun onLabel(label: String) = mutableDraft.update { it.copy(label = label.take(LogInteractionUseCase.LABEL_MAX)) }

    fun onPlaceName(name: String) = mutableDraft.update { it.copy(placeName = name) }

    fun onActivity(activity: ActivityChoice?) = mutableDraft.update { it.copy(activity = activity) }

    /**
     * "Use my location", once the permission prompt has answered (issue #114: the sheets never asked, so on Android
     * nothing happened). Denied or no fix shows why under the button.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) onUseLocation() else mutableDraft.update { it.copy(locationProblem = LocationProblem.DENIED) }
    }

    fun onUseLocation() {
        mutableDraft.update { it.copy(isLocating = true, locationProblem = null) }
        scope.launch {
            val point = runCatching { currentLocation() }.getOrNull()
            mutableDraft.update {
                it.copy(
                    isLocating = false,
                    location = point ?: it.location,
                    locationProblem = LocationProblem.UNAVAILABLE.takeIf { point == null },
                )
            }
        }
    }

    fun onClearLocation() = mutableDraft.update { it.copy(location = null, placeChanged = false) }

    /** F11: the place named itself, but it's somewhere else: type its name instead. */
    fun onChangePlace() = mutableDraft.update { it.copy(placeChanged = true, placeName = "") }

    /** Saves the log; [onSaved] runs once it's written (the sheet then closes). Nothing happens if it can't be saved. */
    fun onSave(onSaved: () -> Unit) {
        val d = mutableDraft.value
        val current = sheet.value
        if (!current.canSave) return
        // At a named place the meetup takes its name (CheckInUseCase matches it again from the fix).
        val placeName = current.place.match?.place?.name ?: d.placeName
        // Only people who exist: an app-icon shortcut can name someone deleted since (goodcompany://log/<id>).
        val people = current.people
        mutableDraft.update { it.copy(saving = true) }
        val meetup = d.kind == LogKind.MEETUP
        val request = LogRequest(
            friendIds = people.map { it.id },
            kind = d.kind,
            date = d.date,
            note = d.note,
            placeName = placeName.takeIf { meetup },
            location = d.location.takeIf { meetup },
            activity = d.activity?.builtIn.takeIf { meetup },
            activityName = d.activity?.customName.takeIf { meetup },
            label = d.label.takeIf { d.kind == LogKind.OTHER }.orEmpty(),
            time = d.time,
        )
        val edited = editing
        if (edited != null && editEntry != null) {
            scope.launch {
                val saved = runCatching { editEntry.invoke(edited, request) }.isSuccess
                mutableDraft.update { it.copy(saving = false) }
                if (saved) onSaved()
            }
            return
        }
        scope.launch {
            val result = runCatching { logInteraction(request) }.getOrNull()
            // Answering a plan: close that occurrence (the meetup, call or chat is already logged), remembering it for Undo.
            val before = if (plan != null && (result is LogResult.Met || result is LogResult.Contacts)) {
                observePlans().first().firstOrNull { it.id == plan.planId }
                    .also { runCatching { confirmPlan(plan.planId, happened = true, day = plan.date, logCheckIn = false) } }
            } else {
                null
            }
            val answered = when (result) {
                is LogResult.Met -> result.copy(plan = before)
                is LogResult.Contacts -> result.copy(plan = before)
                else -> result
            }
            mutableDraft.update { it.copy(saving = false) }
            // "Logged time with … · Undo" for meetups and contacts alike (issue #101); journal entries have their own.
            if (answered is LogResult.Met || answered is LogResult.Contacts) loggedMeetups.offer(LoggedMeetup(answered, people))
            if (answered != null) onSaved()
        }
    }
}
