package org.goodcompany.app.feature.friends.ui.screens.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.SharedContent
import org.goodcompany.app.core.domain.usecase.AddFriendUseCase
import org.goodcompany.app.core.domain.usecase.FilteredPeople
import org.goodcompany.app.core.domain.usecase.LogKind
import org.goodcompany.app.core.domain.usecase.ObserveSettingsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveShareTargetsUseCase
import org.goodcompany.app.core.domain.usecase.PeopleFilter
import org.goodcompany.app.core.domain.usecase.PeopleFiltering
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.domain.usecase.RankedFriend
import org.goodcompany.app.core.domain.usecase.SavePlanUseCase
import org.goodcompany.app.core.domain.usecase.SaveShareRequest
import org.goodcompany.app.core.domain.usecase.SaveSharedUseCase
import org.goodcompany.app.core.domain.usecase.ShareChoice
import org.goodcompany.app.core.domain.usecase.ShareGuesser
import org.goodcompany.app.core.domain.usecase.ShareInbox
import org.goodcompany.app.core.domain.usecase.ShareMatcher
import org.goodcompany.app.core.domain.usecase.SharePlace
import org.goodcompany.app.core.domain.usecase.ShareTarget
import org.goodcompany.app.core.domain.usecase.SuggestUpdateFromSharedUseCase
import org.goodcompany.app.core.domain.util.AppClock

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface ShareMessage {
    data class NothingFound(val firstName: String) : ShareMessage

    data class AiError(val error: Throwable) : ShareMessage

    data object SaveFailed : ShareMessage
}

/** Who was picked on the first step: a person or a circle (F12). */
sealed interface PickedTarget {
    data class Person(val friend: Friend) : PickedTarget

    data class Circle(val circle: FriendGroup, val members: List<Friend>) : PickedTarget
}

/** The types offered on the confirm step (mockup: Met, Message, Call, Email). */
val SHARE_KINDS = listOf(LogKind.MEETUP, LogKind.MESSAGE, LogKind.CALL, LogKind.EMAIL)

data class ShareUiState(
    val content: SharedContent? = null,
    /** Ranked (named, number in the text, recent, then real friends, then A–Z) and filtered by [query] and [filter]. */
    val friends: List<RankedFriend> = emptyList(),
    /** The people picker's filter row (issue #89): everyone, a closeness level or a circle. */
    val filter: PeopleFilter = PeopleFilter.Everyone,
    /** Circles for the filter row, Family included. */
    val filterCircles: List<FriendGroup> = emptyList(),
    /** Everyone the filter and search leave who isn't suggested, for the picker's grouped list, with the row's counts. */
    val others: FilteredPeople<Friend> = FilteredPeople(emptyList(), 0, emptyMap()),
    /** F12: circles to save it for, filtered by [query]. */
    val circles: List<FriendGroup> = emptyList(),
    val hasFriends: Boolean = true,
    val query: String = "",
    val target: PickedTarget? = null,
    val choice: ShareChoice = ShareChoice.NOTE,
    val kind: LogKind = LogKind.MESSAGE,
    val date: LocalDate = LocalDate(2000, 1, 1),
    val today: LocalDate = LocalDate(2000, 1, 1),
    val caption: String = "",
    val price: String = "",
    /** Issue #170: the shared maps link's place, editable; null when the share isn't a location. */
    val place: SharePlace? = null,
    /** Issue #170: "Plan here" was chosen, so the plan editor is open with the place filled in. */
    val planEditorOpen: Boolean = false,
    /** Everyone, A–Z, and the circles a plan can be with, for that editor. */
    val everyone: List<Friend> = emptyList(),
    val planCircles: List<FriendGroup> = emptyList(),
    /** The AI switch (F1): off, nothing about AI shows, not even the hint to add a key. */
    val aiEnabled: Boolean = false,
    /** AI is on and a key is saved (F1): every AI button checks this. */
    val hasAiKey: Boolean = false,
    val isSuggesting: Boolean = false,
    val isSaving: Boolean = false,
    val message: ShareMessage? = null,
    /** Saved for a person: the screen opens their page. */
    val savedTo: FriendId? = null,
    /** Cancelled, saved for a circle, or the share disappeared: the screen closes. */
    val closed: Boolean = false,
    val isLoading: Boolean = true,
) {
    /** Suggested on top (named in it, same number, seen today, recent): [friends] with a reason. */
    val suggested: List<RankedFriend> get() = friends.filter { it.reason != null }

    /** The search matched nobody: offer to add the typed name as a friend (never automatic). */
    val canAddTyped: Boolean get() = query.isNotBlank() && friends.isEmpty() && circles.isEmpty()

    /** Gift ideas are for one person (F17). */
    val canBeGift: Boolean get() = target is PickedTarget.Person

    /** Issue #170: the share is a maps link, so the location choices show. */
    val isLocation: Boolean get() = place != null

    /** Home and work addresses are for one person (#169), not a circle. */
    val canBeAddress: Boolean get() = isLocation && target is PickedTarget.Person

    /** A named place needs the link's coordinates (F11: nothing is ever looked up). */
    val canBeNamedPlace: Boolean get() = place?.point != null

    /** Whether [choice] can be picked for this share and target. */
    fun offers(choice: ShareChoice): Boolean = when (choice) {
        ShareChoice.NOTE -> true
        // A location is logged as "We met here" (with the place) rather than as a contact, and isn't a gift.
        ShareChoice.INTERACTION -> !isLocation
        ShareChoice.GIFT_IDEA -> canBeGift && !isLocation
        ShareChoice.PLAN_HERE, ShareChoice.MET_HERE -> isLocation
        ShareChoice.HOME_ADDRESS, ShareChoice.WORK_ADDRESS -> canBeAddress
        ShareChoice.NAMED_PLACE -> canBeNamedPlace
    }

    /** Save is off while the choice is missing what it needs (an address to keep, a name for the place). */
    val canSave: Boolean get() = !isSaving && when (choice) {
        ShareChoice.HOME_ADDRESS, ShareChoice.WORK_ADDRESS -> place?.addressText != null
        ShareChoice.NAMED_PLACE -> place?.label != null && place.point != null
        else -> true
    }
}

/**
 * "Save to a friend" (D6, C1, F12, F17): pick who it's about (or a circle), then whether it counts as an
 * interaction (type, date), is just an idea or note, or is a gift idea, with a one-line caption; then save.
 */
class ShareToFriendViewModel(
    private val inbox: ShareInbox,
    observeTargets: ObserveShareTargetsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val save: SaveSharedUseCase,
    private val suggest: SuggestUpdateFromSharedUseCase,
    private val addFriend: AddFriendUseCase,
    private val savePlan: SavePlanUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(ShareUiState(date = clock.today(), today = clock.today()))

    /** The content the confirm step was last prefilled for, so a new share starts afresh. */
    private var preparedFor: SharedContent? = null

    val state: StateFlow<ShareUiState> = combine(inbox.pending, inbox.preselected, observeTargets(), observeSettings(), local) {
            content,
            preselected,
            targets,
            settings,
            ui,
        ->
        val q = ui.query.trim().lowercase()
        val listed = PeopleFiltering.apply(targets.friends, ui.filter, targets.filterCircles, ui.query)
        val shown = listed.people.mapTo(HashSet()) { it.id }
        val ranked = content?.let { ShareMatcher.rank(targets.friends, it, targets.lastContact, clock.now(), clock.timeZone) }
            ?: targets.friends.sortedBy { it.displayName.lowercase() }.map { RankedFriend(it, null) }
        val byId = targets.friends.associateBy { it.id }
        val target = when (val t = ui.target) {
            is PickedTarget.Person -> byId[t.friend.id]?.let { PickedTarget.Person(it) }
            is PickedTarget.Circle -> targets.circles.firstOrNull { it.id == t.circle.id }?.let { c ->
                PickedTarget.Circle(c, c.memberIds.mapNotNull(byId::get))
            }
            null -> preselected?.let(byId::get)?.takeIf { ui.savedTo == null }?.let { PickedTarget.Person(it) }
        }
        ui.copy(
            content = content,
            friends = ranked.filter { it.friend.id in shown },
            filterCircles = targets.filterCircles,
            others = listed.copy(people = listed.people.filter { f -> ranked.none { it.friend.id == f.id && it.reason != null } }),
            circles = targets.circles.filter { q.isEmpty() || it.name.lowercase().contains(q) },
            hasFriends = targets.friends.isNotEmpty(),
            everyone = targets.friends.sortedBy { it.displayName.lowercase() },
            planCircles = targets.circles,
            target = target,
            aiEnabled = settings.aiEnabled,
            hasAiKey = settings.usableAiKey != null,
            closed = ui.closed || (content == null && ui.savedTo == null),
            isLoading = false,
        ).let { if (content != null && target != null && preparedFor !== content) prepare(it, content) else it }
            .let { s -> if (s.offers(s.choice)) s else s.copy(choice = ShareChoice.NOTE) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** Prefills the confirm step from the guess (photo → Met on its date, chat text → Message, link → just an idea). */
    private fun prepare(ui: ShareUiState, content: SharedContent): ShareUiState {
        preparedFor = content
        val guess = ShareGuesser.guess(content)
        val today = clock.today()
        val prepared = ui.copy(
            choice = guess.choice,
            kind = guess.kind,
            date = guess.date?.takeIf { it <= today } ?: today,
            today = today,
            caption = ui.caption.ifBlank { content.draft() },
            // Issue #170: what the share carried; a bare short link starts with both fields empty.
            place = guess.location?.let(SharePlace::of),
        )
        local.value = prepared.copy(content = null, friends = emptyList(), circles = emptyList())
        return prepared
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    /** The filter row: everyone, a level or a circle, one at a time. */
    fun onFilter(filter: PeopleFilter) = local.update { it.copy(filter = filter) }

    fun onSelect(friend: Friend) = local.update { it.copy(target = PickedTarget.Person(friend)) }

    fun onSelectCircle(circle: FriendGroup) = local.update { it.copy(target = PickedTarget.Circle(circle, emptyList())) }

    /** "Add <name> as a friend": a shortcut the user taps, never automatic. */
    fun onAddTyped() {
        val name = local.value.query.trim().takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val id = addFriend(name)
            local.update { it.copy(query = "", target = PickedTarget.Person(Friend(id, name))) }
        }
    }

    fun onChangeFriend() {
        inbox.preselected.value?.let { inbox.offer(inbox.pending.value ?: return, null) }
        local.update { it.copy(target = null) }
    }

    fun onChoice(choice: ShareChoice) {
        if (state.value.offers(choice)) local.update { it.copy(choice = choice) }
    }

    /** Issue #170: the place's name, as the user corrects it. */
    fun onPlaceName(text: String) = local.update { it.copy(place = it.place?.copy(name = text)) }

    /** Issue #170: the place's address, as the user corrects or types it (nothing is looked up). */
    fun onPlaceAddress(text: String) = local.update { it.copy(place = it.place?.copy(address = text)) }

    fun onKind(kind: LogKind) = local.update { it.copy(kind = kind) }

    fun onDate(date: LocalDate) = local.update { it.copy(date = minOf(date, clock.today())) }

    fun onCaptionChange(text: String) = local.update { it.copy(caption = text) }

    fun onPriceChange(text: String) = local.update { it.copy(price = text) }

    fun onSuggest() {
        val person = (state.value.target as? PickedTarget.Person)?.friend ?: return
        val content = inbox.pending.value ?: return
        if (local.value.isSuggesting) return
        viewModelScope.launch {
            local.update { it.copy(isSuggesting = true) }
            val result = suggest(person.id, content)
            local.update {
                result.fold(
                    { line ->
                        if (line == null) {
                            it.copy(isSuggesting = false, message = ShareMessage.NothingFound(person.displayName.substringBefore(' ')))
                        } else {
                            it.copy(isSuggesting = false, caption = line)
                        }
                    },
                    { e -> it.copy(isSuggesting = false, message = ShareMessage.AiError(e)) },
                )
            }
        }
    }

    fun onSave() {
        val ui = state.value
        val content = inbox.pending.value ?: return
        if (!ui.canSave) return
        if (ui.choice == ShareChoice.PLAN_HERE) {
            local.update { it.copy(planEditorOpen = true) }
            return
        }
        val target = when (val t = ui.target) {
            is PickedTarget.Person -> ShareTarget.Person(t.friend.id)
            is PickedTarget.Circle -> ShareTarget.Circle(t.circle.id)
            null -> return
        }
        if (ui.isSaving) return
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val request = SaveShareRequest(
                target = target,
                content = content,
                choice = ui.choice,
                kind = ui.kind,
                date = ui.date,
                caption = ui.caption,
                price = ui.price.takeIf { ui.choice == ShareChoice.GIFT_IDEA },
                place = ui.place,
            )
            runCatching { save(request) }.fold(
                {
                    local.update {
                        when (target) {
                            is ShareTarget.Person -> it.copy(isSaving = false, savedTo = target.id)
                            is ShareTarget.Circle -> it.copy(isSaving = false, closed = true)
                        }
                    }
                    inbox.clear()
                },
                { local.update { it.copy(isSaving = false, message = ShareMessage.SaveFailed) } },
            )
        }
    }

    fun onDismissPlanEditor() = local.update { it.copy(planEditorOpen = false) }

    /** Issue #170, "Plan here": saves the plan from the editor, with the link and its coordinates; then like a save. */
    fun onSavePlan(input: PlanInput) {
        val ui = state.value
        if (ui.isSaving || inbox.pending.value == null) return
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching { savePlan.save(null, input) }.fold(
                {
                    local.update {
                        val person = (ui.target as? PickedTarget.Person)?.friend?.id
                        if (person != null) {
                            it.copy(isSaving = false, planEditorOpen = false, savedTo = person)
                        } else {
                            it.copy(isSaving = false, planEditorOpen = false, closed = true)
                        }
                    }
                    inbox.clear()
                },
                { local.update { it.copy(isSaving = false, message = ShareMessage.SaveFailed) } },
            )
        }
    }

    fun onCancel() {
        local.update { it.copy(closed = true) }
        inbox.clear()
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
