package com.neteinstein.goodcompany.feature.settings.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.usecase.DeletePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MergePlacesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlacesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTypedPlacesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PinTypedPlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PlaceSummary
import com.neteinstein.goodcompany.core.domain.usecase.RenamePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenameTypedPlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.TypedPlace
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

    data class Renamed(val to: String) : PlacesMessage

    /** A typed place got the phone's coordinates (issue #121). */
    data class Pinned(val name: String) : PlacesMessage

    /** "Add coordinates" found no location (permission refused, or no fix). */
    data object NoLocation : PlacesMessage
}

data class PlacesUiState(
    /** Most visited first. */
    val places: List<PlaceSummary> = emptyList(),
    /** Places with no GPS (issue #121): names typed on meetups and plans, most used first. */
    val typed: List<TypedPlace> = emptyList(),
    /** True until the stored places have been read once. */
    val isLoading: Boolean = true,
    /** Place deleted a moment ago, offered for undo. */
    val recentlyDeleted: NamedPlace? = null,
    val message: PlacesMessage? = null,
)

/**
 * Settings → Your data → Places (F11, issue #121). GPS places: rename (every meetup there follows), delete (with Undo;
 * the meetups keep the name, which then shows as a place without GPS), merge two names for one spot. Places without
 * GPS: rename, merge spellings, and "Add coordinates" from where the phone is now.
 */
class PlacesViewModel(
    observePlaces: ObservePlacesUseCase,
    private val renamePlace: RenamePlaceUseCase,
    private val deletePlace: DeletePlaceUseCase,
    private val restorePlace: RestorePlaceUseCase,
    private val mergePlaces: MergePlacesUseCase,
    observeTypedPlaces: ObserveTypedPlacesUseCase,
    private val renameTypedPlace: RenameTypedPlaceUseCase,
    private val pinTypedPlace: PinTypedPlaceUseCase,
    private val currentLocation: GetCurrentLocationUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(PlacesUiState())

    val state: StateFlow<PlacesUiState> = combine(observePlaces(), observeTypedPlaces(), local) { places, typed, ui ->
        ui.copy(places = places, typed = typed, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())

    /** Renames a place without GPS; a name another one already has merges them. */
    fun onRenameTyped(from: String, to: String) {
        val target = to.trim()
        if (target.isEmpty() || target == from) return
        val merges = state.value.typed.any { it.name == target }
        viewModelScope.launch {
            renameTypedPlace(setOf(from), target)
            local.update { it.copy(message = if (merges) PlacesMessage.Merged(target) else PlacesMessage.Renamed(target)) }
        }
    }

    /** "Add coordinates": [allowed] is the location permission's answer. */
    fun onPinTyped(name: String, allowed: Boolean) {
        viewModelScope.launch {
            val at = if (allowed) currentLocation() else null
            val pinned = at?.let { pinTypedPlace(name, it) }
            local.update { it.copy(message = if (pinned != null) PlacesMessage.Pinned(pinned.name) else PlacesMessage.NoLocation) }
        }
    }

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
