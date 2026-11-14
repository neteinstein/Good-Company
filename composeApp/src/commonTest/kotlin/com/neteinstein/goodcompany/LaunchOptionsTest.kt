package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakePlanRepository
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
        assertEquals("sample-ana", LaunchOptions.parse("?demo&story=sample-ana").openStoryFriendId)
    }

    @Test
    fun parsesAWebShare() {
        val o = LaunchOptions.parse("?share_title=Ana%27s+news&share_text=Got+the+job%21+%F0%9F%8E%89&share_url=https%3A%2F%2Fx.io%2Fp")
        assertEquals(SharedContent(text = "Ana's news\nGot the job! 🎉", url = "https://x.io/p"), o.shared)
        assertEquals(null, LaunchOptions.parse("?share_text=+").shared)
        assertEquals("100% sure", LaunchOptions.percentDecode("100%25%20sure"))
        assertEquals("50%", LaunchOptions.percentDecode("50%"))
    }

    @Test
    fun appliesDemoAndTheme() = runTest {
        val settings = FakeSettingsRepository()
        val friends = FakeFriendRepository()
        val inbox = ShareInbox()
        ApplyLaunchOptions(
            LoadSampleDataUseCase(
                friends,
                FakeHangoutRepository(),
                FakeReminderRepository(),
                FakeIdeaRepository(),
                FakeGroupRepository(),
                FakePlanRepository(),
                FakeInteractionRepository(),
                FixedClock(),
            ),
            UpdateSettingsUseCase(settings),
            inbox,
        )(LaunchOptions(loadSampleData = true, theme = ThemeMode.DARK, shared = SharedContent(text = "Got the job!")))
        assertEquals("Got the job!", inbox.pending.value?.text)
        assertTrue(settings.settings.value.onboardingDone)
        assertEquals(ThemeMode.DARK, settings.settings.value.themeMode)
        assertFalse(settings.settings.value.dynamicColor)
        assertTrue(friends.friends.value.isNotEmpty())
    }
}
