package org.goodcompany.app.core.data.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.domain.model.GeoPoint
import org.goodcompany.app.core.domain.model.RenderedNotification
import org.goodcompany.app.core.domain.repository.CalendarReader
import org.goodcompany.app.core.domain.repository.ContactsWriter
import org.goodcompany.app.core.domain.repository.DriveTokenSource
import org.goodcompany.app.core.domain.repository.LocationProvider
import org.goodcompany.app.core.domain.repository.NotificationScheduler
import org.koin.core.scope.Scope
import kotlin.coroutines.resume

/** Browsers have no address book API (outside Android Chrome's picker) — friends are added manually on the web. */
actual fun Scope.createContactsSource(): ContactsSource = object : ContactsSource {
    override suspend fun readContacts(withPhotos: Boolean) = emptyList<org.goodcompany.app.core.domain.model.ContactRecord>()
}

actual fun Scope.createContactsWriter(): ContactsWriter = ContactsWriter.None

actual fun Scope.createCommunicationLogSource(): CommunicationLogSource = CommunicationLogSource.None

actual fun Scope.createLocationProvider(): LocationProvider = BrowserLocationProvider()

/** A closed tab can't fire reminders; the web build shows them in the agenda only. */
actual fun Scope.createNotificationScheduler(): NotificationScheduler = object : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<RenderedNotification>) = Unit

    override suspend fun showNow(notification: RenderedNotification) = Unit
}

/** Browsers can't read the device calendar: calendar suggestions are Android and iOS only (D3). */
actual fun Scope.createCalendarReader(): CalendarReader = CalendarReader.None

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

actual fun installNotificationActionHandling() = Unit

/** No automatic backup on the web (issue #166): "Back up now" only. */
actual fun Scope.createDriveTokenSource(): DriveTokenSource = DriveTokenSource.None
