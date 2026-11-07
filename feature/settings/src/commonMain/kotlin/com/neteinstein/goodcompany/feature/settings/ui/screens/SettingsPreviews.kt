package com.neteinstein.goodcompany.feature.settings.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.ui.platform.PlatformCapabilities
import com.neteinstein.goodcompany.core.ui.theme.GoodCompanyTheme

@Preview
@Composable
private fun SettingsPreview() = GoodCompanyTheme(ThemeMode.DARK, dynamicColor = false) {
    SettingsContent(
        state = SettingsUiState(AppSettings(aiApiKey = "sk-ant-xxxxxxxx1234", homeArea = "Lisbon"), maskedKey = "sk-ant-…1234"),
        capabilities = PlatformCapabilities(
            "Preview",
            hasAddressBook = true,
            hasCallLog = false,
            hasBackgroundNotifications = true,
            hasDynamicColor = true,
        ),
        appVersion = "1.0.0",
        actions = SettingsActions(),
    )
}
