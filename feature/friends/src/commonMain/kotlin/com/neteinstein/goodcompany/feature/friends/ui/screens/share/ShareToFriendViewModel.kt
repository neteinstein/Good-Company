package com.neteinstein.goodcompany.feature.friends.ui.screens.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveShareTargetsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RankedFriend
import com.neteinstein.goodcompany.core.domain.usecase.SaveShareRequest
import com.neteinstein.goodcompany.core.domain.usecase.SaveSharedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareChoice
import com.neteinstein.goodcompany.core.domain.usecase.ShareGuesser
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.domain.usecase.ShareMatcher
import com.neteinstein.goodcompany.core.domain.usecase.ShareTarget
import com.neteinstein.goodcompany.core.domain.usecase.SuggestUpdateFromSharedUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

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
    /** Ranked (named, number in the text, recent, then real friends, then A–Z) and filtered by [query]. */
    val friends: List<RankedFriend> = emptyList(),
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
    /** The search matched nobody: offer to add the typed name as a friend (never automatic). */
    val canAddTyped: Boolean get() = query.isNotBlank() && friends.isEmpty() && circles.isEmpty()

    /** Gift ideas are for one person (F17). */
    val canBeGift: Boolean get() = target is PickedTarget.Person
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
            friends = ranked.filter { q.isEmpty() || it.friend.displayName.lowercase().contains(q) },
            circles = targets.circles.filter { q.isEmpty() || it.name.lowercase().contains(q) },
            hasFriends = targets.friends.isNotEmpty(),
            target = target,
            aiEnabled = settings.aiEnabled,
            hasAiKey = settings.usableAiKey != null,
            closed = ui.closed || (content == null && ui.savedTo == null),
            isLoading = false,
        ).let { if (content != null && target != null && preparedFor !== content) prepare(it, content) else it }
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
        )
        local.value = prepared.copy(content = null, friends = emptyList(), circles = emptyList())
        return prepared
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onSelect(friend: Friend) = local.update { it.copy(target = PickedTarget.Person(friend)) }

    fun onSelectCircle(circle: FriendGroup) = local.update { it.copy(target = PickedTarget.Circle(circle, emptyList())) }

    /** "Add <name> as a friend": a shortcut the user taps, never automatic. */
    fun onAddTyped() {
        val name = local.value.query.trim().takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val id = addFriend(name, isCloseFriend = false)
            local.update { it.copy(query = "", target = PickedTarget.Person(Friend(id, name))) }
        }
    }

    fun onChangeFriend() {
        inbox.preselected.value?.let { inbox.offer(inbox.pending.value ?: return, null) }
        local.update { it.copy(target = null) }
    }

    fun onChoice(choice: ShareChoice) = local.update {
        if (choice == ShareChoice.GIFT_IDEA && !it.canBeGift) it else it.copy(choice = choice)
    }

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

    fun onCancel() {
        local.update { it.copy(closed = true) }
        inbox.clear()
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
