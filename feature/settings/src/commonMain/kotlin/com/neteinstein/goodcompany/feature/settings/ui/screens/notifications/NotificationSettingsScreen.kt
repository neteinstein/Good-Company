package com.neteinstein.goodcompany.feature.settings.ui.screens.notifications

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.NotificationType
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.HeaderBackButton
import com.neteinstein.goodcompany.core.ui.components.LocalTimePickerDialog
import com.neteinstein.goodcompany.core.ui.components.LogIcon
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.hhmm
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.weekdayShort
import com.neteinstein.goodcompany.core.ui.platform.currentPlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberNotificationSettingsOpener
import com.neteinstein.goodcompany.core.ui.platform.rememberNotificationsAllowed
import com.neteinstein.goodcompany.core.ui.platform.rememberNotificationsEnabler
import com.neteinstein.goodcompany.core.ui.resources.log_time_pick
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.allow_notifications
import com.neteinstein.goodcompany.feature.settings.resources.digest
import com.neteinstein.goodcompany.feature.settings.resources.digest_day
import com.neteinstein.goodcompany.feature.settings.resources.digest_hint
import com.neteinstein.goodcompany.feature.settings.resources.msg_notifications_off
import com.neteinstein.goodcompany.feature.settings.resources.msg_notifications_on
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_allowed
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_blocked
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_blocked_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_master
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_master_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_msg_test_sent
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_persistent
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_persistent_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_send_test
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_system
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_title
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_types
import com.neteinstein.goodcompany.feature.settings.resources.notif_settings_when
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_birthdays
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_birthdays_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_calendar
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_calendar_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_catch_ups
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_catch_ups_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_daily_log
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_daily_log_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_events
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_events_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_plans
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_plans_hint
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_reminders
import com.neteinstein.goodcompany.feature.settings.resources.notif_type_reminders_hint
import com.neteinstein.goodcompany.feature.settings.resources.pings_at
import com.neteinstein.goodcompany.feature.settings.resources.private_notifications
import com.neteinstein.goodcompany.feature.settings.resources.private_notifications_hint
import com.neteinstein.goodcompany.feature.settings.resources.quiet_note
import com.neteinstein.goodcompany.feature.settings.resources.web_notifications
import com.neteinstein.goodcompany.feature.settings.ui.screens.SwitchRow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

suspend fun NotificationSettingsMessage.text(): String = when (this) {
    is NotificationSettingsMessage.Permission -> getString(if (granted) Res.string.msg_notifications_on else Res.string.msg_notifications_off)
    NotificationSettingsMessage.TestSent -> getString(Res.string.notif_settings_msg_test_sent)
}

data class NotificationSettingsActions(
    val onBack: () -> Unit = {},
    val onEnabledChange: (Boolean) -> Unit = {},
    val onTypeChange: (NotificationType, Boolean) -> Unit = { _, _ -> },
    val onPersistentChange: (Boolean) -> Unit = {},
    val onDailyLogTimeChange: (LocalTime) -> Unit = {},
    val onReminderHourChange: (Int) -> Unit = {},
    val onWeeklyDigestChange: (Boolean) -> Unit = {},
    val onDigestDayChange: (Int) -> Unit = {},
    val onPrivateChange: (Boolean) -> Unit = {},
    /** Asks the system; opens its notification settings when it won't ask again. */
    val onAllow: () -> Unit = {},
    /** The app's notification settings in the system (Android: the channels). */
    val onOpenSystemSettings: () -> Unit = {},
    val onSendTest: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var asked by remember { mutableIntStateOf(0) }
    val enable = rememberNotificationsEnabler { granted ->
        asked++
        viewModel.onPermissionResult(granted)
    }
    val allowed = rememberNotificationsAllowed(refreshKey = asked)
    val openSystemSettings = rememberNotificationSettingsOpener()
    NotificationSettingsContent(
        state = state,
        supported = currentPlatformCapabilities().hasBackgroundNotifications,
        allowed = allowed,
        actions = NotificationSettingsActions(
            onBack = onBack,
            onEnabledChange = viewModel::onEnabledChange,
            onTypeChange = viewModel::onTypeChange,
            onPersistentChange = viewModel::onPersistentChange,
            onDailyLogTimeChange = viewModel::onDailyLogTimeChange,
            onReminderHourChange = viewModel::onReminderHourChange,
            onWeeklyDigestChange = viewModel::onWeeklyDigestChange,
            onDigestDayChange = viewModel::onDigestDayChange,
            onPrivateChange = viewModel::onPrivateChange,
            onAllow = enable,
            onOpenSystemSettings = openSystemSettings,
            onSendTest = viewModel::onSendTest,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

private data class TypeRow(val type: NotificationType, val icon: ImageVector, val title: StringResource, val hint: StringResource)

private val typeRows = listOf(
    TypeRow(NotificationType.BIRTHDAYS, Icons.Rounded.Cake, Res.string.notif_type_birthdays, Res.string.notif_type_birthdays_hint),
    TypeRow(NotificationType.EVENTS, Icons.Rounded.Event, Res.string.notif_type_events, Res.string.notif_type_events_hint),
    TypeRow(NotificationType.REMINDERS, Icons.Rounded.Schedule, Res.string.notif_type_reminders, Res.string.notif_type_reminders_hint),
    TypeRow(NotificationType.PLANS, Icons.Rounded.EventAvailable, Res.string.notif_type_plans, Res.string.notif_type_plans_hint),
    TypeRow(NotificationType.CATCH_UPS, Icons.Rounded.Favorite, Res.string.notif_type_catch_ups, Res.string.notif_type_catch_ups_hint),
    TypeRow(NotificationType.CALENDAR, Icons.Rounded.CalendarMonth, Res.string.notif_type_calendar, Res.string.notif_type_calendar_hint),
    TypeRow(NotificationType.DAILY_LOG, LogIcon, Res.string.notif_type_daily_log, Res.string.notif_type_daily_log_hint),
)

/** Issue #116: the quick times for the daily log reminder (21:00 is the default); any other is picked. */
private val DAILY_LOG_TIMES = listOf(LocalTime(19, 0), LocalTime(20, 0), LocalTime(21, 0), LocalTime(22, 0))

/**
 * Stateless content. [supported] is false on the web (no background notifications); [allowed] is null until the
 * system has answered whether the app may post.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions,
    supported: Boolean = true,
    allowed: Boolean? = true,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showMessage(it.text())
            actions.onMessageShown()
        }
    }
    val s = state.settings
    val n = s.notifications
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(Res.string.notif_settings_title), navigation = { HeaderBackButton(actions.onBack) })
            if (!supported) {
                Note(stringResource(Res.string.web_notifications))
                return@Column
            }
            PermissionCard(allowed, actions)
            SectionCard(stringResource(Res.string.notif_settings_types), icon = Icons.Rounded.Notifications) {
                SwitchRow(
                    stringResource(Res.string.notif_settings_master),
                    stringResource(Res.string.notif_settings_master_hint),
                    n.enabled,
                    actions.onEnabledChange,
                    enabled = !state.loading,
                )
                typeRows.forEach { row ->
                    SwitchRow(
                        stringResource(row.title),
                        stringResource(row.hint),
                        n.enabled && n.allows(row.type),
                        { actions.onTypeChange(row.type, it) },
                        icon = row.icon,
                        enabled = n.enabled && !state.loading,
                    )
                    if (row.type == NotificationType.DAILY_LOG && n.enabled && n.dailyLog) DailyLogTime(n.dailyLogTime, actions.onDailyLogTimeChange)
                }
                SwitchRow(
                    stringResource(Res.string.notif_settings_persistent),
                    stringResource(Res.string.notif_settings_persistent_hint),
                    n.enabled && n.persistent,
                    actions.onPersistentChange,
                    icon = Icons.Rounded.PushPin,
                    enabled = n.enabled && !state.loading,
                )
            }
            SectionCard(stringResource(Res.string.notif_settings_when), icon = Icons.Rounded.Schedule) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.pings_at), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 9, 10, 12, 18).forEach { h ->
                            FilterChip(s.reminderHour == h, { actions.onReminderHourChange(h) }, label = { Text("$h:00") })
                        }
                    }
                }
                SwitchRow(stringResource(Res.string.digest), stringResource(Res.string.digest_hint), s.weeklyDigest, actions.onWeeklyDigestChange)
                if (s.weeklyDigest) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.digest_day), style = MaterialTheme.typography.labelLarge)
                        DayOfWeek.entries.forEach { day ->
                            val iso = day.isoDayNumber
                            FilterChip(s.digestDay == iso, { actions.onDigestDayChange(iso) }, label = { Text(weekdayShort(day)) })
                        }
                    }
                }
                SwitchRow(
                    stringResource(Res.string.private_notifications),
                    stringResource(Res.string.private_notifications_hint),
                    s.privateNotifications,
                    actions.onPrivateChange,
                )
                Note(stringResource(Res.string.quiet_note))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Issue #116: when the daily log reminder comes: a few evening times, or any time picked. */
@Composable
private fun DailyLogTime(time: LocalTime, onChange: (LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 56.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DAILY_LOG_TIMES.forEach { t -> FilterChip(time == t, { onChange(t) }, label = { Text(t.hhmm()) }) }
        val other = time !in DAILY_LOG_TIMES
        FilterChip(
            other,
            { picking = true },
            label = { Text(if (other) time.hhmm() else stringResource(CoreRes.string.log_time_pick)) },
            leadingIcon = { Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp)) },
        )
    }
    if (picking) LocalTimePickerDialog(time, onDismiss = { picking = false }, onPicked = onChange)
}

/** Whether notifications are allowed, with a way to fix it, the system settings and a test notification. */
@Composable
private fun PermissionCard(allowed: Boolean?, actions: NotificationSettingsActions) {
    SectionCard(
        stringResource(if (allowed == false) Res.string.notif_settings_blocked else Res.string.notif_settings_allowed),
        icon = if (allowed == false) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
    ) {
        if (allowed == false) Note(stringResource(Res.string.notif_settings_blocked_hint))
        FlowRowButtons(allowed, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(allowed: Boolean?, actions: NotificationSettingsActions) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowed == false) {
            FilledTonalButton(onClick = actions.onAllow) {
                Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.allow_notifications))
            }
        } else {
            FilledTonalButton(onClick = actions.onSendTest, enabled = allowed == true) {
                Text(stringResource(Res.string.notif_settings_send_test))
            }
        }
        OutlinedButton(onClick = actions.onOpenSystemSettings) { Text(stringResource(Res.string.notif_settings_system)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
