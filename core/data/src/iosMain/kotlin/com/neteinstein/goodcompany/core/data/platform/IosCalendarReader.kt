package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.domain.model.CalendarAttendee
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.repository.CalendarReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKParticipant
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.time.Instant

/**
 * Reads calendars through EventKit (decision D3). Needs *full* access (iOS 17+, `NSCalendarsFullAccessUsageDescription`),
 * asked for when the user turns calendar suggestions on. Read-only; nothing read here is stored.
 */
internal class IosCalendarReader : CalendarReader {
    private val store by lazy { EKEventStore() }

    override val isSupported = true

    override suspend fun hasAccess(): Boolean =
        EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent) == FULL_ACCESS

    override suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.Default) {
        if (!hasAccess()) return@withContext emptyList()
        runCatching {
            store.calendarsForEntityType(EKEntityType.EKEntityTypeEvent).filterIsInstance<EKCalendar>().map { c ->
                DeviceCalendar(id = c.calendarIdentifier, name = c.title, account = c.source?.title.orEmpty())
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    override suspend fun events(calendarIds: Set<String>, from: Instant, to: Instant): List<CalendarEvent> = withContext(Dispatchers.Default) {
        if (!hasAccess() || calendarIds.isEmpty()) return@withContext emptyList()
        runCatching {
            val calendars = store.calendarsForEntityType(EKEntityType.EKEntityTypeEvent)
                .filterIsInstance<EKCalendar>()
                .filter { it.calendarIdentifier in calendarIds }
            if (calendars.isEmpty()) return@runCatching emptyList()
            val predicate = store.predicateForEventsWithStartDate(from.toNSDate(), to.toNSDate(), calendars)
            store.eventsMatchingPredicate(predicate).filterIsInstance<EKEvent>().mapNotNull { e -> e.toEvent() }
        }.getOrDefault(emptyList())
    }

    private fun EKEvent.toEvent(): CalendarEvent? {
        val start = startDate?.toInstant() ?: return null
        val end = endDate?.toInstant() ?: start
        val item = calendarItemIdentifier
        return CalendarEvent(
            id = "$item:${start.epochSeconds}",
            calendarId = calendar?.calendarIdentifier.orEmpty(),
            title = title.orEmpty(),
            start = start,
            end = end,
            allDay = allDay,
            attendees = attendees.orEmpty().filterIsInstance<EKParticipant>().map { p ->
                val url = p.URL.absoluteString.orEmpty()
                CalendarAttendee(p.name.orEmpty(), if (url.startsWith("mailto:", ignoreCase = true)) url.substring(7) else "")
            },
            // Every occurrence of a repeating event shares its calendar item; the external id survives a new device.
            seriesId = if (hasRecurrenceRules) calendarItemExternalIdentifier ?: item else null,
            calendarName = calendar?.title.orEmpty(),
        )
    }

    private companion object {
        /** EKAuthorizationStatusFullAccess (iOS 17); the same raw value as the older "authorized". */
        const val FULL_ACCESS = 3L

        fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

        fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * 1000).toLong())
    }
}
