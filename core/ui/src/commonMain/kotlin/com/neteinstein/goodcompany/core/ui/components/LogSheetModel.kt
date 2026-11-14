package com.neteinstein.goodcompany.core.ui.components

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
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
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** What's typed or picked in a log so far; the friends themselves come from the repository. */
data class LogDraft(
    val kind: LogKind = LogKind.MEETUP,
    val people: List<FriendId> = emptyList(),
    val date: LocalDate,
    val note: String = "",
    val placeName: String = "",
    val location: GeoPoint? = null,
    val isLocating: Boolean = false,
    /** "Change" was tapped on a place that named itself: a new name is typed instead. */
    val placeChanged: Boolean = false,
    val activity: HangoutActivity? = null,
    val saving: Boolean = false,
    val circleId: String? = null,
)

/**
 * The state and actions behind the "Log" sheet (issue #21) and the small "We met" panel (issue #47), so both share one
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
) {
    private val mutableDraft = MutableStateFlow(initial.copy(people = initial.people.distinct(), date = minOf(initial.date, clock.today())))

    /** What's chosen so far ("More details" carries it over to the full Log sheet). */
    val draft: StateFlow<LogDraft> = mutableDraft.asStateFlow()

    val sheet: StateFlow<LogSheetState> = combine(
        combine(observeFriends(), observePlaceBook(), ::Pair),
        observeActivities(),
        observeCircles(),
        mutableDraft,
    ) {
            (friends, places),
            activities,
            circles,
            d,
        ->
        val byId = friends.associateBy { it.friend.id }
        val circle = circles.firstOrNull { it.group.id == d.circleId }
        val everyone = friends.map { it.friend }
        LogSheetState(
            kind = d.kind,
            people = d.people.mapNotNull { byId[it]?.friend },
            everyone = everyone.sortedBy { it.displayName.lowercase() },
            date = d.date,
            today = clock.today(),
            note = d.note,
            place = meetupPlaceState(d.placeName, d.location, d.isLocating, d.placeChanged, places, d.people, everyone),
            activity = d.activity,
            activities = activities,
            saving = d.saving,
            circles = circles.map { it.group }.sortedBy { it.name.lowercase() },
            circle = circle?.group,
            circleMembers = circle?.members.orEmpty(),
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
                        activity = p.activity ?: it.activity,
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

    /** Future days can't be picked; they fall back to today. */
    fun onDate(date: LocalDate) = mutableDraft.update { it.copy(date = minOf(date, clock.today())) }

    fun onNote(note: String) = mutableDraft.update { it.copy(note = note) }

    fun onPlaceName(name: String) = mutableDraft.update { it.copy(placeName = name) }

    fun onActivity(activity: HangoutActivity?) = mutableDraft.update { it.copy(activity = activity) }

    fun onUseLocation() {
        mutableDraft.update { it.copy(isLocating = true) }
        scope.launch {
            val point = currentLocation()
            mutableDraft.update { it.copy(isLocating = false, location = point ?: it.location) }
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
        scope.launch {
            val meetup = d.kind == LogKind.MEETUP
            val result = runCatching {
                logInteraction(
                    LogRequest(
                        friendIds = people.map { it.id },
                        kind = d.kind,
                        date = d.date,
                        note = d.note,
                        placeName = placeName.takeIf { meetup },
                        location = d.location.takeIf { meetup },
                        activity = d.activity.takeIf { meetup },
                    ),
                )
            }.getOrNull()
            // Answering a plan: close that occurrence (the meetup is already logged), remembering it for Undo.
            val answered = if (result is LogResult.Met && plan != null) {
                val before = observePlans().first().firstOrNull { it.id == plan.planId }
                runCatching { confirmPlan(plan.planId, happened = true, day = plan.date, logCheckIn = false) }
                result.copy(plan = before)
            } else {
                result
            }
            mutableDraft.update { it.copy(saving = false) }
            if (answered is LogResult.Met) loggedMeetups.offer(LoggedMeetup(answered, people))
            if (answered != null) onSaved()
        }
    }
}
