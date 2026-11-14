package com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.GiftListDetail
import com.neteinstein.goodcompany.core.domain.usecase.GiftListUseCases
import com.neteinstein.goodcompany.core.domain.usecase.GiftPrices
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ParseKinshipQueryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SuggestGiftsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** One-off messages on a gift list; the screen turns them into text. */
sealed interface GiftListMessage {
    data class Removed(val name: String) : GiftListMessage

    data object NoNewIdeas : GiftListMessage

    data class AiError(val error: Throwable) : GiftListMessage
}

/**
 * "Add people" is the people picker (issue #89): a circle and "Tick all" take a whole circle, the search understands
 * kinship ("my cousins", [GiftListViewModel.kinshipOf]); this is its one optional budget for everyone added.
 */
data class AddPeopleDraft(val budget: String = "")

data class GiftListUiState(
    val detail: GiftListDetail? = null,
    val isLoading: Boolean = true,
    /** Circles with members, Family included: the picker's filter row. */
    val circles: List<FriendGroup> = emptyList(),
    /** AI gift ideas show only while AI is on and a key is saved (F1). */
    val hasAiKey: Boolean = false,
    val adding: AddPeopleDraft? = null,
    val editing: Boolean = false,
    /** AI ideas waiting under a person, kept or dismissed one by one. */
    val suggestions: Map<FriendId, List<String>> = emptyMap(),
    val suggestingFor: FriendId? = null,
    val message: GiftListMessage? = null,
    val recentlyRemoved: GiftList? = null,
    /** A duplicate was made: the screen opens it, then calls [GiftListViewModel.onOpened]. */
    val open: String? = null,
) {
    /** Who "Add people" can pick: everyone not on the list yet. */
    val addable: List<Friend> get() = detail?.others.orEmpty()
}

/** One gift list (F8): people with their gifts, budgets and totals, "Add people" and AI ideas. */
class GiftListViewModel(
    private val listId: String,
    observeGiftList: ObserveGiftListUseCase,
    observeGroups: ObserveGroupsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val lists: GiftListUseCases,
    private val parseKinship: ParseKinshipQueryUseCase,
    private val suggestGifts: SuggestGiftsUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(GiftListUiState())

    val state: StateFlow<GiftListUiState> = combine(local, observeGiftList(listId), observeGroups(), observeSettings()) {
            ui,
            detail,
            groups,
            settings,
        ->
        ui.copy(
            detail = detail,
            isLoading = false,
            circles = groups.map { it.group }.filter { it.memberIds.isNotEmpty() },
            hasAiKey = settings.usableAiKey != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    // --- The list ---

    fun onEdit() = local.update { it.copy(editing = true) }

    fun onDismissEdit() = local.update { it.copy(editing = false) }

    fun onSaveEdit(title: String, occasion: GiftOccasion?, date: LocalDate?) {
        viewModelScope.launch {
            lists.update(listId, title, occasion, date)
            local.update { it.copy(editing = false) }
        }
    }

    fun onDuplicate() {
        viewModelScope.launch {
            val copy = lists.duplicate(listId) ?: return@launch
            local.update { it.copy(open = copy) }
        }
    }

    fun onOpened() = local.update { it.copy(open = null) }

    // --- Adding people ---

    fun onStartAdding() = local.update { it.copy(adding = AddPeopleDraft()) }

    fun onDismissAdding() = local.update { it.copy(adding = null) }

    /** What a search in the picker means as kinship ("my cousins", issue #41); null for a name search. */
    suspend fun kinshipOf(query: String): Set<LinkType>? = parseKinship(query)

    fun onAddBudget(text: String) = local.update { s -> s.copy(adding = s.adding?.copy(budget = text)) }

    /** "Done" in the picker: adds [picked] (in the order they were ticked) with the budget, if any. */
    fun onConfirmAdding(picked: List<FriendId>) {
        val draft = local.value.adding ?: return
        if (picked.isEmpty()) return
        viewModelScope.launch {
            lists.addPeople(listId, picked, GiftPrices.parse(draft.budget)?.amount)
            local.update { it.copy(adding = null) }
        }
    }

    // --- A person ---

    fun onSetBudget(friendId: FriendId, text: String) {
        viewModelScope.launch { lists.setBudget(listId, friendId, GiftPrices.parse(text)?.amount) }
    }

    fun onRemovePerson(friendId: FriendId) {
        val name = state.value.detail?.people?.firstOrNull { it.friend.id == friendId }?.friend?.displayName ?: return
        viewModelScope.launch {
            val before = lists.removePerson(listId, friendId) ?: return@launch
            local.update { it.copy(recentlyRemoved = before, message = GiftListMessage.Removed(name)) }
        }
    }

    fun onUndoRemove() {
        val before = local.value.recentlyRemoved ?: return
        viewModelScope.launch {
            lists.restore(before)
            local.update { it.copy(recentlyRemoved = null) }
        }
    }

    fun onAddGift(friendId: FriendId, title: String, price: String) {
        viewModelScope.launch { lists.addGift(listId, friendId, title, price) }
    }

    fun onAddIdea(friendId: FriendId, giftId: String) {
        viewModelScope.launch { lists.addGift.existing(listId, friendId, giftId) }
    }

    fun onRemoveGift(friendId: FriendId, giftId: String) {
        viewModelScope.launch { lists.removeGift(listId, friendId, giftId) }
    }

    /** Tapping a status chip: to buy → bought → given (recorded on their page) → to buy. */
    fun onAdvance(friendId: FriendId, giftId: String) {
        viewModelScope.launch { lists.advance(listId, friendId, giftId) }
    }

    // --- AI ideas (only while AI is on) ---

    fun onSuggest(friendId: FriendId) {
        if (local.value.suggestingFor != null || !state.value.hasAiKey) return
        viewModelScope.launch {
            local.update { it.copy(suggestingFor = friendId) }
            val result = suggestGifts(friendId)
            local.update { s ->
                result.fold(
                    { ideas ->
                        s.copy(
                            suggestingFor = null,
                            suggestions = if (ideas.isEmpty()) s.suggestions - friendId else s.suggestions + (friendId to ideas),
                            message = if (ideas.isEmpty()) GiftListMessage.NoNewIdeas else s.message,
                        )
                    },
                    { e -> s.copy(suggestingFor = null, message = GiftListMessage.AiError(e)) },
                )
            }
        }
    }

    /** Keeps an AI idea: it goes on the list (and on their page as an idea). */
    fun onKeepSuggestion(friendId: FriendId, title: String) {
        dropSuggestion(friendId, title)
        onAddGift(friendId, title, "")
    }

    fun onDismissSuggestions(friendId: FriendId) = local.update { it.copy(suggestions = it.suggestions - friendId) }

    private fun dropSuggestion(friendId: FriendId, title: String) = local.update { s ->
        val rest = s.suggestions[friendId].orEmpty() - title
        s.copy(suggestions = if (rest.isEmpty()) s.suggestions - friendId else s.suggestions + (friendId to rest))
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
