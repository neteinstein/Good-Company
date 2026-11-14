package com.neteinstein.goodcompany.feature.ideas.ui.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
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
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.HeaderSettingsButton
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.PlanRow
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.YearReviewCard
import com.neteinstein.goodcompany.core.ui.components.calendarEntry
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.platform.rememberCalendarAdder
import com.neteinstein.goodcompany.core.ui.resources.plan_cancelled
import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_new
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.core.ui.resources.plan_skipped
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.feature.ideas.resources.Res
import com.neteinstein.goodcompany.feature.ideas.resources.plans_coming_up
import com.neteinstein.goodcompany.feature.ideas.resources.plans_empty
import com.neteinstein.goodcompany.feature.ideas.resources.plans_free_body
import com.neteinstein.goodcompany.feature.ideas.resources.plans_free_title
import com.neteinstein.goodcompany.feature.ideas.resources.plans_gift_lists_hint
import com.neteinstein.goodcompany.feature.ideas.resources.plans_gift_lists_progress
import com.neteinstein.goodcompany.feature.ideas.resources.plans_gift_lists_title
import com.neteinstein.goodcompany.feature.ideas.resources.plans_ideas_all
import com.neteinstein.goodcompany.feature.ideas.resources.plans_ideas_empty
import com.neteinstein.goodcompany.feature.ideas.resources.plans_ideas_title
import com.neteinstein.goodcompany.feature.ideas.resources.plans_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** The Plans tab (issue #67, was Ideas). Works with AI off: the weekly ideas card is the only AI part (F1). */
@Composable
fun PlansScreen(
    onOpenSettings: () -> Unit,
    onOpenGiftLists: () -> Unit,
    onOpenWhosFree: () -> Unit,
    /** The weekly ideas in full (only reachable while AI is on). */
    onOpenIdeas: () -> Unit,
    onOpenYearReview: (Int) -> Unit,
    /** "Did you meet?" → yes: the "We met" panel on that occurrence (issues #43, #47). */
    onLogPlan: (planId: String, day: LocalDate) -> Unit,
    viewModel: PlansViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addToCalendar = rememberCalendarAdder()
    PlansContent(
        state = state,
        actions = PlansActions(
            onOpenSettings = onOpenSettings,
            onOpenGiftLists = onOpenGiftLists,
            onOpenWhosFree = onOpenWhosFree,
            onOpenIdeas = onOpenIdeas,
            onOpenYearReview = onOpenYearReview,
            onNewPlan = { viewModel.onOpenEditor(null) },
            onChangePlan = viewModel::onOpenEditor,
            onConfirm = { plan, happened -> if (happened) onLogPlan(plan.id, plan.date) else viewModel.onDidNotHappen(plan) },
            onSkip = viewModel::onSkip,
            onCancel = viewModel::onCancel,
            onUndoCancel = viewModel::onUndoCancel,
            onAddToCalendar = { plan -> addToCalendar(plan.calendarEntry(state.friendsOf(plan), circle = state.circleOf(plan))) },
            onMessageShown = viewModel::onMessageShown,
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
}

data class PlansActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenGiftLists: () -> Unit = {},
    val onOpenWhosFree: () -> Unit = {},
    val onOpenIdeas: () -> Unit = {},
    val onOpenYearReview: (Int) -> Unit = {},
    val onNewPlan: () -> Unit = {},
    val onChangePlan: (Plan) -> Unit = {},
    val onConfirm: (Plan, Boolean) -> Unit = { _, _ -> },
    val onSkip: (Plan) -> Unit = {},
    val onCancel: (Plan) -> Unit = {},
    val onUndoCancel: () -> Unit = {},
    val onAddToCalendar: (Plan) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Localised snackbar text for a [PlansMessage]. */
suspend fun PlansMessage.text(): String = when (this) {
    PlansMessage.Saved -> getString(CoreRes.string.plan_saved)
    PlansMessage.Cancelled -> getString(CoreRes.string.plan_cancelled)
    PlansMessage.Closed -> getString(CoreRes.string.plan_closed)
    PlansMessage.Skipped -> getString(CoreRes.string.plan_skipped)
}

@Composable
fun PlansContent(state: PlansUiState, actions: PlansActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showMessage(message.text(), if (state.undoPlan != null) undo else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoCancel()
        actions.onMessageShown()
    }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(Res.string.plans_title),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(onClick = actions.onNewPlan) { Icon(Icons.Rounded.Add, stringResource(CoreRes.string.plan_new)) }
                            HeaderSettingsButton(actions.onOpenSettings)
                        }
                    },
                )
            }
            state.reviewYear?.let { year ->
                item(key = "review") { YearReviewCard(year, onClick = { actions.onOpenYearReview(year) }) }
            }

            item(key = "plans-h") { SectionHeader(stringResource(Res.string.plans_coming_up)) }
            if (!state.isLoading && state.plans.isEmpty()) {
                item(key = "plans-empty") {
                    Text(
                        stringResource(Res.string.plans_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            if (state.plans.isNotEmpty()) {
                item(key = "plans") {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            state.plans.forEach { plan ->
                                PlanRow(
                                    plan = plan,
                                    friends = state.friendsOf(plan),
                                    today = state.today,
                                    onConfirm = { actions.onConfirm(plan, it) },
                                    onAddToCalendar = { actions.onAddToCalendar(plan) },
                                    onChange = { actions.onChangePlan(plan) },
                                    onCancel = { actions.onCancel(plan) },
                                    circle = state.circleOf(plan),
                                    onSkip = { actions.onSkip(plan) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "tiles") {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val list = state.giftList
                    Tile(
                        icon = Icons.Rounded.CardGiftcard,
                        title = stringResource(Res.string.plans_gift_lists_title),
                        body = list?.let {
                            stringResource(Res.string.plans_gift_lists_progress, it.list.title, it.bought + it.given, it.gifts)
                        } ?: stringResource(Res.string.plans_gift_lists_hint),
                        onClick = actions.onOpenGiftLists,
                        modifier = Modifier.weight(1f),
                    )
                    Tile(
                        icon = Icons.Rounded.Groups,
                        title = stringResource(Res.string.plans_free_title),
                        body = stringResource(Res.string.plans_free_body),
                        onClick = actions.onOpenWhosFree,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // F1: nothing AI shows while AI is off.
            if (state.aiEnabled) {
                item(key = "ideas") { IdeasCard(state, actions.onOpenIdeas) }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
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
