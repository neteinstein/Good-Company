package com.neteinstein.goodcompany.feature.settings.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.cal_access_needed
import com.neteinstein.goodcompany.feature.settings.resources.cal_allow
import com.neteinstein.goodcompany.feature.settings.resources.cal_choose
import com.neteinstein.goodcompany.feature.settings.resources.cal_denied
import com.neteinstein.goodcompany.feature.settings.resources.cal_lookback
import com.neteinstein.goodcompany.feature.settings.resources.cal_none_found
import com.neteinstein.goodcompany.feature.settings.resources.cal_pick_hint
import com.neteinstein.goodcompany.feature.settings.resources.cal_privacy
import com.neteinstein.goodcompany.feature.settings.resources.cal_see_suggestions
import com.neteinstein.goodcompany.feature.settings.resources.cal_switch
import com.neteinstein.goodcompany.feature.settings.resources.cal_switch_hint
import com.neteinstein.goodcompany.feature.settings.resources.cal_title
import com.neteinstein.goodcompany.feature.settings.resources.cal_web
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.back as backLabel

suspend fun CalendarSettingsMessage.text(): String = when (this) {
    CalendarSettingsMessage.AccessDenied -> getString(Res.string.cal_denied)
}

data class CalendarSettingsActions(
    val onBack: () -> Unit = {},
    /** Turning on asks for the system permission first (the screen does that). */
    val onToggle: (Boolean) -> Unit = {},
    val onAllowAccess: () -> Unit = {},
    val onChosen: (calendarId: String, chosen: Boolean) -> Unit = { _, _ -> },
    val onOpenSuggestions: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun CalendarSettingsScreen(
    onBack: () -> Unit,
    onOpenSuggestions: () -> Unit,
    viewModel: CalendarSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestAccess = rememberPermissionRequester(AppPermission.CALENDAR, viewModel::onPermissionResult)
    CalendarSettingsContent(
        state = state,
        actions = CalendarSettingsActions(
            onBack = onBack,
            onToggle = { on -> if (on) requestAccess() else viewModel.onEnabled(false) },
            onAllowAccess = requestAccess,
            onChosen = viewModel::onChosen,
            onOpenSuggestions = onOpenSuggestions,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsContent(state: CalendarSettingsUiState, actions: CalendarSettingsActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showMessage(it.text())
            actions.onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.cal_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.backLabel)) }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (!state.supported) {
                Note(stringResource(Res.string.cal_web))
                return@Column
            }
            ListItem(
                modifier = Modifier.clickable(enabled = !state.loading) { actions.onToggle(!state.enabled) },
                headlineContent = { Text(stringResource(Res.string.cal_switch), style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(stringResource(Res.string.cal_switch_hint)) },
                trailingContent = { Switch(state.enabled, actions.onToggle, enabled = !state.loading) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            when {
                state.needsAccess -> {
                    Note(stringResource(Res.string.cal_access_needed))
                    FilledTonalButton(onClick = actions.onAllowAccess, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(Res.string.cal_allow))
                    }
                }
                state.enabled -> {
                    Text(
                        stringResource(Res.string.cal_choose),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                    )
                    if (state.calendars.isEmpty()) Note(stringResource(Res.string.cal_none_found))
                    state.calendars.forEach { row -> CalendarCheckRow(row, actions.onChosen) }
                    if (state.calendars.isNotEmpty() && state.calendars.none { it.chosen }) Note(stringResource(Res.string.cal_pick_hint))
                    if (state.suggestionCount > 0) {
                        Button(onClick = actions.onOpenSuggestions, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text(pluralStringResource(Res.plurals.cal_see_suggestions, state.suggestionCount, state.suggestionCount))
                        }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(Res.string.cal_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Note(stringResource(Res.string.cal_lookback))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CalendarCheckRow(row: CalendarRow, onChosen: (String, Boolean) -> Unit) {
    val c = row.calendar
    ListItem(
        modifier = Modifier.clickable { onChosen(c.id, !row.chosen) },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(row.chosen, onCheckedChange = { onChosen(c.id, it) })
                Box(
                    Modifier.size(12.dp).background(
                        c.color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                )
            }
        },
        headlineContent = { Text(c.name) },
        supportingContent = c.account.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
