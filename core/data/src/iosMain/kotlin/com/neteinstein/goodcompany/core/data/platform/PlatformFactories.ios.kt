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

actual fun installNotificationActionHandling() {
    platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter().delegate = IosNotificationDelegate
}
