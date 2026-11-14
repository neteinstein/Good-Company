package com.neteinstein.goodcompany.feature.friends.ui.screens.choose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.ContactChoice
import com.neteinstein.goodcompany.core.domain.usecase.ContactList
import com.neteinstein.goodcompany.core.domain.usecase.ContactListFilter
import com.neteinstein.goodcompany.core.domain.usecase.ListContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PickedContactsResult
import com.neteinstein.goodcompany.core.domain.usecase.SaveChosenContactsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A block of the list under a sticky header: the favourites ([letter] null) or one initial ("A", "B", …, "#"). */
data class ContactSection(val letter: String?, val items: List<ContactChoice>)

data class ChooseContactsUiState(
    val isLoading: Boolean = true,
    /** Every contact, favourites first then A–Z. */
    val all: List<ContactChoice> = emptyList(),
    val query: String = "",
    val filter: ContactListFilter = ContactListFilter.FAVOURITES_FIRST,
    /** What [query] and [filter] leave, grouped under sticky headers. */
    val sections: List<ContactSection> = emptyList(),
    /** Contact ids ticked by the user. Nothing is ticked at first. */
    val selected: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    /** Set once the ticked people are saved: the screen closes. */
    val saved: PickedContactsResult? = null,
) {
    val total: Int get() = all.size
    val canSave: Boolean get() = selected.isNotEmpty() && !isSaving

    /** The list read nothing: no permission, or an empty address book. */
    val isEmpty: Boolean get() = !isLoading && all.isEmpty()
}

/**
 * The phone's contacts with checkboxes (decision D2). Only the people ticked are saved; those already friends are shown
 * ticked and can't be changed. Photos are read only for the people saved.
 */
class ChooseContactsViewModel(
    private val listContacts: ListContactsUseCase,
    private val saveChosen: SaveChosenContactsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ChooseContactsUiState())
    val state: StateFlow<ChooseContactsUiState> = _state.asStateFlow()
    private var loading: Job? = null
    private var opened = false

    init {
        reload()
    }

    /**
     * The screen opened. The first time, the list read in `init` is used; when this ViewModel outlives the screen
     * (onboarding shows it outside the navigation graph) a later opening starts again from a fresh read with nothing ticked.
     */
    fun onOpened() {
        if (opened) reload()
        opened = true
    }

    /** Reads the address book again from scratch. */
    fun reload() {
        loading?.cancel()
        _state.value = ChooseContactsUiState()
        loading = viewModelScope.launch {
            val all = listContacts()
            _state.update { it.copy(isLoading = false, all = all).regroup() }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query).regroup() }

    fun onFilterChange(filter: ContactListFilter) = _state.update { it.copy(filter = filter).regroup() }

    /** Ticks or unticks a contact. People already friends stay as they are. */
    fun onToggle(contactId: String) = _state.update { s ->
        val choice = s.all.firstOrNull { it.record.contactId == contactId }
        if (choice == null || choice.alreadyFriend || s.isSaving) return@update s
        s.copy(selected = if (contactId in s.selected) s.selected - contactId else s.selected + contactId)
    }

    /** Saves only the ticked people. */
    fun onSave() {
        val s = _state.value
        if (!s.canSave) return
        val records = s.all.filter { it.record.contactId in s.selected }.map { it.record }
        save(records)
    }

    /** People chosen in the system contact picker instead (when the list is empty because access is off). */
    fun onPicked(records: List<ContactRecord>) {
        if (records.isNotEmpty() && !_state.value.isSaving) save(records)
    }

    private fun save(records: List<ContactRecord>) {
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveChosen(records)
            _state.update { it.copy(isSaving = false, saved = result) }
        }
    }

    private fun ChooseContactsUiState.regroup(): ChooseContactsUiState {
        val visible = ContactList.filter(all, query, filter)
        val favourites = if (filter == ContactListFilter.ALL) emptyList() else visible.filter { it.record.starred }
        val rest = if (favourites.isEmpty()) visible else visible.filterNot { it.record.starred }
        val sections = buildList {
            if (favourites.isNotEmpty()) add(ContactSection(null, favourites))
            rest.groupBy { ContactList.indexLetter(it.record.name) }
                .forEach { (letter, items) -> add(ContactSection(letter, items)) }
        }
        return copy(sections = sections)
    }
}
