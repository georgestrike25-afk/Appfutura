package com.cumbre.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.cumbre.core.recording.LocationSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Ubicación mediante [LocationManager] del sistema (GPS), sin depender de Google Play Services:
 * funciona en cualquier Android y sin conexión a Internet.
 */
class AndroidLocationSource(private val context: Context) {
    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    fun hasFinePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasAnyPermission(): Boolean = hasFinePermission() ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(): Boolean = manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    fun hasGpsHardware(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    /**
     * Lecturas de ubicación. Usa el GPS si hay permiso preciso; si solo hay permiso aproximado,
     * usa el proveedor de red/fusionado del sistema (menos preciso).
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMillis: Long = 1_000L): Flow<LocationSample> = callbackFlow {
        val lm = manager
        if (lm == null || !hasAnyPermission()) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toSample())
            }

            // Necesario en Android 8–9, donde estos métodos no tienen implementación por defecto.
            @Deprecated("Requerido por APIs antiguas")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val provider = when {
            hasFinePermission() && lm.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.allProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }
        try {
            lm.requestLocationUpdates(provider, intervalMillis, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        } catch (e: IllegalArgumentException) {
            close(e)
            return@callbackFlow
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    /** Última ubicación conocida si es reciente (solo para mostrar, nunca se graba). */
    @SuppressLint("MissingPermission")
    fun recentLastKnown(maxAgeMillis: Long = 2 * 60_000L): LocationSample? {
        val lm = manager ?: return null
        if (!hasAnyPermission()) return null
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }
        val best = candidates.maxByOrNull { it.elapsedRealtimeNanos } ?: return null
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - best.elapsedRealtimeNanos) / 1_000_000
        return if (ageMillis <= maxAgeMillis) best.toSample() else null
    }
}

fun Location.toSample(): LocationSample = LocationSample(
    latitude = latitude,
    longitude = longitude,
    timeMillis = sanitizedTime(time),
    altitudeMeters = if (hasAltitude()) altitude else null,
    accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
)

/**
 * Algunos receptores GPS informan fechas erróneas (por ejemplo, por el reinicio del contador
 * de semanas GPS). Si la hora difiere más de un día del reloj del sistema, se usa este último.
 */
internal fun sanitizedTime(gpsTime: Long, now: Long = System.currentTimeMillis()): Long =
    if (gpsTime <= 0L || kotlin.math.abs(gpsTime - now) > 24 * 60 * 60 * 1000L) now else gpsTime
