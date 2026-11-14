package com.neteinstein.goodcompany.feature.hangouts.ui.screens.free

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveOccasionMatchesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.OccasionMatch
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
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
sealed interface WhosFreeMessage {
    data object PlanSaved : WhosFreeMessage
}

/** The plan editor, opened from a friend's "Plan" for an occasion. */
data class FreePlanDraft(val friendId: FriendId, val occasion: Occasion)

data class WhosFreeUiState(
    /** Friends per occasion (only occasions someone is tagged with), due friends first. */
    val occasions: Map<Occasion, List<OccasionMatch>> = emptyMap(),
    val selectedOccasion: Occasion? = null,
    /** For the plan editor. */
    val friends: List<Friend> = emptyList(),
    val circles: List<FriendGroup> = emptyList(),
    val planDraft: FreePlanDraft? = null,
    val isLoading: Boolean = true,
    val message: WhosFreeMessage? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
)

/** "Who's free for…?" (moved from Together to the Plans tab, issue #67). */
class WhosFreeViewModel(
    observeOccasions: ObserveOccasionMatchesUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeGroups: ObserveGroupsUseCase,
    private val savePlan: SavePlanUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(WhosFreeUiState())

    val state: StateFlow<WhosFreeUiState> = combine(observeOccasions(), observeFriends(), observeGroups(), local) { occasions, friends, groups, ui ->
        ui.copy(
            occasions = occasions,
            selectedOccasion = ui.selectedOccasion?.takeIf { it in occasions } ?: occasions.keys.firstOrNull(),
            friends = friends.map { it.friend },
            circles = groups.map { it.group },
            isLoading = false,
            today = clock.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhosFreeUiState())

    fun onSelectOccasion(occasion: Occasion) = local.update { it.copy(selectedOccasion = occasion) }

    fun onPlanWith(friendId: FriendId, occasion: Occasion) = local.update { it.copy(planDraft = FreePlanDraft(friendId, occasion)) }

    fun onDismissPlan() = local.update { it.copy(planDraft = null) }

    /** Saves the plan; with nobody picked it is with the friend whose "Plan" was tapped. */
    fun onSavePlan(input: PlanInput) {
        val draft = local.value.planDraft ?: return
        if (input.title.isBlank()) return
        val withSomeone = if (input.friendIds.isEmpty() && input.circleId == null) input.copy(friendIds = listOf(draft.friendId)) else input
        viewModelScope.launch {
            savePlan.save(null, withSomeone)
            local.update { it.copy(planDraft = null, message = WhosFreeMessage.PlanSaved) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
