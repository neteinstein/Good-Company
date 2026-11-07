package com.neteinstein.goodcompany.feature.ideas.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.SoftCard
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.feature.ideas.ui.components.IdeaCardView
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun IdeasScreen(onOpenSettings: () -> Unit, viewModel: IdeasViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IdeasContent(state, onOpenSettings, viewModel::onRunNow, viewModel::onWeeklyToggle, viewModel::onDismiss, viewModel::onMessageShown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasContent(
    state: IdeasUiState,
    onOpenSettings: () -> Unit,
    onRunNow: () -> Unit,
    onWeeklyToggle: (Boolean) -> Unit,
    onDismiss: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Ideas") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (!state.isLoading && !state.hasAiKey) {
                item(key = "no-key") {
                    EmptyState(
                        icon = Icons.Rounded.Key,
                        title = "Bring your own AI key",
                        message = "Ideas match your friends' tastes with current news and events near you. " +
                            "They use Claude with your own Anthropic API key, stored only on this device.",
                        actionLabel = "Add API key",
                        onAction = onOpenSettings,
                    )
                }
                return@LazyColumn
            }
            item(key = "control") {
                SoftCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Weekly plans", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${state.optedInFriends} friends opted in" +
                                        (state.lastRunAt?.let { " · last run ${relativePast(it, state.now, state.zone)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(state.weeklyEnabled, onWeeklyToggle)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRunNow, enabled = !state.isRunning, modifier = Modifier.fillMaxWidth()) {
                            AnimatedContent(state.isRunning) { running ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (running) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Searching the web…")
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
            if (!state.isLoading && state.ideas.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "No ideas yet",
                        message = "Turn on “Weekly event ideas” for a few friends, add what they like, then tap “Find plans now”.",
                    )
                }
            }
            items(state.ideas, key = { it.idea.id }) { card ->
                IdeaCardView(card, onDismiss = { onDismiss(card.idea.id) }, modifier = Modifier.animateItem())
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
