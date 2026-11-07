package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsViewModelTest {
    private val g = TestGraph()
    private val repo get() = g.settingsRepo

    private fun vm() = SettingsViewModel(
        g.observeSettings,
        g.updateSettings,
        g.hasSampleData,
        g.loadSampleData,
        g.removeSampleData,
        g.importContactsFile,
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
        assertEquals(SettingsMessage.KeySaved, state.value.message)
        vm.onClearApiKey()
        assertNull(state.value.maskedKey)
        assertEquals(SettingsMessage.KeyRemoved, state.value.message)
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
        vm.onCircleDaysChange(Circle.INNER, 14)
        vm.onCircleDaysChange(Circle.CLOSE, 60)
        vm.onCircleDaysChange(Circle.GOOD, 0)
        vm.onNotificationPermissionResult(false)
        val s = state.value.settings
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(false, s.dynamicColor)
        assertEquals(false, s.weeklyIdeasEnabled)
        assertEquals("Porto", s.homeArea)
        assertEquals(23, s.reminderHour)
        assertEquals(14, s.innerCircleDays)
        assertEquals(60, s.closeCircleDays)
        assertEquals(1, s.goodFriendsDays)
        assertEquals(SettingsMessage.Notifications(false), state.value.message)
        assertEquals("••••", SettingsViewModel.mask("short"))
    }

    @Test
    fun sampleDataToggleAndReplayIntro() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onLoadSampleData()
        assertEquals(true, state.value.hasSampleData)
        assertEquals(SettingsMessage.SampleAdded, state.value.message)
        vm.onRemoveSampleData()
        assertEquals(false, state.value.hasSampleData)
        assertEquals(SettingsMessage.SampleRemoved, state.value.message)
        repo.update { it.copy(onboardingDone = true) }
        vm.onReplayIntro()
        assertEquals(false, repo.settings.value.onboardingDone)
    }

    @Test
    fun importsContactsFile() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactsFilePicked("")
        assertNull(state.value.message)
        vm.onContactsFilePicked("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Marta Reis\r\nBDAY:--0504\r\nEND:VCARD\r\n")
        assertEquals(SettingsMessage.FileImported(ContactsFileImportResult(1, 0, 0)), state.value.message)
        assertEquals("Marta Reis", g.friendRepo.friends.value.single().name)
    }
}
