package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.scope.Scope
import kotlin.coroutines.resume

/** Browsers have no address book API (outside Android Chrome's picker) — friends are added manually on the web. */
actual fun Scope.createContactsSource(): ContactsSource = object : ContactsSource {
    override suspend fun readContacts() = emptyList<com.neteinstein.goodcompany.core.domain.model.ContactRecord>()
}

actual fun Scope.createCommunicationLogSource(): CommunicationLogSource = CommunicationLogSource.None

actual fun Scope.createLocationProvider(): LocationProvider = BrowserLocationProvider()

/** A closed tab can't fire reminders; the web build shows them in the agenda only. */
actual fun Scope.createNotificationScheduler(): NotificationScheduler = object : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<PlannedNotification>) = Unit
}

private class BrowserLocationProvider : LocationProvider {
    override suspend fun currentLocation(): GeoPoint? = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { cont ->
            requestPosition(
                onSuccess = { lat, lng -> if (cont.isActive) cont.resume(GeoPoint(lat, lng)) },
                onError = { if (cont.isActive) cont.resume(null) },
            )
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun requestPosition(onSuccess: (Double, Double) -> Unit, onError: () -> Unit): Unit = js(
    """{
        if (!navigator.geolocation) { onError(); return; }
        navigator.geolocation.getCurrentPosition(
            function (p) { onSuccess(p.coords.latitude, p.coords.longitude); },
            function () { onError(); },
            { maximumAge: 600000, timeout: 15000 }
        );
    }""",
)
