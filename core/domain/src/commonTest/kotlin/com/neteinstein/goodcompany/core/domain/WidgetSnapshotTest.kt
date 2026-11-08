package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.HomeWidgetSummary
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.WidgetSnapshot
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetSnapshotTest {
    private val today = LocalDate(2026, 3, 10)
    private val ana = friend("ana", "Ana Silva")

    private fun snap(next: AgendaItem?, private: Boolean = false) = WidgetSnapshot.from(HomeWidgetSummary(next, null, private, today))

    @Test
    fun mapsEachKindToPlainData() {
        val date = LocalDate(2026, 3, 12)
        assertEquals(
            WidgetSnapshot.Next(WidgetSnapshot.Kind.BIRTHDAY, name = "Ana", date = date),
            snap(AgendaItem.FriendBirthday(ana, date, 30)).next,
        )
        assertEquals(
            WidgetSnapshot.Next(WidgetSnapshot.Kind.KID_BIRTHDAY, name = "Ana", kidName = "Leo", date = date),
            snap(AgendaItem.KidBirthday(ana, Kid("k", "Leo"), date, 6)).next,
        )
        assertEquals(
            WidgetSnapshot.Next(WidgetSnapshot.Kind.EVENT, name = "Ana", title = "Surgery", date = date),
            snap(AgendaItem.FriendEvent(ana, ImportantEvent("e", "Surgery", date))).next,
        )
        val plan = Plan("p", listOf(ana.id), "Lunch", date, createdAt = at(2026, 3, 1))
        assertEquals(WidgetSnapshot.Kind.PLAN, snap(AgendaItem.PlanUpcoming(plan, listOf(ana))).next?.kind)
        val reminder = Reminder("r", "Book tickets", createdAt = at(2026, 3, 1))
        assertEquals("Book tickets", snap(AgendaItem.ReminderDue(reminder, emptyList(), date)).next?.title)
        assertEquals(null, snap(null).next)
        assertEquals(WidgetSnapshot(isPrivate = true), snap(AgendaItem.FriendBirthday(ana, date, 30), private = true))
    }

    @Test
    fun catchUpAndJsonShapeForSwift() {
        val status = Relationships.MeetupStatus(ana, lastSeenDate = null, dueDate = today, daysSinceLastSeen = null)
        val s = WidgetSnapshot.from(HomeWidgetSummary(AgendaItem.FriendBirthday(ana, today, null), status, false, today))
        assertEquals(WidgetSnapshot.CatchUp("ana", "Ana"), s.catchUp)
        // The Swift widget decodes exactly this (defaults are left out, dates are ISO strings).
        assertEquals(
            """{"next":{"kind":"BIRTHDAY","name":"Ana","date":"2026-03-10"},"catchUp":{"friendId":"ana","firstName":"Ana"}}""",
            Json.encodeToString(WidgetSnapshot.serializer(), s),
        )
    }
}
