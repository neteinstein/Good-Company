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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionCard
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
            onLoadSampleData = viewModel::onLoadSampleData,
            onRemoveSampleData = viewModel::onRemoveSampleData,
            onReplayIntro = viewModel::onReplayIntro,
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
    val onLoadSampleData: () -> Unit = {},
    val onRemoveSampleData: () -> Unit = {},
    val onReplayIntro: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
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
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ScreenHeader(eyebrow = "Make it yours", title = "Settings")

            SectionCard("Appearance", icon = Icons.Rounded.Palette) {
                val modes = listOf(
                    Triple(ThemeMode.SYSTEM, "Auto", Icons.Rounded.SettingsBrightness),
                    Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                    Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    modes.forEachIndexed { i, (mode, label, icon) ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { actions.onThemeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = { Icon(icon, null, Modifier.size(18.dp)) },
                            label = { Text(label) },
                        )
                    }
                }
                if (capabilities.hasDynamicColor) {
                    SwitchRow("Colours from wallpaper", "Material You dynamic colour", s.dynamicColor, actions.onDynamicColorChange)
                }
            }

            SectionCard("AI ideas", icon = Icons.Rounded.AutoAwesome) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ApiKeyField(state.maskedKey, actions.onSaveApiKey, actions.onClearApiKey)
                    Text(
                        "Uses Claude with your own Anthropic API key (console.anthropic.com). The key stays on this device and is only sent to " +
                            "api.anthropic.com, with the tastes and chat snippets of the friends involved. Usage is billed to your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                SwitchRow("Weekly plans", "Once a week, for friends you opt in", s.weeklyIdeasEnabled, actions.onWeeklyIdeasChange)
            }

            SectionCard("Reminders", icon = Icons.Rounded.Notifications) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (capabilities.hasBackgroundNotifications) {
                        FilledTonalButton(onClick = actions.onEnableNotifications) {
                            Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Allow notifications")
                        }
                    } else {
                        Text(
                            "Browsers can't remind you when the tab is closed — check “Coming up”, or install the mobile app.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text("Birthday & big-day pings at", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 9, 10, 12, 18).forEach { h ->
                            FilterChip(s.reminderHour == h, { actions.onReminderHourChange(h) }, label = { Text("$h:00") })
                        }
                    }
                }
            }

            SectionCard("Your data", icon = Icons.Rounded.Storage) {
                ListItem(
                    headlineContent = {
                        Text(if (state.hasSampleData) "Sample friends are loaded" else "Sample friends", style = MaterialTheme.typography.titleSmall)
                    },
                    supportingContent = {
                        Text(
                            if (state.hasSampleData) {
                                "Remove them — your own friends stay untouched."
                            } else {
                                "Add a demo circle to explore every feature."
                            },
                        )
                    },
                    trailingContent = {
                        AnimatedContent(state.hasSampleData) { loaded ->
                            if (loaded) {
                                OutlinedButton(onClick = actions.onRemoveSampleData) { Text("Remove") }
                            } else {
                                FilledTonalButton(onClick = actions.onLoadSampleData) { Text("Add") }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    modifier = Modifier.clickable(onClick = actions.onReplayIntro),
                    leadingContent = { Icon(Icons.Rounded.Replay, null) },
                    headlineContent = { Text("Replay the intro", style = MaterialTheme.typography.titleSmall) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            SectionCard("Privacy", icon = Icons.Rounded.Shield) {
                Text(
                    "Friends, tastes, kids, meetups and reminders are stored only on this device. " +
                        (if (capabilities.hasAddressBook) "Contacts are read only to build your list. " else "") +
                        (if (capabilities.hasCallLog) "The call log is only read in the sideload build. " else "") +
                        "Nothing is uploaded except the AI requests you trigger. SMS are never read.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Text(
                "Good Company $appVersion · ${capabilities.name} · made with 💛",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp).align(Alignment.CenterHorizontally),
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
            ListItem(
                modifier = Modifier.clickable { editing = true },
                leadingContent = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text("Anthropic API key", style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(maskedKey.orEmpty()) },
                trailingContent = { TextButton(onClick = onClear) { Text("Remove") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            )
        } else {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Anthropic API key") },
                placeholder = { Text("sk-ant-…") },
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide key" else "Show key")
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
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = icon?.let { { Icon(it, null) } },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
