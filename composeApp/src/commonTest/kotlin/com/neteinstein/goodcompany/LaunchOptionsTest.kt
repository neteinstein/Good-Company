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
        val o = LaunchOptions.parse("?demo&tab=Circles&theme=dark&friend=sample-ana")
        assertTrue(o.loadSampleData)
        assertEquals(TopLevelDestination.CIRCLES, o.startTab)
        assertEquals(ThemeMode.DARK, o.theme)
        assertEquals("sample-ana", o.openFriendId)
        assertEquals(LaunchOptions(), LaunchOptions.parse(""))
        assertEquals(null, LaunchOptions.parse("?tab=nope").startTab)
        assertTrue(LaunchOptions.parse("?review").showReview)
        assertEquals("sample-ana", LaunchOptions.parse("?demo&story=sample-ana").openStoryFriendId)
    }

    @Test
    fun fourTabs_noAiOnlyTab_startOnToday() {
        assertEquals(
            listOf(TopLevelDestination.TODAY, TopLevelDestination.PEOPLE, TopLevelDestination.CIRCLES, TopLevelDestination.PLANS),
            TopLevelDestination.entries,
        )
        assertEquals(TopLevelDestination.TODAY, LaunchOptions().startDestination)
        // Plans is a tab whatever the AI switch (no more falling back while AI is off).
        assertEquals(TopLevelDestination.PLANS, LaunchOptions.parse("?tab=plans").startDestination)
        assertEquals(TopLevelDestination.PLANS, LaunchOptions.parse("?ai&tab=plans").startDestination)
    }

    @Test
    fun newTabNames() {
        assertEquals(TopLevelDestination.TODAY, LaunchOptions.parse("?tab=today").startTab)
        assertEquals(TopLevelDestination.PEOPLE, LaunchOptions.parse("?tab=people").startTab)
        assertEquals(TopLevelDestination.CIRCLES, LaunchOptions.parse("?tab=circles").startTab)
        assertEquals(TopLevelDestination.PLANS, LaunchOptions.parse("?tab=PLANS").startTab)
    }

    @Test
    fun oldTabNamesMapAcross_andSettingsOpensSettings() {
        assertEquals(TopLevelDestination.PEOPLE, LaunchOptions.parse("?tab=friends").startTab)
        assertEquals(TopLevelDestination.CIRCLES, LaunchOptions.parse("?tab=Together").startTab)
        assertEquals(TopLevelDestination.TODAY, LaunchOptions.parse("?tab=agenda").startTab)
        assertEquals(TopLevelDestination.PLANS, LaunchOptions.parse("?tab=ideas").startTab)
        val settings = LaunchOptions.parse("?demo&tab=settings")
        assertTrue(settings.openSettings)
        assertEquals(null, settings.startTab)
        assertEquals(TopLevelDestination.TODAY, settings.startDestination)
        assertFalse(LaunchOptions.parse("?tab=today").openSettings)
    }

    @Test
    fun parsesAWebShare() {
        val o = LaunchOptions.parse("?share_title=Ana%27s+news&share_text=Got+the+job%21+%F0%9F%8E%89&share_url=https%3A%2F%2Fx.io%2Fp")
        assertEquals(SharedContent(text = "Got the job! 🎉", url = "https://x.io/p", title = "Ana's news"), o.shared)
        assertEquals(SharedContent(text = "Just a title"), LaunchOptions.parse("?share_title=Just+a+title").shared)
        assertEquals(
            SharedContent(url = "https://x.io/p", title = "Page"),
            LaunchOptions.parse("?share_title=Page&share_url=https%3A%2F%2Fx.io%2Fp").shared,
        )
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
        )(LaunchOptions.parse("?demo&ai&theme=dark").copy(shared = SharedContent(text = "Got the job!")))
        assertEquals("Got the job!", inbox.pending.value?.text)
        assertTrue(settings.settings.value.aiEnabled)
        assertTrue(settings.settings.value.onboardingDone)
        assertEquals(ThemeMode.DARK, settings.settings.value.themeMode)
        assertFalse(settings.settings.value.dynamicColor)
        assertTrue(friends.friends.value.isNotEmpty())
    }
}
