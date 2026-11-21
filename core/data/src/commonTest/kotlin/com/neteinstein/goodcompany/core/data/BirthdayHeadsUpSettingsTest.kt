package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.storage.PersistentState
import com.neteinstein.goodcompany.core.domain.model.NotificationSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue #133: "Heads-up before birthdays" in the notification settings JSON (device settings and backups). */
class BirthdayHeadsUpSettingsTest {
    private val json = PersistentState.DefaultJson

    @Test
    fun olderJsonWithoutTheFieldReadsAsAWeek() {
        val old = """{"birthdays":true,"catchUps":false,"dailyLogMinute":1200}"""
        val read = json.decodeFromString(NotificationSettings.serializer(), old)
        assertEquals(7, read.birthdayHeadsUpDays)
        assertEquals(false, read.catchUps)
    }

    @Test
    fun everyChoiceRoundTrips() {
        NotificationSettings.BIRTHDAY_HEADS_UP_CHOICES.forEach { days ->
            val settings = NotificationSettings(birthdayHeadsUpDays = days)
            assertEquals(
                settings,
                json.decodeFromString(NotificationSettings.serializer(), json.encodeToString(NotificationSettings.serializer(), settings)),
            )
        }
        assertEquals(listOf(0, 1, 2, 3, 7, 14), NotificationSettings.BIRTHDAY_HEADS_UP_CHOICES)
    }
}
