package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BirthdayTest {
    @Test
    fun nextOccurrence_laterThisYear() {
        assertEquals(LocalDate(2026, 5, 4), Birthday(5, 4).nextOccurrence(LocalDate(2026, 3, 10)))
    }

    @Test
    fun nextOccurrence_todayCountsAsNext() {
        assertEquals(LocalDate(2026, 3, 10), Birthday(3, 10).nextOccurrence(LocalDate(2026, 3, 10)))
    }

    @Test
    fun nextOccurrence_alreadyPassedRollsToNextYear() {
        assertEquals(LocalDate(2027, 1, 2), Birthday(1, 2).nextOccurrence(LocalDate(2026, 3, 10)))
    }

    @Test
    fun leapDay_fallsBackToFeb28InCommonYears() {
        assertEquals(LocalDate(2027, 2, 28), Birthday(2, 29).nextOccurrence(LocalDate(2026, 3, 10)))
        assertEquals(LocalDate(2028, 2, 29), Birthday(2, 29).nextOccurrence(LocalDate(2027, 3, 1)))
    }

    @Test
    fun ageOn_beforeAndAfterBirthday() {
        val b = Birthday(6, 15, 1990)
        assertEquals(35, b.ageOn(LocalDate(2026, 6, 14)))
        assertEquals(36, b.ageOn(LocalDate(2026, 6, 15)))
    }

    @Test
    fun ageOn_unknownYear() {
        assertNull(Birthday(6, 15).ageOn(LocalDate(2026, 6, 15)))
    }

    @Test
    fun invalidMonthRejected() {
        assertFailsWith<IllegalArgumentException> { Birthday(13, 1) }
    }
}
