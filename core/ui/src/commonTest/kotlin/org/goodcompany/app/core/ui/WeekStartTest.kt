package org.goodcompany.app.core.ui

import kotlinx.datetime.DayOfWeek
import org.goodcompany.app.core.ui.platform.dayOfWeekFromIso
import org.goodcompany.app.core.ui.platform.dayOfWeekFromSundayFirst
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue #138: the platforms' week-start numbers. */
class WeekStartTest {
    @Test
    fun sundayFirstNumbers() {
        assertEquals(DayOfWeek.SUNDAY, dayOfWeekFromSundayFirst(1))
        assertEquals(DayOfWeek.MONDAY, dayOfWeekFromSundayFirst(2))
        assertEquals(DayOfWeek.SATURDAY, dayOfWeekFromSundayFirst(7))
        assertEquals(DayOfWeek.MONDAY, dayOfWeekFromSundayFirst(0))
    }

    @Test
    fun isoNumbers() {
        assertEquals(DayOfWeek.MONDAY, dayOfWeekFromIso(1))
        assertEquals(DayOfWeek.SUNDAY, dayOfWeekFromIso(7))
        assertEquals(DayOfWeek.MONDAY, dayOfWeekFromIso(0))
    }
}
