package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsViewModelTest {
    private val repo = FakeSettingsRepository()
    private fun vm() = SettingsViewModel(ObserveSettingsUseCase(repo), UpdateSettingsUseCase(repo))

    @Test
    fun apiKeyIsTrimmedMaskedAndRemovable() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveApiKey("   ")
        assertNull(state.value.maskedKey)
        vm.onSaveApiKey("  sk-ant-api03-abcdefghij1234  ")
        assertEquals("sk-ant-api03-abcdefghij1234", repo.settings.value.aiApiKey)
        assertEquals("sk-ant-…1234", state.value.maskedKey)
        vm.onClearApiKey()
        assertNull(state.value.maskedKey)
    }

    @Test
    fun preferencesUpdate() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onThemeChange(ThemeMode.DARK)
        vm.onDynamicColorChange(false)
        vm.onWeeklyIdeasChange(false)
        vm.onHomeAreaChange("Porto")
        vm.onReminderHourChange(42)
        val s = state.value.settings
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(false, s.dynamicColor)
        assertEquals(false, s.weeklyIdeasEnabled)
        assertEquals("Porto", s.homeArea)
        assertEquals(23, s.reminderHour)
        assertEquals("••••", SettingsViewModel.mask("short"))
    }
}
