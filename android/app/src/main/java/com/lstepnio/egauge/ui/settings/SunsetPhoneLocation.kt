package com.lstepnio.egauge.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.lstepnio.egauge.SunsetLocation
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_CACHED_AGE_NS = 15 * 60 * 1_000_000_000L

internal sealed interface SunsetPhoneLocation {
    data class Ready(val location: SunsetLocation) : SunsetPhoneLocation
    data object PermissionRequired : SunsetPhoneLocation
    data object LocationOff : SunsetPhoneLocation
    data object Unavailable : SunsetPhoneLocation
}

private fun hasSunsetLocationPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
internal suspend fun readSunsetLocation(context: Context): SunsetPhoneLocation {
    if (!hasSunsetLocationPermission(context)) return SunsetPhoneLocation.PermissionRequired
    val manager = context.getSystemService(LocationManager::class.java) ?: return SunsetPhoneLocation.Unavailable
    if (!LocationManagerCompat.isLocationEnabled(manager)) return SunsetPhoneLocation.LocationOff

    val enabled = manager.getProviders(true)
    val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter(enabled::contains)
    val cached = (providers + LocationManager.PASSIVE_PROVIDER).filter(enabled::contains).mapNotNull { provider ->
        fromProvider { manager.getLastKnownLocation(provider) }
    }
    val now = SystemClock.elapsedRealtimeNanos()
    val recent = cached.filter { now - it.elapsedRealtimeNanos in 0..MAX_CACHED_AGE_NS }.maxByOrNull { it.elapsedRealtimeNanos }
    val location = recent ?: firstSunsetFix(providers) { provider ->
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) {
                    continuation.resume(it)
                }
            } catch (_: SecurityException) {
                continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                continuation.resume(null)
            }
        }
    }
    return when {
        !hasSunsetLocationPermission(context) -> SunsetPhoneLocation.PermissionRequired

        !LocationManagerCompat.isLocationEnabled(manager) -> SunsetPhoneLocation.LocationOff

        location == null -> SunsetPhoneLocation.Unavailable

        else -> SunsetPhoneLocation.Ready(
            SunsetLocation((location.latitude * 10).roundToInt() * 10, (location.longitude * 10).roundToInt() * 10)
        )
    }
}

private inline fun fromProvider(read: () -> Location?): Location? = try {
    read()
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

internal suspend fun <T : Any> firstSunsetFix(providers: List<String>, read: suspend (String) -> T?): T? = withTimeoutOrNull(12_000) {
    channelFlow {
        providers.forEach { provider -> launch { read(provider)?.let { send(it) } } }
    }.firstOrNull()
}
