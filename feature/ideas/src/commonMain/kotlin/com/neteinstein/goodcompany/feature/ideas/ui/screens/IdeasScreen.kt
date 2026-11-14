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
import com.neteinstein.goodcompany.core.domain.usecase.WeeklyIdeasOutcome
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SendMessageSheet
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import com.neteinstein.goodcompany.feature.ideas.resources.Res
import com.neteinstein.goodcompany.feature.ideas.resources.empty_body
import com.neteinstein.goodcompany.feature.ideas.resources.empty_title
import com.neteinstein.goodcompany.feature.ideas.resources.eyebrow
import com.neteinstein.goodcompany.feature.ideas.resources.filter_all
import com.neteinstein.goodcompany.feature.ideas.resources.filter_news
import com.neteinstein.goodcompany.feature.ideas.resources.filter_plans
import com.neteinstein.goodcompany.feature.ideas.resources.filter_talk
import com.neteinstein.goodcompany.feature.ideas.resources.hero_last_run
import com.neteinstein.goodcompany.feature.ideas.resources.hero_opted_in
import com.neteinstein.goodcompany.feature.ideas.resources.hero_run
import com.neteinstein.goodcompany.feature.ideas.resources.hero_searching
import com.neteinstein.goodcompany.feature.ideas.resources.hero_title
import com.neteinstein.goodcompany.feature.ideas.resources.key_banner_body
import com.neteinstein.goodcompany.feature.ideas.resources.key_banner_title
import com.neteinstein.goodcompany.feature.ideas.resources.msg_found
import com.neteinstein.goodcompany.feature.ideas.resources.msg_no_friends
import com.neteinstein.goodcompany.feature.ideas.resources.msg_no_key
import com.neteinstein.goodcompany.feature.ideas.resources.msg_none
import com.neteinstein.goodcompany.feature.ideas.resources.msg_nothing
import com.neteinstein.goodcompany.feature.ideas.resources.names_and
import com.neteinstein.goodcompany.feature.ideas.resources.news_message
import com.neteinstein.goodcompany.feature.ideas.resources.no_key_action
import com.neteinstein.goodcompany.feature.ideas.resources.no_key_body
import com.neteinstein.goodcompany.feature.ideas.resources.no_key_step1
import com.neteinstein.goodcompany.feature.ideas.resources.no_key_step2
import com.neteinstein.goodcompany.feature.ideas.resources.no_key_step3
import com.neteinstein.goodcompany.feature.ideas.resources.no_key_title
import com.neteinstein.goodcompany.feature.ideas.resources.propose_message
import com.neteinstein.goodcompany.feature.ideas.resources.propose_title
import com.neteinstein.goodcompany.feature.ideas.resources.subtitle
import com.neteinstein.goodcompany.feature.ideas.resources.title
import com.neteinstein.goodcompany.feature.ideas.ui.components.IdeaCardView
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** Localised snackbar text for an [IdeasMessage]. */
suspend fun IdeasMessage.text(): String = when (this) {
    is IdeasMessage.Found -> if (count == 0) getString(Res.string.msg_none) else getPluralString(Res.plurals.msg_found, count, count)
    is IdeasMessage.Failed -> aiErrorText(error)
    is IdeasMessage.Skipped -> getString(
        when (reason) {
            WeeklyIdeasOutcome.Reason.NO_API_KEY -> Res.string.msg_no_key
            WeeklyIdeasOutcome.Reason.NO_FRIENDS_OPTED_IN -> Res.string.msg_no_friends
            WeeklyIdeasOutcome.Reason.AI_OFF, WeeklyIdeasOutcome.Reason.DISABLED, WeeklyIdeasOutcome.Reason.NOT_DUE -> Res.string.msg_nothing
        },
    )
    IdeasMessage.PlanSaved -> getString(CoreRes.string.plan_saved)
}

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
        viewModel::onPropose,
        viewModel::onPlan,
    )
    state.planning?.let { card ->
        PlanEditor(
            friends = card.friends,
            today = state.today,
            onDismiss = viewModel::onDismissPlan,
            onSave = viewModel::onSavePlan,
            preselected = card.friends.map { it.id }.toSet(),
            initialTitle = card.idea.title,
        )
    }
    state.proposing?.let { card ->
        val names = friendNames(card.friends.map { it.displayName.substringBefore(' ') })
        SendMessageSheet(
            title = stringResource(Res.string.propose_title, names),
            suggestion = proposalText(card),
            phone = card.friends.singleOrNull()?.phoneNumbers?.firstOrNull(),
            preferred = card.friends.singleOrNull()?.preferredChannel,
            email = card.friends.singleOrNull()?.emails?.firstOrNull()?.address,
            onDismiss = viewModel::onDismissPropose,
            onSent = viewModel::onProposed,
        )
    }
}

/** "Ana", "Ana & Rui", "Ana, Rui & Bea". */
@Composable
fun friendNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> stringResource(Res.string.names_and, names.dropLast(1).joinToString(), names.last())
}

/** The drafted WhatsApp/text message for an idea, in the user's language. */
@Composable
fun proposalText(card: IdeaCard): String {
    val idea = card.idea
    val names = friendNames(card.friends.map { it.displayName.substringBefore(' ') })
    val what = listOfNotNull(idea.title, idea.whenText?.let { "($it)" }).joinToString(" ")
    val base = if (idea.kind ==
        IdeaKind.EVENT
    ) {
        stringResource(Res.string.propose_message, names, what)
    } else {
        stringResource(Res.string.news_message, names, what)
    }
    return listOfNotNull(base, idea.url).joinToString("\n")
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
    onPropose: (String) -> Unit = {},
    onPlan: (String) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.text())
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
                    eyebrow = stringResource(Res.string.eyebrow),
                    title = stringResource(Res.string.title),
                    subtitle = stringResource(Res.string.subtitle),
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
                val options = listOf(
                    null to Res.string.filter_all,
                    IdeaKind.EVENT to Res.string.filter_plans,
                    IdeaKind.NEWS to Res.string.filter_news,
                    IdeaKind.TALKING_POINT to Res.string.filter_talk,
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(options, key = { it.first?.name ?: "all" }) { (kind, label) ->
                        FilterChip(state.kindFilter == kind, { onKindFilter(kind) }, label = { Text(stringResource(label)) })
                    }
                }
            }
            if (!state.isLoading && state.ideas.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(Res.string.empty_title),
                        message = stringResource(Res.string.empty_body),
                    )
                }
            }
            items(state.ideas, key = { it.idea.id }) { card ->
                IdeaCardView(
                    card,
                    now = state.now,
                    zone = state.zone,
                    onDismiss = { onDismiss(card.idea.id) },
                    onPropose = { onPropose(card.idea.id) },
                    onPlan = { onPlan(card.idea.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun Hero(state: IdeasUiState, onRunNow: () -> Unit, onWeeklyToggle: (Boolean) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(OnColorGradients.Ideas)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.hero_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        pluralStringResource(Res.plurals.hero_opted_in, state.optedInFriends, state.optedInFriends).let { base ->
                            state.lastRunAt?.let { stringResource(Res.string.hero_last_run, base, relativePast(it, state.now, state.zone)) } ?: base
                        },
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
                            Text(stringResource(Res.string.hero_searching))
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.hero_run))
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
                Text(stringResource(Res.string.key_banner_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.key_banner_body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NoKey(onOpenSettings: () -> Unit) {
    Column {
        EmptyState(
            icon = Icons.Rounded.Key,
            title = stringResource(Res.string.no_key_title),
            message = stringResource(Res.string.no_key_body),
            actionLabel = stringResource(Res.string.no_key_action),
            onAction = onOpenSettings,
        )
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    stringResource(Res.string.no_key_step1),
                    stringResource(Res.string.no_key_step2),
                    stringResource(Res.string.no_key_step3),
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
