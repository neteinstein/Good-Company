package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.ui.navigation.TopLevelDestination
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchOptionsTest {
    @Test
    fun parsesQuery() {
        val o = LaunchOptions.parse("?demo&tab=Together&theme=dark&friend=sample-ana")
        assertTrue(o.loadSampleData)
        assertEquals(TopLevelDestination.TOGETHER, o.startTab)
        assertEquals(ThemeMode.DARK, o.theme)
        assertEquals("sample-ana", o.openFriendId)
        assertEquals(LaunchOptions(), LaunchOptions.parse(""))
        assertEquals(null, LaunchOptions.parse("?tab=nope").startTab)
        assertTrue(LaunchOptions.parse("?review").showReview)
    }

    @Test
    fun appliesDemoAndTheme() = runTest {
        val settings = FakeSettingsRepository()
        val friends = FakeFriendRepository()
        ApplyLaunchOptions(
            LoadSampleDataUseCase(
                friends,
                FakeHangoutRepository(),
                FakeReminderRepository(),
                FakeIdeaRepository(),
                FakeGroupRepository(),
                FixedClock(),
            ),
            UpdateSettingsUseCase(settings),
        )(LaunchOptions(loadSampleData = true, theme = ThemeMode.DARK))
        assertTrue(settings.settings.value.onboardingDone)
        assertEquals(ThemeMode.DARK, settings.settings.value.themeMode)
        assertFalse(settings.settings.value.dynamicColor)
        assertTrue(friends.friends.value.isNotEmpty())
    }
}
