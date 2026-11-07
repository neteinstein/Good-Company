package com.neteinstein.goodcompany.core.ui

import com.neteinstein.goodcompany.core.ui.format.parseTags
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FormattingTest {
    private val today = LocalDate(2026, 3, 10)
    private val now = LocalDateTime(2026, 3, 10, 18, 0).toInstant(TimeZone.UTC)

    @Test
    fun relativeDays() {
        assertEquals("Today", relativeDay(today, today))
        assertEquals("Tomorrow", relativeDay(LocalDate(2026, 3, 11), today))
        assertEquals("In 4 days", relativeDay(LocalDate(2026, 3, 14), today))
        assertEquals("Mar 30", relativeDay(LocalDate(2026, 3, 30), today))
        assertEquals("Yesterday", relativeDay(LocalDate(2026, 3, 9), today))
    }

    @Test
    fun relativeDayPhrases() {
        assertEquals("today", relativeDayPhrase(today, today))
        assertEquals("tomorrow", relativeDayPhrase(LocalDate(2026, 3, 11), today))
        assertEquals("in 4 days", relativeDayPhrase(LocalDate(2026, 3, 14), today))
        assertEquals("on Mar 30", relativeDayPhrase(LocalDate(2026, 3, 30), today))
    }

    @Test
    fun relativePastBuckets() {
        val z = TimeZone.UTC
        assertEquals("just now", relativePast(now, now, z))
        assertEquals("5m ago", relativePast(now - 5.minutes, now, z))
        assertEquals("3h ago", relativePast(now - 3.hours, now, z))
        assertEquals("yesterday", relativePast(now - 20.hours, now, z))
        assertEquals("10 days ago", relativePast(now - 10.days, now, z))
        assertEquals("3 weeks ago", relativePast(now - 21.days, now, z))
        assertEquals("4 months ago", relativePast(now - 125.days, now, z))
        assertEquals("over a year ago", relativePast(now - 400.days, now, z))
    }

    @Test
    fun labelsAndTags() {
        assertEquals("Dec 1", LocalDate(2026, 12, 1).shortLabel())
        assertEquals(listOf("jazz", "sushi"), parseTags(" jazz, sushi ,, jazz\n"))
    }
}
