package com.neteinstein.goodcompany.feature.hangouts.ui.screens.free

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.usecase.OccasionMatch
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.HeaderBackButton
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.free_days
import com.neteinstein.goodcompany.feature.hangouts.resources.free_due
import com.neteinstein.goodcompany.feature.hangouts.resources.free_hint
import com.neteinstein.goodcompany.feature.hangouts.resources.free_never
import com.neteinstein.goodcompany.feature.hangouts.resources.free_plan
import com.neteinstein.goodcompany.feature.hangouts.resources.free_title
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** "Who's free for…?", pushed from the Plans tab (issue #67). */
@Composable
fun WhosFreeScreen(onBack: () -> Unit, onOpenFriend: (FriendId) -> Unit, viewModel: WhosFreeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WhosFreeContent(
        state = state,
        actions = WhosFreeActions(
            onBack = onBack,
            onOpenFriend = onOpenFriend,
            onSelectOccasion = viewModel::onSelectOccasion,
            onPlanWith = viewModel::onPlanWith,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
    state.planDraft?.let { draft ->
        PlanEditor(
            friends = state.friends,
            today = state.today,
            onDismiss = viewModel::onDismissPlan,
            onSave = viewModel::onSavePlan,
            preselected = setOf(draft.friendId),
            initialTitle = occasionLabel(draft.occasion),
            circles = state.circles,
        )
    }
}

data class WhosFreeActions(
    val onBack: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onSelectOccasion: (Occasion) -> Unit = {},
    val onPlanWith: (FriendId, Occasion) -> Unit = { _, _ -> },
    val onMessageShown: () -> Unit = {},
)

/** Localised snackbar text for a [WhosFreeMessage]. */
suspend fun WhosFreeMessage.text(): String = when (this) {
    WhosFreeMessage.PlanSaved -> getString(CoreRes.string.plan_saved)
}

@Composable
fun WhosFreeContent(state: WhosFreeUiState, actions: WhosFreeActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showMessage(message.text())
        actions.onMessageShown()
    }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item(key = "header") {
                ScreenHeader(title = stringResource(Res.string.free_title), navigation = { HeaderBackButton(actions.onBack) })
            }
            if (state.occasions.isEmpty()) {
                if (!state.isLoading) {
                    item(key = "hint") {
                        Text(
                            stringResource(Res.string.free_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                return@LazyColumn
            }
            item(key = "chips") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.occasions.keys.forEach { o ->
                        FilterChip(o == state.selectedOccasion, { actions.onSelectOccasion(o) }, label = { Text(occasionLabel(o)) })
                    }
                }
            }
            val occasion = state.selectedOccasion
            val matches = occasion?.let { state.occasions[it] }.orEmpty()
            items(matches, key = { "free-${occasion?.name}-${it.friend.id.value}" }) { m ->
                MatchRow(
                    match = m,
                    onClick = { actions.onOpenFriend(m.friend.id) },
                    onPlan = { occasion?.let { actions.onPlanWith(m.friend.id, it) } },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun MatchRow(match: OccasionMatch, onClick: () -> Unit, onPlan: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(if (match.catchUpDue) 1f else 0f, size = 52.dp, stroke = 4.dp, color = MaterialTheme.colorScheme.primary) {
                FriendAvatar(match.friend, size = 40.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(match.friend.displayName, style = MaterialTheme.typography.titleSmall)
                val days = match.daysSinceLastSeen
                Text(
                    when {
                        match.catchUpDue -> stringResource(Res.string.free_due)
                        days != null -> pluralStringResource(Res.plurals.free_days, days, days)
                        else -> stringResource(Res.string.free_never)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onPlan) { Text(stringResource(Res.string.free_plan)) }
        }
    }
}
