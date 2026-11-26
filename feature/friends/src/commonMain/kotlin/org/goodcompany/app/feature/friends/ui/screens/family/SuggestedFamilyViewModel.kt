package org.goodcompany.app.feature.friends.ui.screens.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.LinkType
import org.goodcompany.app.core.domain.usecase.ConfirmKinshipUseCase
import org.goodcompany.app.core.domain.usecase.DismissKinshipUseCase
import org.goodcompany.app.core.domain.usecase.KinshipProposal
import org.goodcompany.app.core.domain.usecase.ObserveKinshipProposalsUseCase

data class SuggestedFamilyUiState(
    val proposals: List<KinshipProposal> = emptyList(),
    val isLoading: Boolean = true,
)

/** "Suggested family" (F7): every kinship worked out from your relatives' links, each confirmed with one tap. */
class SuggestedFamilyViewModel(
    observe: ObserveKinshipProposalsUseCase,
    private val confirm: ConfirmKinshipUseCase,
    private val dismiss: DismissKinshipUseCase,
) : ViewModel() {
    val state: StateFlow<SuggestedFamilyUiState> = observe()
        .map { SuggestedFamilyUiState(it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuggestedFamilyUiState())

    fun onConfirm(id: FriendId, relation: LinkType) {
        viewModelScope.launch { confirm(id, relation) }
    }

    /** "Not right" / "Not family": not proposed again. */
    fun onDismiss(id: FriendId) {
        val proposal = state.value.proposals.firstOrNull { it.person.id == id } ?: return
        viewModelScope.launch { dismiss(id, proposal.options) }
    }
}
