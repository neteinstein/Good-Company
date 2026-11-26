package org.goodcompany.app.core.data.platform

import org.goodcompany.app.core.data.cloud.PlayServicesDriveTokenSource
import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.domain.repository.CalendarReader
import org.goodcompany.app.core.domain.repository.ContactsWriter
import org.goodcompany.app.core.domain.repository.DriveTokenSource
import org.goodcompany.app.core.domain.repository.LocationProvider
import org.goodcompany.app.core.domain.repository.NotificationScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

actual fun Scope.createContactsSource(): ContactsSource = AndroidContactsSource(androidContext())

actual fun Scope.createContactsWriter(): ContactsWriter = AndroidContactsWriter(androidContext())

actual fun Scope.createCommunicationLogSource(): CommunicationLogSource = AndroidCommunicationLogSource(androidContext())

actual fun Scope.createLocationProvider(): LocationProvider = AndroidLocationProvider(androidContext())

actual fun Scope.createNotificationScheduler(): NotificationScheduler = AndroidNotificationScheduler(androidContext())

actual fun Scope.createCalendarReader(): CalendarReader = AndroidCalendarReader(androidContext())

actual fun installNotificationActionHandling() = Unit

actual fun Scope.createDriveTokenSource(): DriveTokenSource = PlayServicesDriveTokenSource(androidContext())
