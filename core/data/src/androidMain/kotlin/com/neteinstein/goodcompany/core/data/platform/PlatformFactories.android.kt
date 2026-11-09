package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

actual fun Scope.createContactsSource(): ContactsSource = AndroidContactsSource(androidContext())

actual fun Scope.createContactsWriter(): ContactsWriter = AndroidContactsWriter(androidContext())

actual fun Scope.createCommunicationLogSource(): CommunicationLogSource = AndroidCommunicationLogSource(androidContext())

actual fun Scope.createLocationProvider(): LocationProvider = AndroidLocationProvider(androidContext())

actual fun Scope.createNotificationScheduler(): NotificationScheduler = AndroidNotificationScheduler(androidContext())

actual fun installNotificationActionHandling() = Unit
