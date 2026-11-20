package io.github.karismatic_megafauna.papertimetable.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Position(val lat: Double, val lon: Double, val ageMinutes: Long)

/** The outcome of asking where the phone is, once. */
sealed interface Fix {
    data class Fresh(val lat: Double, val lon: Double) : Fix
    /** No new fix in time; this is where the phone was [ageMinutes] ago. */
    data class Stale(val lat: Double, val lon: Double, val ageMinutes: Long) : Fix
    data object Denied : Fix
    /** Location is switched off for the whole phone. */
    data object Off : Fix
    data object None : Fix
}

/** The little of Android's location service that Near me needs, so it can be faked in tests. */
interface LocationSource {
    fun hasPermission(): Boolean
    fun enabledProviders(): List<String>
    /** Suspends until [provider] has a fix; null if it gives up. */
    suspend fun current(provider: String): Position?
    fun lastKnown(): Position?
}

/**
 * One location fix, on request, with a deadline. Never tracks, never runs in
 * the background. Uses the phone's own location service: the Kompakt has no
 * Google services.
 */
class OneShotLocation(private val source: LocationSource, private val timeoutMs: Long = 30_000) {
    suspend fun get(): Fix {
        if (!source.hasPermission()) return Fix.Denied
        val providers = source.enabledProviders()
        if (providers.isEmpty()) return Fix.Off
        val fresh = withTimeoutOrNull(timeoutMs) {
            channelFlow {
                providers.forEach { provider -> launch { source.current(provider)?.let { send(it) } } }
            }.firstOrNull()
        }
        if (fresh != null) return Fix.Fresh(fresh.lat, fresh.lon)
        val last = source.lastKnown()
        return if (last != null && last.ageMinutes < MAX_STALE_MINUTES) Fix.Stale(last.lat, last.lon, last.ageMinutes) else Fix.None
    }

    companion object {
        const val MAX_STALE_MINUTES = 30
    }
}

class AndroidLocationSource(private val context: Context) : LocationSource {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun enabledProviders(): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    @SuppressLint("MissingPermission") // callers check hasPermission() first
    override suspend fun current(provider: String): Position? = suspendCancellableCoroutine { cont ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location -> if (cont.isActive) cont.resume(location?.toPosition()) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location.toPosition())
                    }
                    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        } catch (e: IllegalArgumentException) { // provider does not exist on this device
            if (cont.isActive) cont.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    override fun lastKnown(): Position? = enabledProviders()
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.elapsedRealtimeNanos }
        ?.toPosition()

    private fun Location.toPosition() =
        Position(latitude, longitude, (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 60_000_000_000L)
}

