package com.neteinstein.goodcompany.feature.ideas.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.usecase.CancelPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GiftListSummary
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGiftListsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlansUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveYearReviewCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface PlansMessage {
    data object Saved : PlansMessage

    data object Cancelled : PlansMessage

    data object Closed : PlansMessage

    data object Skipped : PlansMessage
}

/** The plan editor: [plan] null = a new plan ("+"). */
data class PlanEditing(val plan: Plan?)

data class PlansUiState(
    /** Open plans, one-off and recurring (each at its open occurrence), soonest first; past ones ask "Did you meet?". */
    val plans: List<Plan> = emptyList(),
    val friends: List<Friend> = emptyList(),
    /** The circles (for a plan's circle and the editor). */
    val circles: List<FriendGroup> = emptyList(),
    /** The gift list the tile shows: the next current one, or null when there is none. */
    val giftList: GiftListSummary? = null,
    /** AI is on and a key is saved (F1): only then the "Ideas for this week" card shows. */
    val aiEnabled: Boolean = false,
    /** The first few weekly ideas, only while [aiEnabled]. */
    val ideas: List<IdeaCard> = emptyList(),
    /** How many weekly ideas there are in all, only while [aiEnabled]. */
    val ideaCount: Int = 0,
    /** "Your year in friendship" in December (issue #67), else null. */
    val reviewYear: Int? = null,
    val editing: PlanEditing? = null,
    /** Plan cancelled a moment ago, offered for undo. */
    val undoPlan: Plan? = null,
    val isLoading: Boolean = true,
    val message: PlansMessage? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
) {
    private val byId: Map<FriendId, Friend> get() = friends.associateBy { it.id }

    fun friendsOf(plan: Plan): List<Friend> = plan.friendIds.mapNotNull { byId[it] }

    fun circleOf(plan: Plan): FriendGroup? = plan.circleId?.let { id -> circles.firstOrNull { it.id == id } }
}

/** The Plans tab (issue #67, was Ideas): plans, gift lists, "Who's free for…?" and, with AI on, the weekly ideas. */
class PlansViewModel(
    observePlans: ObservePlansUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeGroups: ObserveGroupsUseCase,
    observeGiftLists: ObserveGiftListsUseCase,
    observeIdeas: ObserveIdeasUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeReviewYear: ObserveYearReviewCardUseCase,
    private val savePlan: SavePlanUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val cancelPlan: CancelPlanUseCase,
    private val restorePlan: RestorePlanUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(PlansUiState())

    private val people = combine(observePlans(), observeFriends(), observeGroups()) { plans, friends, groups ->
        Triple(plans, friends.map { it.friend }, groups.map { it.group })
    }
    private val extras = combine(observeGiftLists(), observeIdeas(), observeSettings(), observeReviewYear()) { gifts, ideas, settings, year ->
        Extras(gifts.current.firstOrNull(), ideas, settings.usableAiKey != null, year)
    }

    val state: StateFlow<PlansUiState> = combine(people, extras, local) { (plans, friends, circles), extras, ui ->
        val byId = friends.associateBy { it.id }
        val ideas = if (extras.aiEnabled) extras.ideas else emptyList()
        ui.copy(
            plans = plans,
            friends = friends,
            circles = circles,
            giftList = extras.giftList,
            aiEnabled = extras.aiEnabled,
            ideas = ideas.take(IDEAS_ON_CARD).map { IdeaCard(it, it.friendIds.mapNotNull(byId::get)) },
            ideaCount = ideas.size,
            reviewYear = extras.reviewYear,
            isLoading = false,
            today = clock.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    /** "+" (null) or "Change" on a plan. */
    fun onOpenEditor(plan: Plan?) = local.update { it.copy(editing = PlanEditing(plan)) }

    fun onDismissEditor() = local.update { it.copy(editing = null) }

    fun onSavePlan(input: PlanInput) {
        val editing = local.value.editing ?: return
        if (input.title.isBlank() || (input.friendIds.isEmpty() && input.circleId == null)) return
        viewModelScope.launch {
            savePlan.save(editing.plan?.id, input)
            local.update { it.copy(editing = null, message = PlansMessage.Saved) }
        }
    }

    /** "Didn't happen" on a past occurrence. (A yes opens the "We met" panel, through navigation.) */
    fun onDidNotHappen(plan: Plan) {
        viewModelScope.launch {
            confirmPlan(plan.id, happened = false, day = plan.date)
            local.update { it.copy(message = PlansMessage.Closed) }
        }
    }

    /** "Skip this one" on a recurring plan: the series moves on. */
    fun onSkip(plan: Plan) {
        viewModelScope.launch {
            confirmPlan(plan.id, happened = false, day = plan.date)
            local.update { it.copy(message = PlansMessage.Skipped) }
        }
    }

    fun onCancel(plan: Plan) {
        viewModelScope.launch {
            cancelPlan(plan.id)
            local.update { it.copy(undoPlan = plan, message = PlansMessage.Cancelled) }
        }
    }

    fun onUndoCancel() {
        val plan = local.value.undoPlan ?: return
        viewModelScope.launch {
            restorePlan(plan)
            local.update { it.copy(undoPlan = null) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, undoPlan = null) }

    private data class Extras(
        val giftList: GiftListSummary?,
        val ideas: List<Idea>,
        val aiEnabled: Boolean,
        val reviewYear: Int?,
    )

    private companion object {
        const val IDEAS_ON_CARD = 2
    }
}
