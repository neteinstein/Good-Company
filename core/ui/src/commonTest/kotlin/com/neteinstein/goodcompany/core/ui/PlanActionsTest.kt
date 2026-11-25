package com.neteinstein.goodcompany.core.ui

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import com.neteinstein.goodcompany.core.ui.components.asksForSeries
import com.neteinstein.goodcompany.core.ui.components.calendarEntry
import com.neteinstein.goodcompany.core.ui.components.otherAnswers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Issue #136: what sits behind ▾ next to "It happened". */
class PlanActionsTest {
    private val day = LocalDate(2026, 3, 9)
    private fun plan(vararg ids: String, circleId: String? = null, repeat: Repeat? = null) =
        Plan("p", ids.map(::FriendId), "Dinner", day, createdAt = Instant.fromEpochSeconds(0), circleId = circleId, repeat = repeat)

    @Test
    fun theOtherAnswers_withIDidntGoOnlyForAGroupOrACircle() {
        assertEquals(
            listOf(PlanAnswer.DIDNT_HAPPEN, PlanAnswer.CANCEL, PlanAnswer.DELETE),
            plan("ana").otherAnswers(),
        )
        val group = listOf(PlanAnswer.DIDNT_HAPPEN, PlanAnswer.NOT_ATTENDED, PlanAnswer.CANCEL, PlanAnswer.DELETE)
        assertEquals(group, plan("ana", "rui").otherAnswers())
        assertEquals(group, plan("ana", circleId = "surf").otherAnswers())
    }

    @Test
    fun onASeriesOnlyCancelAndDeleteAskThisOneOrTheWholeSeries() {
        val series = plan("ana", repeat = Repeat(1, RepeatUnit.WEEK, day))
        assertTrue(series.asksForSeries(PlanAnswer.CANCEL))
        assertTrue(series.asksForSeries(PlanAnswer.DELETE))
        assertFalse(series.asksForSeries(PlanAnswer.DIDNT_HAPPEN))
        assertFalse(plan("ana").asksForSeries(PlanAnswer.DELETE))
    }

    @Test
    fun addToCalendar_isTimedWithThePlansTime_andAllDayWithout() {
        // Issue #167.
        val allDay = plan("ana").calendarEntry(emptyList())
        assertNull(allDay.time)
        assertNull(allDay.start())
        val timed = plan("ana").copy(time = LocalTime(19, 30)).calendarEntry(emptyList(), on = LocalDate(2026, 3, 16))
        assertEquals(LocalDateTime(2026, 3, 16, 19, 30), timed.start())
    }
}
