package com.neteinstein.goodcompany.feature.ideas.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.feature.ideas.ui.components.IdeaCardView
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun IdeasScreen(onOpenSettings: () -> Unit, viewModel: IdeasViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IdeasContent(
        state,
        onOpenSettings,
        viewModel::onRunNow,
        viewModel::onWeeklyToggle,
        viewModel::onDismiss,
        viewModel::onMessageShown,
        viewModel::onKindFilter,
    )
}

@Composable
fun IdeasContent(
    state: IdeasUiState,
    onOpenSettings: () -> Unit,
    onRunNow: () -> Unit,
    onWeeklyToggle: (Boolean) -> Unit,
    onDismiss: (String) -> Unit,
    onMessageShown: () -> Unit,
    onKindFilter: (IdeaKind?) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom =
                padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = "Powered by Claude",
                    title = "Ideas",
                    subtitle = "Plans and conversation starters matched to your friends' tastes",
                )
            }
            if (!state.isLoading && !state.hasAiKey && state.ideas.isEmpty() && state.kindFilter == null) {
                item(key = "no-key") { NoKey(onOpenSettings) }
                return@LazyColumn
            }
            if (state.hasAiKey) {
                item(key = "hero") { Hero(state, onRunNow, onWeeklyToggle) }
            } else {
                item(key = "key-banner") { KeyBanner(onOpenSettings) }
            }
            item(key = "filters") {
                val options = listOf(null to "All", IdeaKind.EVENT to "Plans", IdeaKind.NEWS to "News", IdeaKind.TALKING_POINT to "Talk about")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(options, key = {
                        it.second
                    }) { (kind, label) -> FilterChip(state.kindFilter == kind, { onKindFilter(kind) }, label = { Text(label) }) }
                }
            }
            if (!state.isLoading && state.ideas.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "No ideas yet",
                        message = "Turn on “Weekly event ideas” for a few friends, add what they love, then tap “Find plans now”.",
                    )
                }
            }
            items(state.ideas, key = {
                it.idea.id
            }) { card -> IdeaCardView(card, onDismiss = { onDismiss(card.idea.id) }, modifier = Modifier.animateItem()) }
        }
    }
}

@Composable
private fun Hero(state: IdeasUiState, onRunNow: () -> Unit, onWeeklyToggle: (Boolean) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(Color(0xFF6A3DE8), Color(0xFFB4372F), Color(0xFFFF8A65)))),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Weekly plans", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "${state.optedInFriends} friends opted in" +
                            (state.lastRunAt?.let { " · last run ${relativePast(it, state.now, state.zone)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Switch(
                    state.weeklyEnabled,
                    onWeeklyToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color(0xFF6A3DE8)),
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRunNow,
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6A3DE8)),
            ) {
                AnimatedContent(state.isRunning) { running ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF6A3DE8))
                            Spacer(Modifier.width(10.dp))
                            Text("Searching what's on near you…")
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Find plans now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyBanner(onOpenSettings: () -> Unit) {
    Surface(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Add your API key for fresh ideas", style = MaterialTheme.typography.titleSmall)
                Text("Tap to open Settings — the key stays on this device.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NoKey(onOpenSettings: () -> Unit) {
    Column {
        EmptyState(
            icon = Icons.Rounded.Key,
            title = "Bring your own AI key",
            message = "Ideas match your friends' tastes with today's news and events near you, using Claude and your own Anthropic API key.",
            actionLabel = "Add API key in Settings",
            onAction = onOpenSettings,
        )
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Create a key at console.anthropic.com",
                    "Paste it in Settings → AI ideas (it stays on this device)",
                    "On a friend's page, turn on “Weekly event ideas”",
                ).forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
