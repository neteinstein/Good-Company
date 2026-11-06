package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** One-shot CLLocationManager request; asks for when-in-use permission the first time. */
internal class IosLocationProvider : LocationProvider {
    override suspend fun currentLocation(): GeoPoint? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val manager = CLLocationManager()
                val delegate = OneShotDelegate { point ->
                    if (cont.isActive) cont.resume(point)
                }
                manager.delegate = delegate
                manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
                cont.invokeOnCancellation {
                    manager.stopUpdatingLocation()
                    manager.delegate = null
                }
                // Keep the delegate alive for the duration of the request (delegate is a weak ref).
                retained = delegate
                when (manager.authorizationStatus) {
                    kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
                    kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
                    else -> cont.resume(null)
                }
                retainedManager = manager
            }
        }.also {
            retained = null
            retainedManager = null
        }
    }

    private var retained: NSObject? = null
    private var retainedManager: CLLocationManager? = null

    private class OneShotDelegate(private val done: (GeoPoint?) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            when (manager.authorizationStatus) {
                kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
                kCLAuthorizationStatusNotDetermined -> Unit
                else -> done(null)
            }
        }

        @OptIn(ExperimentalForeignApi::class)
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation
            done(location?.coordinate?.useContents { GeoPoint(latitude, longitude) })
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            done(null)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
