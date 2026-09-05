package com.weatherquips.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.weatherquips.app.domain.model.Coordinates
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Outcome of a location request, so the UI never has to interpret exceptions. */
sealed interface LocationResult {
    data class Success(val coordinates: Coordinates) : LocationResult
    data object PermissionDenied : LocationResult
    data object LocationDisabled : LocationResult
    data object Unavailable : LocationResult
}

/**
 * Device location on the platform APIs — no Google Play Services, so the app
 * also works on de-Googled devices.
 *
 * A recent last-known fix is reused before ever powering up a provider, and a
 * live fix is requested exactly once (never a continuous stream), which is the
 * Android equivalent of the web app's single `getCurrentPosition` call.
 */
class LocationProvider(private val context: Context) {

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    /** True when the user granted precise (rather than approximate) location. */
    fun hasPrecisePermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean =
        locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

    suspend fun currentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        val manager = locationManager ?: return LocationResult.Unavailable
        if (!isLocationEnabled()) return LocationResult.LocationDisabled

        lastKnownLocation(manager)?.let { return LocationResult.Success(it.toCoordinates()) }

        return try {
            val fresh = withTimeout(REQUEST_TIMEOUT_MILLIS) { requestSingleUpdate(manager) }
            if (fresh == null) LocationResult.Unavailable else LocationResult.Success(fresh.toCoordinates())
        } catch (_: TimeoutCancellationException) {
            // A stale fix still beats no weather at all.
            lastKnownLocation(manager, maxAgeMillis = Long.MAX_VALUE)
                ?.let { LocationResult.Success(it.toCoordinates()) }
                ?: LocationResult.Unavailable
        } catch (_: SecurityException) {
            LocationResult.PermissionDenied
        }
    }

    private fun lastKnownLocation(
        manager: LocationManager,
        maxAgeMillis: Long = MAX_LAST_KNOWN_AGE_MILLIS,
    ): Location? = try {
        manager.getProviders(true)
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
            .filter { System.currentTimeMillis() - it.time <= maxAgeMillis }
            .maxByOrNull { it.time }
    } catch (_: SecurityException) {
        null
    }

    private suspend fun requestSingleUpdate(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = preferredProvider(manager)
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, singleThreadExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }

                        @Deprecated("Required by the pre-API 30 LocationListener contract")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                        override fun onProviderDisabled(provider: String) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(null)
                        }

                        override fun onProviderEnabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    /**
     * Network/fused positioning is preferred: it is fast and low-power, and the
     * app only needs city-level accuracy. GPS is the fallback.
     */
    private fun preferredProvider(manager: LocationManager): String? {
        val enabled = manager.getProviders(true)
        val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationManager.FUSED_PROVIDER
        } else {
            null
        }
        return listOfNotNull(fused, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in enabled }
            ?: enabled.firstOrNull()
    }

    private fun Location.toCoordinates() = Coordinates(latitude, longitude)

    private companion object {
        const val MAX_LAST_KNOWN_AGE_MILLIS = 5 * 60 * 1000L
        const val REQUEST_TIMEOUT_MILLIS = 12_000L
        val singleThreadExecutor = Executors.newSingleThreadExecutor()
    }
}
