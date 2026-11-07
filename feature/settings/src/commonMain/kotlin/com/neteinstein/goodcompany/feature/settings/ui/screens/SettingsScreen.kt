package com.neteinstein.goodcompany.feature.settings.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.SoftCard
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.PlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.platformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(appVersion: String, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestNotifications = rememberPermissionRequester(AppPermission.NOTIFICATIONS, viewModel::onNotificationPermissionResult)
    SettingsContent(
        state = state,
        capabilities = platformCapabilities,
        appVersion = appVersion,
        actions = SettingsActions(
            onSaveApiKey = viewModel::onSaveApiKey,
            onClearApiKey = viewModel::onClearApiKey,
            onThemeChange = viewModel::onThemeChange,
            onDynamicColorChange = viewModel::onDynamicColorChange,
            onWeeklyIdeasChange = viewModel::onWeeklyIdeasChange,
            onHomeAreaChange = viewModel::onHomeAreaChange,
            onReminderHourChange = viewModel::onReminderHourChange,
            onEnableNotifications = requestNotifications,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class SettingsActions(
    val onSaveApiKey: (String) -> Unit = {},
    val onClearApiKey: () -> Unit = {},
    val onThemeChange: (ThemeMode) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onWeeklyIdeasChange: (Boolean) -> Unit = {},
    val onHomeAreaChange: (String) -> Unit = {},
    val onReminderHourChange: (Int) -> Unit = {},
    val onEnableNotifications: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(state: SettingsUiState, capabilities: PlatformCapabilities, appVersion: String, actions: SettingsActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val s = state.settings
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Appearance")
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val modes = listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Rounded.SettingsBrightness),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (mode, label, icon) ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { actions.onThemeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                icon = { Icon(icon, null) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (capabilities.hasDynamicColor) {
                        SwitchRow(
                            Icons.Rounded.Palette,
                            "Colours from wallpaper",
                            "Material You dynamic colour",
                            s.dynamicColor,
                            actions.onDynamicColorChange,
                        )
                    }
                }
            }

            SectionHeader("AI ideas")
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ApiKeyField(state.maskedKey, actions.onSaveApiKey, actions.onClearApiKey)
                    Text(
                        "Matching tastes with news and finding weekly plans uses Claude with your own Anthropic API key " +
                            "(console.anthropic.com). The key stays on this device and is only sent to api.anthropic.com, " +
                            "together with the tastes and chat snippets of the friends involved. Usage is billed to your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchRow(
                        Icons.Rounded.AutoAwesome,
                        "Weekly plans",
                        "Once a week, for friends you opt in",
                        s.weeklyIdeasEnabled,
                        actions.onWeeklyIdeasChange,
                    )
                    var area by rememberSaveable(s.homeArea) { mutableStateOf(s.homeArea) }
                    OutlinedTextField(
                        value = area,
                        onValueChange = {
                            area = it
                            actions.onHomeAreaChange(it)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Place, null) },
                        label = { Text("Your city or area") },
                        placeholder = { Text("Leave empty to use your location") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionHeader("Reminders")
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (capabilities.hasBackgroundNotifications) {
                        FilledTonalButton(onClick = actions.onEnableNotifications) {
                            Icon(Icons.Rounded.Notifications, null)
                            Text("  Allow notifications")
                        }
                    } else {
                        Text(
                            "Browsers can't remind you when the tab is closed — use the Coming up tab, or install the mobile app.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text("Birthday & event pings at", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 9, 10, 12, 18).forEach { h ->
                            FilterChip(s.reminderHour == h, { actions.onReminderHourChange(h) }, label = { Text("$h:00") })
                        }
                    }
                }
            }

            SectionHeader("Privacy")
            SoftCard {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Shield, null) },
                    headlineContent = { Text("Your data stays on this device") },
                    supportingContent = {
                        Text(
                            "Friends, tastes, kids, meetups and reminders are stored locally. " +
                                (if (capabilities.hasAddressBook) "Contacts are read only to build your list. " else "") +
                                (if (capabilities.hasCallLog) "The call log is only read in the sideload build. " else "") +
                                "Nothing is uploaded except the AI requests you trigger. SMS are never read.",
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
            Text(
                "Good Company $appVersion · ${capabilities.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ApiKeyField(maskedKey: String?, onSave: (String) -> Unit, onClear: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(maskedKey != null && !editing) { saved ->
        if (saved) {
            Row {
                ListItem(
                    modifier = Modifier.weight(1f).clickable { editing = true },
                    leadingContent = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Anthropic API key") },
                    supportingContent = { Text(maskedKey.orEmpty()) },
                    trailingContent = { TextButton(onClick = onClear) { Text("Remove") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        } else {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Anthropic API key") },
                placeholder = { Text("sk-ant-…") },
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide" else "Show")
                        }
                        TextButton(
                            onClick = {
                                onSave(draft)
                                draft = ""
                                editing = false
                            },
                            enabled = draft.isNotBlank(),
                        ) { Text("Save") }
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
