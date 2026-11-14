package com.neteinstein.goodcompany.core.data.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.neteinstein.goodcompany.core.domain.model.CalendarAttendee
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.repository.CalendarReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Reads calendars through `CalendarContract` (decision D3). READ_CALENDAR is a normal runtime ("dangerous") permission,
 * not one of Google Play's restricted ones (unlike SMS and call log), so it's declared in every build. Read-only: the
 * app never writes to a calendar (adding a plan goes through the system editor). Nothing read here is stored.
 */
internal class AndroidCalendarReader(private val context: Context) : CalendarReader {
    override val isSupported = true

    override suspend fun hasAccess() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val out = mutableListOf<DeviceCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                ),
                null,
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += DeviceCalendar(
                        id = c.getLong(0).toString(),
                        name = c.getString(1).orEmpty(),
                        account = c.getString(2).orEmpty().takeUnless { it == c.getString(1) }.orEmpty(),
                        color = if (c.isNull(3)) null else c.getInt(3).toLong() and 0xFFFFFFFFL,
                    )
                }
            }
        }
        out
    }

    override suspend fun events(calendarIds: Set<String>, from: Instant, to: Instant): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasAccess() || calendarIds.isEmpty()) return@withContext emptyList()
        val ids = calendarIds.mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.toEpochMilliseconds())
            ContentUris.appendId(it, to.toEpochMilliseconds())
        }.build()
        val rows = mutableListOf<Row>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.RRULE,
                    CalendarContract.Instances.ORIGINAL_ID,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                ),
                "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",")})",
                null,
                CalendarContract.Instances.BEGIN,
            )?.use { c ->
                while (c.moveToNext()) {
                    val eventId = c.getLong(0)
                    val series = when {
                        !c.isNull(6) -> eventId.toString()
                        !c.isNull(7) -> c.getString(7)
                        else -> null
                    }
                    rows += Row(
                        eventId = eventId,
                        begin = c.getLong(1),
                        end = c.getLong(2),
                        title = c.getString(3).orEmpty(),
                        allDay = c.getInt(4) == 1,
                        calendarId = c.getLong(5).toString(),
                        series = series,
                        calendarName = c.getString(8).orEmpty(),
                    )
                }
            }
        }
        val attendees = attendees(rows.map { it.eventId }.distinct())
        rows.map { r ->
            CalendarEvent(
                id = "${r.eventId}:${r.begin}",
                calendarId = r.calendarId,
                title = r.title,
                start = r.begin.toEventInstant(r.allDay),
                end = r.end.toEventInstant(r.allDay),
                allDay = r.allDay,
                attendees = attendees[r.eventId].orEmpty(),
                seriesId = r.series,
                calendarName = r.calendarName,
            )
        }
    }

    private fun attendees(eventIds: List<Long>): Map<Long, List<CalendarAttendee>> {
        val out = mutableMapOf<Long, MutableList<CalendarAttendee>>()
        eventIds.chunked(CHUNK).forEach { chunk ->
            runCatching {
                context.contentResolver.query(
                    CalendarContract.Attendees.CONTENT_URI,
                    arrayOf(CalendarContract.Attendees.EVENT_ID, CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL),
                    "${CalendarContract.Attendees.EVENT_ID} IN (${chunk.joinToString(",")})",
                    null,
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        out.getOrPut(c.getLong(0)) { mutableListOf() } += CalendarAttendee(c.getString(1).orEmpty(), c.getString(2).orEmpty())
                    }
                }
            }
        }
        return out
    }

    private class Row(
        val eventId: Long,
        val begin: Long,
        val end: Long,
        val title: String,
        val allDay: Boolean,
        val calendarId: String,
        val series: String?,
        val calendarName: String,
    )

    private companion object {
        const val CHUNK = 200

        /** All-day events are stored at UTC midnight: move them to the same date's midnight here. */
        fun Long.toEventInstant(allDay: Boolean): Instant {
            val instant = Instant.fromEpochMilliseconds(this)
            if (!allDay) return instant
            return instant.toLocalDateTime(TimeZone.UTC).date.atStartOfDayIn(TimeZone.currentSystemDefault())
        }
    }
}
