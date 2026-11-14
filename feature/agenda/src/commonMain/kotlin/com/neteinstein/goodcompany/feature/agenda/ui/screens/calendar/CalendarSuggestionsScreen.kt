package com.neteinstein.goodcompany.feature.agenda.ui.screens.calendar

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.usecase.CalendarMatchKind
import com.neteinstein.goodcompany.core.domain.usecase.CalendarSuggestion
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.showUndo
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.core.ui.format.weekdayShort
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.cal_empty_body
import com.neteinstein.goodcompany.feature.agenda.resources.cal_empty_title
import com.neteinstein.goodcompany.feature.agenda.resources.cal_log_it
import com.neteinstein.goodcompany.feature.agenda.resources.cal_looking_back
import com.neteinstein.goodcompany.feature.agenda.resources.cal_matched_both
import com.neteinstein.goodcompany.feature.agenda.resources.cal_matched_email
import com.neteinstein.goodcompany.feature.agenda.resources.cal_matched_name
import com.neteinstein.goodcompany.feature.agenda.resources.cal_msg_never
import com.neteinstein.goodcompany.feature.agenda.resources.cal_msg_no
import com.neteinstein.goodcompany.feature.agenda.resources.cal_never
import com.neteinstein.goodcompany.feature.agenda.resources.cal_no
import com.neteinstein.goodcompany.feature.agenda.resources.cal_screen_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.back as backLabel
import com.neteinstein.goodcompany.core.ui.resources.undo as undoLabel

suspend fun CalendarSuggestionsMessage.text(): String = when (this) {
    is CalendarSuggestionsMessage.Decided -> when (decision.kind) {
        CalendarDecisionKind.NO -> getString(Res.string.cal_msg_no, title)
        CalendarDecisionKind.NEVER -> getString(Res.string.cal_msg_never, title)
    }
}

data class CalendarSuggestionsActions(
    val onBack: () -> Unit = {},
    /** Opens the log sheet prefilled with the suggestion's people, day and activity guess. */
    val onLog: (CalendarSuggestion) -> Unit = {},
    val onNo: (CalendarSuggestion) -> Unit = {},
    val onNever: (CalendarSuggestion) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun CalendarSuggestionsScreen(
    onBack: () -> Unit,
    onLog: (CalendarSuggestion) -> Unit,
    viewModel: CalendarSuggestionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarSuggestionsContent(
        state,
        CalendarSuggestionsActions(
            onBack = onBack,
            onLog = onLog,
            onNo = viewModel::onNo,
            onNever = viewModel::onNever,
            onUndo = viewModel::onUndo,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSuggestionsContent(state: CalendarSuggestionsUiState, actions: CalendarSuggestionsActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undoLabel)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showUndo(message.text(), undo)
        if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onMessageShown()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.cal_screen_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.backLabel)) }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            if (!state.loading && state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = stringResource(Res.string.cal_empty_title),
                        message = stringResource(Res.string.cal_empty_body),
                    )
                }
            }
            items(state.recent, key = { it.key }) { SuggestionCard(it, state.zone, actions, Modifier.animateItem()) }
            if (state.lookingBack.isNotEmpty()) {
                item(key = "looking-back") {
                    Text(
                        stringResource(Res.string.cal_looking_back),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(state.lookingBack, key = { it.key }) { SuggestionCard(it, state.zone, actions, Modifier.animateItem()) }
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: CalendarSuggestion, zone: TimeZone, actions: CalendarSuggestionsActions, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StackedAvatars(s.friends, size = 44.dp, max = 3)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.event.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        detailLine(s, zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        actions.onLog(s)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.cal_log_it)) }
                OutlinedButton(onClick = { actions.onNo(s) }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cal_no)) }
            }
            if (s.isRepeating) {
                TextButton(onClick = { actions.onNever(s) }) { Text(stringResource(Res.string.cal_never, s.event.title)) }
            }
        }
    }
}

/** "Fri, Nov 6 · 13:00 · Personal · matched by name". */
@Composable
private fun detailLine(s: CalendarSuggestion, zone: TimeZone): String {
    val start = s.event.start.toLocalDateTime(zone)
    val matched = stringResource(
        when {
            s.matchedBy.size > 1 -> Res.string.cal_matched_both
            CalendarMatchKind.EMAIL in s.matchedBy -> Res.string.cal_matched_email
            else -> Res.string.cal_matched_name
        },
    )
    return listOfNotNull(
        "${weekdayShort(start.dayOfWeek)}, ${s.date.shortLabel()}",
        start.timeLabel().takeUnless { s.event.allDay },
        s.event.calendarName.takeIf { it.isNotBlank() },
        matched,
    ).joinToString(" · ")
}
