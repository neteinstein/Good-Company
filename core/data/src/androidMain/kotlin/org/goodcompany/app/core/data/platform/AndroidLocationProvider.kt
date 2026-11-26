package org.goodcompany.app.core.data.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.goodcompany.app.core.domain.model.GeoPoint
import org.goodcompany.app.core.domain.repository.LocationProvider
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Framework LocationManager (no Play Services dependency). Coarse location is enough for "where were we". */
internal class AndroidLocationProvider(private val context: Context) : LocationProvider {
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = PROVIDERS.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        // Location switched off: nothing will come (the sheet says so, issue #114).
        val provider = enabled.firstOrNull() ?: return null

        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, executor) { if (cont.isActive) cont.resume(it) }
            }
        }
        // No fresh fix in time (indoors, GPS only…): the newest last-known fix from any enabled provider.
        val location = fresh ?: enabled.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}
