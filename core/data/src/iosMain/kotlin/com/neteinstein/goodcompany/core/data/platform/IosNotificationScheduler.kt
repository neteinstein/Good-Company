package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/** UNUserNotificationCenter calendar triggers. iOS caps pending requests at 64, so we keep the soonest 60. */
internal class IosNotificationScheduler : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<PlannedNotification>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
        val zone = TimeZone.currentSystemDefault()
        notifications.sortedBy { it.at }.take(MAX_PENDING).forEach { n ->
            val local = n.at.toLocalDateTime(zone)
            val components = NSDateComponents().apply {
                year = local.year.toLong()
                month = local.month.ordinal.toLong() + 1
                day = local.day.toLong()
                hour = local.hour.toLong()
                minute = local.minute.toLong()
            }
            val content = UNMutableNotificationContent().apply {
                setTitle(n.title)
                setBody(n.body)
                setSound(UNNotificationSound.defaultSound())
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(n.id, content, trigger), null)
        }
    }

    private companion object {
        const val MAX_PENDING = 60
    }
}
