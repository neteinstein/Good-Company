package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsViewModelTest {
    private val repo = FakeSettingsRepository()
    private val friends = FakeFriendRepository()
    private val hangouts = FakeHangoutRepository()
    private val reminders = FakeReminderRepository()
    private val ideas = FakeIdeaRepository()

    private fun vm() = SettingsViewModel(
        ObserveSettingsUseCase(repo),
        UpdateSettingsUseCase(repo),
        HasSampleDataUseCase(friends),
        LoadSampleDataUseCase(friends, hangouts, reminders, ideas, FixedClock()),
        RemoveSampleDataUseCase(friends, hangouts, reminders, ideas),
    )

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

    @Test
    fun sampleDataToggleAndReplayIntro() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onLoadSampleData()
        assertEquals(true, state.value.hasSampleData)
        vm.onRemoveSampleData()
        assertEquals(false, state.value.hasSampleData)
        repo.update { it.copy(onboardingDone = true) }
        vm.onReplayIntro()
        assertEquals(false, repo.settings.value.onboardingDone)
    }
}
