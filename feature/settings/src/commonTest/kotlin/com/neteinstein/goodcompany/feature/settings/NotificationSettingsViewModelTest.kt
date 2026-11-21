package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.NotificationSettings
import com.neteinstein.goodcompany.core.domain.model.NotificationType
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsCard
import com.neteinstein.goodcompany.feature.settings.ui.screens.notifications.NotificationSettingsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.notifications.NotificationSettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationSettingsViewModelTest {
    private val g = TestGraph()

    private fun vm() = NotificationSettingsViewModel(g.observeSettings, g.updateSettings, g.updateNotificationSettings, g.sendTestNotification)

    @Test
    fun switchesSaveEachTypeTheMasterAndKeepOnScreen() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(NotificationSettings(), state.value.settings.notifications)
        NotificationType.entries.forEach { vm.onTypeChange(it, false) }
        vm.onPersistentChange(false)
        vm.onEnabledChange(false)
        val n = state.value.settings.notifications
        assertFalse(n.enabled)
        assertFalse(n.persistent)
        assertEquals(NotificationSettings(false, false, false, false, false, false, false, false, dailyLog = false), n)
        vm.onTypeChange(NotificationType.PLANS, true)
        assertTrue(state.value.settings.notifications.plans)
    }

    @Test
    fun theDailyLogTimeIsSaved() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(kotlinx.datetime.LocalTime(21, 0), state.value.settings.notifications.dailyLogTime)
        vm.onDailyLogTimeChange(kotlinx.datetime.LocalTime(20, 30))
        assertEquals(20 * 60 + 30, state.value.settings.notifications.dailyLogMinute)
    }

    @Test
    fun theBirthdayHeadsUpIsAWeekByDefault_andTakesOnlyTheOfferedChoices() = runViewModelTest {
        // Issue #133.
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(7, state.value.settings.notifications.birthdayHeadsUpDays)
        vm.onBirthdayHeadsUpChange(2)
        assertEquals(2, state.value.settings.notifications.birthdayHeadsUpDays)
        vm.onBirthdayHeadsUpChange(5)
        assertEquals(2, state.value.settings.notifications.birthdayHeadsUpDays)
        vm.onBirthdayHeadsUpChange(0)
        assertEquals(0, state.value.settings.notifications.birthdayHeadsUpDays)
        assertTrue(state.value.settings.notifications.birthdays, "Off for the heads-up leaves the day's notification on")
    }

    @Test
    fun theSettingsCardsKeepClosenessRightAfterReminders() {
        // Issue #133: Appearance · AI · Reminders · Closeness · Your data.
        assertEquals(
            listOf(SettingsCard.APPEARANCE, SettingsCard.AI, SettingsCard.REMINDERS, SettingsCard.CLOSENESS, SettingsCard.YOUR_DATA),
            SettingsCard.ORDER,
        )
    }

    @Test
    fun hourDigestAndPrivacy() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onReminderHourChange(42)
        vm.onWeeklyDigestChange(false)
        vm.onDigestDayChange(9)
        vm.onPrivateChange(true)
        val s = state.value.settings
        assertEquals(23, s.reminderHour)
        assertFalse(s.weeklyDigest)
        assertEquals(7, s.digestDay)
        assertTrue(s.privateNotifications)
    }

    @Test
    fun testNotificationAndPermissionMessages() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSendTest()
        assertEquals("test", g.scheduler.shownNow.single().id)
        assertEquals(NotificationSettingsMessage.TestSent, state.value.message)
        vm.onMessageShown()
        assertNull(state.value.message)
        vm.onPermissionResult(false)
        assertEquals(NotificationSettingsMessage.Permission(false), state.value.message)
    }
}
