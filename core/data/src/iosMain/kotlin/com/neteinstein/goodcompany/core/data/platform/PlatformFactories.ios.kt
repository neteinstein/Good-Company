package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import org.koin.core.scope.Scope

actual fun Scope.createContactsSource(): ContactsSource = IosContactsSource()

/** iOS exposes no call history to third-party apps. */
actual fun Scope.createCommunicationLogSource(): CommunicationLogSource = CommunicationLogSource.None

actual fun Scope.createLocationProvider(): LocationProvider = IosLocationProvider()

actual fun Scope.createNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()

/** UNUserNotificationCenter holds its delegate weakly, so keep the instance alive here. */
private var notificationDelegate: IosNotificationDelegate? = null

actual fun installNotificationActionHandling() {
    val delegate = notificationDelegate ?: IosNotificationDelegate().also { notificationDelegate = it }
    platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
}
