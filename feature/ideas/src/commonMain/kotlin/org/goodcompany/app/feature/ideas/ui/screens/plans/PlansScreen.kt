package org.goodcompany.app.feature.ideas.ui.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Plan
import org.goodcompany.app.core.domain.model.PlanAnswer
import org.goodcompany.app.core.ui.components.AppSnackbarHost
import org.goodcompany.app.core.ui.components.CancelledPlanRow
import org.goodcompany.app.core.ui.components.HeaderSettingsButton
import org.goodcompany.app.core.ui.components.PlanEditor
import org.goodcompany.app.core.ui.components.PlanRow
import org.goodcompany.app.core.ui.components.ScreenHeader
import org.goodcompany.app.core.ui.components.SectionCard
import org.goodcompany.app.core.ui.components.SectionHeader
import org.goodcompany.app.core.ui.components.StackedAvatars
import org.goodcompany.app.core.ui.components.YearReviewCard
import org.goodcompany.app.core.ui.components.calendarEntry
import org.goodcompany.app.core.ui.components.showMessage
import org.goodcompany.app.core.ui.platform.rememberCalendarAdder
import org.goodcompany.app.core.ui.resources.plan_cancelled
import org.goodcompany.app.core.ui.resources.plan_closed
import org.goodcompany.app.core.ui.resources.plan_deleted
import org.goodcompany.app.core.ui.resources.plan_new
import org.goodcompany.app.core.ui.resources.plan_not_attended
import org.goodcompany.app.core.ui.resources.plan_saved
import org.goodcompany.app.core.ui.resources.plan_skipped
import org.goodcompany.app.core.ui.resources.undo
import org.goodcompany.app.feature.ideas.resources.Res
import org.goodcompany.app.feature.ideas.resources.gift_lists_deleted
import org.goodcompany.app.feature.ideas.resources.gift_lists_empty
import org.goodcompany.app.feature.ideas.resources.gift_lists_new
import org.goodcompany.app.feature.ideas.resources.gift_lists_past
import org.goodcompany.app.feature.ideas.resources.gift_lists_title
import org.goodcompany.app.feature.ideas.resources.plans_cancelled
import org.goodcompany.app.feature.ideas.resources.plans_coming_up
import org.goodcompany.app.feature.ideas.resources.plans_empty
import org.goodcompany.app.feature.ideas.resources.plans_free_body
import org.goodcompany.app.feature.ideas.resources.plans_free_title
import org.goodcompany.app.feature.ideas.resources.plans_ideas_all
import org.goodcompany.app.feature.ideas.resources.plans_ideas_empty
import org.goodcompany.app.feature.ideas.resources.plans_ideas_title
import org.goodcompany.app.feature.ideas.resources.plans_title
import org.goodcompany.app.feature.ideas.ui.screens.gifts.GiftListCard
import org.goodcompany.app.feature.ideas.ui.screens.gifts.GiftListCardActions
import org.goodcompany.app.feature.ideas.ui.screens.gifts.GiftListEditorDialog
import org.goodcompany.app.feature.ideas.ui.screens.gifts.NewGiftList
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.goodcompany.app.core.ui.resources.Res as CoreRes

/** The Plans tab (issue #67, was Ideas). Works with AI off: the weekly ideas card is the only AI part (F1). */
@Composable
fun PlansScreen(
    onOpenSettings: () -> Unit,
    onOpenWhosFree: () -> Unit,
    /** The weekly ideas in full (only reachable while AI is on). */
    onOpenIdeas: () -> Unit,
    onOpenYearReview: (Int) -> Unit,
    /** "Did you meet?" → yes: the "We met" panel on that occurrence (issues #43, #47). */
    onLogPlan: (planId: String, day: LocalDate) -> Unit,
    /** A person on an open gift list (issue #104). */
    onOpenFriend: (FriendId) -> Unit = {},
    /** Set when a person's "Gift lists" link brought you here (issue #104): their list opens and the tab scrolls to it. */
    showGiftListsFor: FriendId? = null,
    /** Called once [showGiftListsFor] is handled. */
    onShowGiftListsHandled: () -> Unit = {},
    viewModel: PlansViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addToCalendar = rememberCalendarAdder()
    LaunchedEffect(showGiftListsFor) {
        showGiftListsFor?.let {
            viewModel.onShowGiftListsFor(it)
            onShowGiftListsHandled()
        }
    }
    PlansContent(
        state = state,
        actions = PlansActions(
            onOpenSettings = onOpenSettings,
            onOpenWhosFree = onOpenWhosFree,
            onOpenIdeas = onOpenIdeas,
            onOpenYearReview = onOpenYearReview,
            onNewPlan = { viewModel.onOpenEditor(null) },
            onChangePlan = viewModel::onOpenEditor,
            // "It happened" opens the "We met" panel on that occurrence; the other answers close it here (issue #100).
            onAnswer = { plan, answer, whole ->
                if (answer == PlanAnswer.HAPPENED) onLogPlan(plan.id, plan.date) else viewModel.onAnswer(plan, answer, whole)
            },
            onSkip = viewModel::onSkip,
            onDeleteCancelled = viewModel::onDeleteCancelled,
            onUndo = viewModel::onUndo,
            onAddToCalendar = { plan -> addToCalendar(plan.calendarEntry(state.friendsOf(plan), circle = state.circleOf(plan))) },
            onMessageShown = viewModel::onMessageShown,
            onNewGiftList = viewModel::onNewGiftList,
            giftList = GiftListCardActions(
                onToggle = viewModel::onToggleGiftList,
                onEdit = viewModel::onEditGiftList,
                onDuplicate = viewModel::onDuplicateGiftList,
                onDelete = viewModel::onDeleteGiftList,
                onOpenFriend = onOpenFriend,
            ),
            onScrolled = viewModel::onScrolled,
        ),
    )
    state.editing?.let { editing ->
        PlanEditor(
            friends = state.friends,
            today = state.today,
            onDismiss = viewModel::onDismissEditor,
            onSave = viewModel::onSavePlan,
            initial = editing.plan,
            circles = state.circles,
        )
    }
    state.gifts.creating?.let { draft ->
        GiftListEditorDialog(draft, state.today, isNew = true, onDismiss = viewModel::onDismissNewGiftList, onSave = viewModel::onCreateGiftList)
    }
    state.gifts.editing?.let { list ->
        GiftListEditorDialog(
            NewGiftList(list.title, list.occasion, list.occasionDate),
            state.today,
            isNew = false,
            onDismiss = viewModel::onDismissEditGiftList,
            onSave = viewModel::onSaveGiftList,
        )
    }
}

data class PlansActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenWhosFree: () -> Unit = {},
    val onOpenIdeas: () -> Unit = {},
    val onOpenYearReview: (Int) -> Unit = {},
    val onNewPlan: () -> Unit = {},
    val onChangePlan: (Plan) -> Unit = {},
    val onAnswer: (Plan, PlanAnswer, wholeSeries: Boolean) -> Unit = { _, _, _ -> },
    val onSkip: (Plan) -> Unit = {},
    val onDeleteCancelled: (Plan) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onAddToCalendar: (Plan) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onNewGiftList: () -> Unit = {},
    val giftList: GiftListCardActions = GiftListCardActions(),
    val onScrolled: () -> Unit = {},
)

/** Localised snackbar text for a [PlansMessage]. */
suspend fun PlansMessage.text(): String = when (this) {
    PlansMessage.Saved -> getString(CoreRes.string.plan_saved)
    PlansMessage.Cancelled -> getString(CoreRes.string.plan_cancelled)
    PlansMessage.Deleted -> getString(CoreRes.string.plan_deleted)
    PlansMessage.Closed -> getString(CoreRes.string.plan_closed)
    PlansMessage.NotAttended -> getString(CoreRes.string.plan_not_attended)
    PlansMessage.Skipped -> getString(CoreRes.string.plan_skipped)
    is PlansMessage.GiftListDeleted -> getString(Res.string.gift_lists_deleted, title)
}

@Composable
fun PlansContent(state: PlansUiState, actions: PlansActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    val listState = rememberLazyListState()
    val rows = plansRows(state)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val canUndo = state.undoPlan != null || state.undoGiftList != null
        val result = snackbar.showMessage(message.text(), if (canUndo) undo else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndo()
        actions.onMessageShown()
    }
    // Issue #104: a new, duplicated or linked-to gift list is brought into view once it is in the list.
    LaunchedEffect(state.gifts.scrollTo, rows.size) {
        val target = state.gifts.scrollTo ?: return@LaunchedEffect
        val index = rows.indexOfGiftTarget(target) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        actions.onScrolled()
    }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            items(rows, key = { it.key }) { row -> PlansRowContent(row, state, actions, snackbar) }
        }
    }
}

@Composable
private fun LazyItemScope.PlansRowContent(row: PlansRow, state: PlansUiState, actions: PlansActions, snackbar: SnackbarHostState) {
    when (row) {
        PlansRow.Header -> ScreenHeader(
            title = stringResource(Res.string.plans_title),
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(onClick = actions.onNewPlan) { Icon(Icons.Rounded.Add, stringResource(CoreRes.string.plan_new)) }
                    HeaderSettingsButton(actions.onOpenSettings)
                }
            },
        )
        is PlansRow.Review -> YearReviewCard(row.year, onClick = { actions.onOpenYearReview(row.year) })
        PlansRow.PlansHeader -> SectionHeader(stringResource(Res.string.plans_coming_up))
        PlansRow.PlansEmpty -> Text(
            stringResource(Res.string.plans_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        PlansRow.OpenPlans -> PlansCard {
            state.plans.forEach { plan ->
                PlanRow(
                    plan = plan,
                    friends = state.friendsOf(plan),
                    today = state.today,
                    onAnswer = { answer, whole -> actions.onAnswer(plan, answer, whole) },
                    onAddToCalendar = { actions.onAddToCalendar(plan) },
                    onChange = { actions.onChangePlan(plan) },
                    circle = state.circleOf(plan),
                    onSkip = { actions.onSkip(plan) },
                )
            }
        }
        // Issue #100: cancelled plans are kept, struck through.
        PlansRow.CancelledHeader -> SectionHeader(stringResource(Res.string.plans_cancelled))
        PlansRow.Cancelled -> PlansCard {
            state.cancelled.forEach { plan ->
                CancelledPlanRow(
                    plan = plan,
                    friends = state.friendsOf(plan),
                    today = state.today,
                    onDelete = { actions.onDeleteCancelled(plan) },
                    circle = state.circleOf(plan),
                )
            }
        }
        PlansRow.GiftsHeader -> SectionHeader(
            stringResource(Res.string.gift_lists_title),
            action = {
                TextButton(onClick = actions.onNewGiftList) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.gift_lists_new))
                }
            },
        )
        PlansRow.GiftsEmpty -> Text(
            stringResource(Res.string.gift_lists_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        is PlansRow.GiftList -> GiftListCard(
            summary = row.summary,
            expanded = row.expanded,
            past = row.past,
            snackbar = snackbar,
            actions = actions.giftList,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).animateItem(),
        )
        PlansRow.PastGiftsHeader -> SectionHeader(stringResource(Res.string.gift_lists_past))
        PlansRow.WhosFree -> Tile(
            icon = Icons.Rounded.Groups,
            title = stringResource(Res.string.plans_free_title),
            body = stringResource(Res.string.plans_free_body),
            onClick = actions.onOpenWhosFree,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PlansRow.Ideas -> IdeasCard(state, actions.onOpenIdeas)
    }
}

@Composable
private fun PlansCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IdeasCard(state: PlansUiState, onOpenIdeas: () -> Unit) {
    SectionCard(
        title = stringResource(Res.string.plans_ideas_title),
        icon = Icons.Rounded.AutoAwesome,
        action = { TextButton(onClick = onOpenIdeas) { Text(stringResource(Res.string.plans_ideas_all, state.ideaCount)) } },
    ) {
        if (state.ideas.isEmpty()) {
            Text(
                stringResource(Res.string.plans_ideas_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.ideas.forEach { card ->
            Surface(onClick = onOpenIdeas, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StackedAvatars(card.friends, size = 36.dp, max = 2)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.idea.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = listOfNotNull(card.idea.whenText, card.idea.description.takeIf { it.isNotBlank() }).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
