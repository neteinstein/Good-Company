package org.goodcompany.app.core.data.platform

import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.domain.repository.CalendarReader
import org.goodcompany.app.core.domain.repository.ContactsWriter
import org.goodcompany.app.core.domain.repository.DriveTokenSource
import org.goodcompany.app.core.domain.repository.LocationProvider
import org.goodcompany.app.core.domain.repository.NotificationScheduler
import org.koin.core.scope.Scope

actual fun Scope.createContactsSource(): ContactsSource = IosContactsSource()

/** Only the Android sideload build writes to contacts. */
actual fun Scope.createContactsWriter(): ContactsWriter = ContactsWriter.None

/** iOS exposes no call history to third-party apps. */
actual fun Scope.createCommunicationLogSource(): CommunicationLogSource = CommunicationLogSource.None

actual fun Scope.createLocationProvider(): LocationProvider = IosLocationProvider()

actual fun Scope.createNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()

actual fun Scope.createCalendarReader(): CalendarReader = IosCalendarReader()

/** UNUserNotificationCenter holds its delegate weakly, so keep the instance alive here. */
private var notificationDelegate: IosNotificationDelegate? = null

actual fun installNotificationActionHandling() {
    val delegate = notificationDelegate ?: IosNotificationDelegate().also { notificationDelegate = it }
    platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
}

/** No automatic backup on iOS (issue #166): "Back up now" only. */
actual fun Scope.createDriveTokenSource(): DriveTokenSource = DriveTokenSource.None
