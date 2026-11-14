package com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.usecase.GiftListSummary
import com.neteinstein.goodcompany.core.domain.usecase.GiftListUseCases
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGiftListsUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** One-off messages on the gift-lists screen; the screen turns them into text. */
sealed interface GiftListsMessage {
    data class Deleted(val title: String) : GiftListsMessage
}

/** The "New list" dialog: a title, an occasion and an optional date. */
data class NewGiftList(val title: String = "", val occasion: GiftOccasion? = null, val date: LocalDate? = null)

data class GiftListsUiState(
    val current: List<GiftListSummary> = emptyList(),
    val past: List<GiftListSummary> = emptyList(),
    val isLoading: Boolean = true,
    val today: LocalDate,
    val creating: NewGiftList? = null,
    /** A list just created or duplicated: the screen opens it, then calls [GiftListsViewModel.onOpened]. */
    val open: String? = null,
    val message: GiftListsMessage? = null,
    val recentlyDeleted: GiftList? = null,
)

/** Gift lists per occasion (F8): this year's lists, past ones (with "Duplicate"), and "New list". */
class GiftListsViewModel(
    observeGiftLists: ObserveGiftListsUseCase,
    private val lists: GiftListUseCases,
    clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(GiftListsUiState(today = clock.today()))

    val state: StateFlow<GiftListsUiState> = combine(local, observeGiftLists()) { ui, overview ->
        ui.copy(current = overview.current, past = overview.past, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onNewList(suggested: NewGiftList = NewGiftList()) = local.update { it.copy(creating = suggested) }

    fun onDismissNew() = local.update { it.copy(creating = null) }

    fun onCreate(title: String, occasion: GiftOccasion?, date: LocalDate?) {
        viewModelScope.launch {
            val id = lists.create(title, occasion, date) ?: return@launch
            local.update { it.copy(creating = null, open = id) }
        }
    }

    fun onDuplicate(id: String) {
        viewModelScope.launch {
            val copy = lists.duplicate(id) ?: return@launch
            local.update { it.copy(open = copy) }
        }
    }

    fun onOpened() = local.update { it.copy(open = null) }

    fun onDelete(id: String) {
        viewModelScope.launch {
            val deleted = lists.delete(id) ?: return@launch
            local.update { it.copy(recentlyDeleted = deleted, message = GiftListsMessage.Deleted(deleted.title)) }
        }
    }

    fun onUndoDelete() {
        val list = local.value.recentlyDeleted ?: return
        viewModelScope.launch {
            lists.restore(list)
            local.update { it.copy(recentlyDeleted = null) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
