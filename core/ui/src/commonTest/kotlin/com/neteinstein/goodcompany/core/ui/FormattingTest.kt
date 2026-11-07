package com.neteinstein.goodcompany.core.ui

import com.neteinstein.goodcompany.core.ui.format.RelativeDay
import com.neteinstein.goodcompany.core.ui.format.RelativePast
import com.neteinstein.goodcompany.core.ui.format.isFarDate
import com.neteinstein.goodcompany.core.ui.format.parseTags
import com.neteinstein.goodcompany.core.ui.format.urlEncode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FormattingTest {
    private val today = LocalDate(2026, 3, 10)
    private val now = LocalDateTime(2026, 3, 10, 18, 0).toInstant(TimeZone.UTC)

    @Test
    fun relativeDays() {
        assertEquals(RelativeDay.Today, RelativeDay.of(today, today))
        assertEquals(RelativeDay.Tomorrow, RelativeDay.of(LocalDate(2026, 3, 11), today))
        assertEquals(RelativeDay.InDays(4), RelativeDay.of(LocalDate(2026, 3, 14), today))
        assertEquals(RelativeDay.On(LocalDate(2026, 3, 30)), RelativeDay.of(LocalDate(2026, 3, 30), today))
        assertEquals(RelativeDay.Yesterday, RelativeDay.of(LocalDate(2026, 3, 9), today))
        assertEquals(RelativeDay.DaysAgo(3), RelativeDay.of(LocalDate(2026, 3, 7), today))
        assertTrue(isFarDate(LocalDate(2026, 4, 1), today))
    }

    @Test
    fun relativePastBuckets() {
        val z = TimeZone.UTC
        assertEquals(RelativePast.JustNow, RelativePast.of(now, now, z))
        assertEquals(RelativePast.Minutes(5), RelativePast.of(now - 5.minutes, now, z))
        assertEquals(RelativePast.Hours(3), RelativePast.of(now - 3.hours, now, z))
        assertEquals(RelativePast.Yesterday, RelativePast.of(now - 20.hours, now, z))
        assertEquals(RelativePast.Days(10), RelativePast.of(now - 10.days, now, z))
        assertEquals(RelativePast.Weeks(3), RelativePast.of(now - 21.days, now, z))
        assertEquals(RelativePast.Months(4), RelativePast.of(now - 125.days, now, z))
        assertEquals(RelativePast.OverAYear, RelativePast.of(now - 400.days, now, z))
    }

    @Test
    fun tagsAndUrlEncoding() {
        assertEquals(listOf("jazz", "sushi"), parseTags(" jazz, sushi ,, jazz\n"))
        assertEquals("Parab%C3%A9ns%2C%20Ana%21%20%F0%9F%8E%89", urlEncode("Parabéns, Ana! 🎉"))
        assertEquals("a-b_c.d~e", urlEncode("a-b_c.d~e"))
    }
}
