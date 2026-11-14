package com.neteinstein.goodcompany.feature.settings.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.usecase.DeletePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MergePlacesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlacesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PlaceSummary
import com.neteinstein.goodcompany.core.domain.usecase.RenamePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlaceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface PlacesMessage {
    data class Deleted(val name: String) : PlacesMessage

    data class Merged(val into: String) : PlacesMessage
}

data class PlacesUiState(
    /** Most visited first. */
    val places: List<PlaceSummary> = emptyList(),
    /** True until the stored places have been read once. */
    val isLoading: Boolean = true,
    /** Place deleted a moment ago, offered for undo. */
    val recentlyDeleted: NamedPlace? = null,
    val message: PlacesMessage? = null,
)

/** Settings → Places (F11): rename (every meetup there follows), delete (with Undo), merge two names for one spot. */
class PlacesViewModel(
    observePlaces: ObservePlacesUseCase,
    private val renamePlace: RenamePlaceUseCase,
    private val deletePlace: DeletePlaceUseCase,
    private val restorePlace: RestorePlaceUseCase,
    private val mergePlaces: MergePlacesUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(PlacesUiState())

    val state: StateFlow<PlacesUiState> = combine(observePlaces(), local) { places, ui -> ui.copy(places = places, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())

    fun onRename(id: String, name: String) {
        viewModelScope.launch { renamePlace(id, name) }
    }

    fun onDelete(id: String) {
        viewModelScope.launch {
            val removed = deletePlace(id) ?: return@launch
            local.update { it.copy(recentlyDeleted = removed, message = PlacesMessage.Deleted(removed.name)) }
        }
    }

    fun onUndoDelete() {
        val place = local.value.recentlyDeleted ?: return
        viewModelScope.launch {
            restorePlace(place)
            local.update { it.copy(recentlyDeleted = null) }
        }
    }

    /** [fromId]'s meetups move to [intoId], which keeps its name. */
    fun onMerge(fromId: String, intoId: String) {
        val into = state.value.places.firstOrNull { it.place.id == intoId }?.place ?: return
        viewModelScope.launch {
            mergePlaces(fromId, intoId)
            local.update { it.copy(recentlyDeleted = null, message = PlacesMessage.Merged(into.name)) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
