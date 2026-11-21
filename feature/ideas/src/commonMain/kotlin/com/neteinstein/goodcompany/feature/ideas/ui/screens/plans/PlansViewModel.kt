package com.neteinstein.goodcompany.feature.ideas.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.domain.usecase.ClosePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GiftListSummary
import com.neteinstein.goodcompany.core.domain.usecase.GiftListUseCases
import com.neteinstein.goodcompany.core.domain.usecase.GiftListsOverview
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
import com.neteinstein.goodcompany.feature.ideas.ui.screens.gifts.NewGiftList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface PlansMessage {
    data object Saved : PlansMessage

    data object Cancelled : PlansMessage

    data object Deleted : PlansMessage

    data object Closed : PlansMessage

    data object NotAttended : PlansMessage

    data object Skipped : PlansMessage

    /** A gift list was deleted from its card (issue #104); Undo puts it back. */
    data class GiftListDeleted(val title: String) : PlansMessage
}

/**
 * The Gift lists section of the Plans tab (issue #104: the lists open in place instead of on their own screens).
 * [current] and [past] come from the store; the rest is this tab's.
 */
data class GiftListsSection(
    val current: List<GiftListSummary> = emptyList(),
    val past: List<GiftListSummary> = emptyList(),
    /** The lists opened in place, showing their people, gifts, budgets and totals. */
    val expanded: Set<String> = emptySet(),
    /** "+ New list" is open. */
    val creating: NewGiftList? = null,
    /** "Edit list" is open on this list. */
    val editing: GiftList? = null,
    /**
     * Where the tab should scroll: a list's id, or [HEADER] for the section itself. The screen scrolls, then calls
     * [PlansViewModel.onScrolled].
     */
    val scrollTo: String? = null,
) {
    val isEmpty: Boolean get() = current.isEmpty() && past.isEmpty()

    fun find(id: String): GiftList? = (current + past).firstOrNull { it.list.id == id }?.list

    companion object {
        const val HEADER = "gift-lists"
    }
}

/** The plan editor: [plan] null = a new plan ("+"). */
data class PlanEditing(val plan: Plan?)

data class PlansUiState(
    /** Open plans, one-off and recurring (each at its open occurrence), soonest first; past ones ask "Did you meet?". */
    val plans: List<Plan> = emptyList(),
    /** Recently cancelled plans (issue #100), kept and shown struck through: the last month and later, latest first. */
    val cancelled: List<Plan> = emptyList(),
    val friends: List<Friend> = emptyList(),
    /** The circles (for a plan's circle and the editor). */
    val circles: List<FriendGroup> = emptyList(),
    /** Gift lists, each one opened in place (issue #104). */
    val gifts: GiftListsSection = GiftListsSection(),
    /** AI is on and a key is saved (F1): only then the "Ideas for this week" card shows. */
    val aiEnabled: Boolean = false,
    /** The first few weekly ideas, only while [aiEnabled]. */
    val ideas: List<IdeaCard> = emptyList(),
    /** How many weekly ideas there are in all, only while [aiEnabled]. */
    val ideaCount: Int = 0,
    /** "Your year in friendship" in December (issue #67), else null. */
    val reviewYear: Int? = null,
    val editing: PlanEditing? = null,
    /** Plan cancelled or deleted a moment ago, offered for undo (as it was before). */
    val undoPlan: Plan? = null,
    /** Gift list deleted a moment ago, offered for undo. */
    val undoGiftList: GiftList? = null,
    val isLoading: Boolean = true,
    val message: PlansMessage? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
) {
    private val byId: Map<FriendId, Friend> get() = friends.associateBy { it.id }

    fun friendsOf(plan: Plan): List<Friend> = plan.friendIds.mapNotNull { byId[it] }

    fun circleOf(plan: Plan): FriendGroup? = plan.circleId?.let { id -> circles.firstOrNull { it.id == id } }
}

/**
 * The Plans tab (issue #67, was Ideas): plans, gift lists (opened in place, issue #104), "Who's free for…?" and, with
 * AI on, the weekly ideas.
 */
class PlansViewModel(
    observePlans: ObservePlansUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeGroups: ObserveGroupsUseCase,
    private val observeGiftLists: ObserveGiftListsUseCase,
    observeIdeas: ObserveIdeasUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeReviewYear: ObserveYearReviewCardUseCase,
    private val savePlan: SavePlanUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val closePlan: ClosePlanUseCase,
    private val restorePlan: RestorePlanUseCase,
    private val giftLists: GiftListUseCases,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(PlansUiState())

    private val people = combine(observePlans(), observePlans.cancelled(), observeFriends(), observeGroups()) { plans, cancelled, friends, groups ->
        People(plans, cancelled, friends.map { it.friend }, groups.map { it.group })
    }
    private val extras = combine(observeGiftLists(), observeIdeas(), observeSettings(), observeReviewYear()) { gifts, ideas, settings, year ->
        Extras(gifts, ideas, settings.usableAiKey != null, year)
    }

    val state: StateFlow<PlansUiState> = combine(people, extras, local) { (plans, cancelled, friends, circles), extras, ui ->
        val byId = friends.associateBy { it.id }
        val ideas = if (extras.aiEnabled) extras.ideas else emptyList()
        val since = clock.today().minus(CANCELLED_DAYS, DateTimeUnit.DAY)
        ui.copy(
            plans = plans,
            cancelled = cancelled.filter { it.date >= since }.take(CANCELLED_SHOWN),
            friends = friends,
            circles = circles,
            gifts = ui.gifts.copy(current = extras.giftLists.current, past = extras.giftLists.past),
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

    /**
     * An answer from "How did it go?" or the row's menu (issue #100) for that occurrence ([Plan.date] on this tab).
     * "It happened" opens the "We met" panel through navigation instead. Cancel and Delete offer Undo.
     */
    fun onAnswer(plan: Plan, answer: PlanAnswer, wholeSeries: Boolean = false) {
        if (answer == PlanAnswer.HAPPENED) return
        viewModelScope.launch {
            val before = closePlan(plan.id, answer, day = plan.date, wholeSeries = wholeSeries)
            val message = when (answer) {
                PlanAnswer.DIDNT_HAPPEN, PlanAnswer.HAPPENED -> PlansMessage.Closed
                PlanAnswer.NOT_ATTENDED -> PlansMessage.NotAttended
                PlanAnswer.CANCEL -> PlansMessage.Cancelled
                PlanAnswer.DELETE -> PlansMessage.Deleted
            }
            val undo = before.takeIf { answer == PlanAnswer.CANCEL || answer == PlanAnswer.DELETE }
            local.update { it.copy(undoPlan = undo, undoGiftList = null, message = message) }
        }
    }

    /** Delete on a cancelled plan (issue #100), with Undo. */
    fun onDeleteCancelled(plan: Plan) {
        viewModelScope.launch {
            val before = closePlan(plan.id, PlanAnswer.DELETE, wholeSeries = true)
            local.update { it.copy(undoPlan = before, undoGiftList = null, message = PlansMessage.Deleted) }
        }
    }

    /** "Skip this one" on a recurring plan: the series moves on. */
    fun onSkip(plan: Plan) {
        viewModelScope.launch {
            confirmPlan(plan.id, happened = false, day = plan.date)
            local.update { it.copy(message = PlansMessage.Skipped) }
        }
    }

    fun onUndo() {
        val plan = local.value.undoPlan
        val list = local.value.undoGiftList
        if (plan == null && list == null) return
        viewModelScope.launch {
            plan?.let { restorePlan(it) }
            list?.let { giftLists.restore(it) }
            local.update { it.copy(undoPlan = null, undoGiftList = null) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, undoPlan = null, undoGiftList = null) }

    // --- Gift lists (issue #104) ---

    /** Tapping a list's card opens it in place, or closes it again. */
    fun onToggleGiftList(id: String) = gifts { g -> g.copy(expanded = if (id in g.expanded) g.expanded - id else g.expanded + id) }

    fun onNewGiftList() = gifts { it.copy(creating = NewGiftList(occasion = GiftOccasion.CHRISTMAS)) }

    fun onDismissNewGiftList() = gifts { it.copy(creating = null) }

    /** "Create": the new list opens in place and the tab scrolls to it. */
    fun onCreateGiftList(title: String, occasion: GiftOccasion?, date: LocalDate?) {
        viewModelScope.launch {
            val id = giftLists.create(title, occasion, date) ?: return@launch
            gifts { it.copy(creating = null, expanded = it.expanded + id, scrollTo = id) }
        }
    }

    fun onEditGiftList(id: String) {
        // The lists themselves are only in [state] (from the store), not in the tab's own part.
        val list = state.value.gifts.find(id) ?: return
        gifts { it.copy(editing = list) }
    }

    fun onDismissEditGiftList() = gifts { it.copy(editing = null) }

    fun onSaveGiftList(title: String, occasion: GiftOccasion?, date: LocalDate?) {
        val list = local.value.gifts.editing ?: return
        viewModelScope.launch {
            giftLists.update(list.id, title, occasion, date)
            gifts { it.copy(editing = null) }
        }
    }

    /** "Duplicate" keeps people and budgets a year on; the copy opens in place. */
    fun onDuplicateGiftList(id: String) {
        viewModelScope.launch {
            val copy = giftLists.duplicate(id) ?: return@launch
            gifts { it.copy(expanded = it.expanded + copy, scrollTo = copy) }
        }
    }

    fun onDeleteGiftList(id: String) {
        viewModelScope.launch {
            val deleted = giftLists.delete(id) ?: return@launch
            local.update {
                it.copy(
                    gifts = it.gifts.copy(expanded = it.gifts.expanded - id),
                    undoGiftList = deleted,
                    undoPlan = null,
                    message = PlansMessage.GiftListDeleted(deleted.title),
                )
            }
        }
    }

    /**
     * "Gift lists" on a person's gift card (issue #104): opens the list they're on ([GiftListsOverview.listFor]) and
     * scrolls to it; on none, scrolls to the section.
     */
    fun onShowGiftListsFor(friendId: FriendId) {
        viewModelScope.launch {
            val id = observeGiftLists().first().listFor(friendId)
            gifts { g -> if (id == null) g.copy(scrollTo = GiftListsSection.HEADER) else g.copy(expanded = g.expanded + id, scrollTo = id) }
        }
    }

    fun onScrolled() = gifts { it.copy(scrollTo = null) }

    private fun gifts(change: (GiftListsSection) -> GiftListsSection) = local.update { it.copy(gifts = change(it.gifts)) }

    private data class People(
        val plans: List<Plan>,
        val cancelled: List<Plan>,
        val friends: List<Friend>,
        val circles: List<FriendGroup>,
    )

    private data class Extras(
        val giftLists: GiftListsOverview,
        val ideas: List<Idea>,
        val aiEnabled: Boolean,
        val reviewYear: Int?,
    )

    private companion object {
        const val IDEAS_ON_CARD = 2
        const val CANCELLED_DAYS = 30
        const val CANCELLED_SHOWN = 5
    }
}
