package com.neteinstein.goodcompany.feature.friends.ui.screens.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.usecase.AddLifeUpdateUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.domain.usecase.ShareMatcher
import com.neteinstein.goodcompany.core.domain.usecase.SuggestUpdateFromSharedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface ShareMessage {
    data class NothingFound(val firstName: String) : ShareMessage

    data class AiError(val error: Throwable) : ShareMessage
}

data class ShareUiState(
    val content: SharedContent? = null,
    /** Ranked (people named in the share first) and filtered by [query]. */
    val friends: List<Friend> = emptyList(),
    val mentioned: Set<FriendId> = emptySet(),
    val hasFriends: Boolean = true,
    val query: String = "",
    val selected: Friend? = null,
    val draft: String = "",
    val hasAiKey: Boolean = false,
    val isSuggesting: Boolean = false,
    val message: ShareMessage? = null,
    /** Saved: the screen opens this friend. */
    val savedTo: FriendId? = null,
    /** Cancelled, or the share disappeared: the screen closes. */
    val closed: Boolean = false,
    val isLoading: Boolean = true,
)

/** "Save to a friend": turns something shared from another app into one friend's "What's new" line. */
class ShareToFriendViewModel(
    private val inbox: ShareInbox,
    observeFriends: ObserveFriendsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val addLifeUpdate: AddLifeUpdateUseCase,
    private val suggest: SuggestUpdateFromSharedUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(ShareUiState())

    val state: StateFlow<ShareUiState> = combine(inbox.pending, observeFriends(), observeSettings(), local) { content, all, settings, ui ->
        val friends = all.map { it.friend }
        val q = ui.query.trim().lowercase()
        val ranked = content?.let { ShareMatcher.rank(friends, it) } ?: friends.sortedBy { it.name.lowercase() }
        ui.copy(
            content = content,
            friends = ranked.filter { q.isEmpty() || it.name.lowercase().contains(q) },
            mentioned = content?.let { ShareMatcher.mentions(friends, it) }.orEmpty(),
            hasFriends = friends.isNotEmpty(),
            selected = ui.selected?.let { s -> friends.firstOrNull { it.id == s.id } },
            hasAiKey = settings.hasAiKey,
            closed = ui.closed || (content == null && ui.savedTo == null),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUiState())

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onSelect(friend: Friend) = local.update {
        it.copy(selected = friend, draft = it.draft.ifBlank { inbox.pending.value?.draft().orEmpty() })
    }

    fun onChangeFriend() = local.update { it.copy(selected = null) }

    fun onDraftChange(text: String) = local.update { it.copy(draft = text) }

    fun onSuggest() {
        val friend = local.value.selected ?: return
        val content = inbox.pending.value ?: return
        if (local.value.isSuggesting) return
        viewModelScope.launch {
            local.update { it.copy(isSuggesting = true) }
            val result = suggest(friend.id, content)
            local.update {
                result.fold(
                    { line ->
                        if (line == null) {
                            it.copy(isSuggesting = false, message = ShareMessage.NothingFound(friend.name.substringBefore(' ')))
                        } else {
                            it.copy(isSuggesting = false, draft = line)
                        }
                    },
                    { e -> it.copy(isSuggesting = false, message = ShareMessage.AiError(e)) },
                )
            }
        }
    }

    fun onSave() {
        val friend = local.value.selected ?: return
        val text = local.value.draft
        if (text.isBlank()) return
        viewModelScope.launch {
            if (addLifeUpdate(friend.id, text)) {
                local.update { it.copy(savedTo = friend.id) }
                inbox.clear()
            }
        }
    }

    fun onCancel() {
        local.update { it.copy(closed = true) }
        inbox.clear()
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
