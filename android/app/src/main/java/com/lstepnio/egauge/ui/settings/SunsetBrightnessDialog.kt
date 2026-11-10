package com.lstepnio.egauge.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.lstepnio.egauge.SunsetBrightness
import com.lstepnio.egauge.SunsetLocation
import com.lstepnio.egauge.core.designsystem.DialogContent
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun SunsetBrightnessDialog(
    current: SunsetBrightness,
    dayBrightness: Int,
    onBack: () -> Unit,
    onSave: (Boolean, Int, SunsetLocation?) -> Unit
) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(current.nightBrightness.coerceAtMost(dayBrightness)) }
    var automatic by remember { mutableStateOf(current.automatic) }
    var location by remember { mutableStateOf(current.location.takeIf { current.automatic }) }
    var looking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) looking = true else message = "Allow approximate location to set the sunset schedule."
    }
    LaunchedEffect(looking) {
        if (!looking) return@LaunchedEffect
        val resolved = readSunsetLocation(context)
        looking = false
        if (resolved != null) location = resolved else message = "Couldn't get your location. Try again with location services on."
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Night brightness") },
        text = {
            DialogContent {
                Text("$level%")
                Slider(
                    level.toFloat(),
                    { level = it.roundToInt() },
                    enabled = dayBrightness > 5,
                    valueRange = 5f..maxOf(dayBrightness.toFloat(), 6f),
                    colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.semantics {
                        contentDescription = "Night brightness level"
                        stateDescription = "$level percent"
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sunset to sunrise", Modifier.weight(1f))
                    Switch(
                        automatic,
                        { automatic = it },
                        modifier = Modifier.semantics {
                            contentDescription = "Sunset to sunrise"
                        }
                    )
                }
                if (automatic) {
                    Text("The gauge uses your approximate location to adjust for seasonal daylight.")
                    TextButton(
                        onClick = {
                            message = null
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                looking = true
                            } else {
                                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        enabled = !looking
                    ) {
                        Text(
                            if (looking) {
                                "Finding location…"
                            } else if (location == null) {
                                "Use phone location"
                            } else {
                                "Update location"
                            }
                        )
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button({ onSave(automatic, level, location) }, enabled = !looking && (!automatic || location != null)) {
                Text("Save to gauge")
            }
        },
        dismissButton = { TextButton(onBack) { Text("Cancel") } }
    )
}

@SuppressLint("MissingPermission")
private suspend fun readSunsetLocation(context: Context): SunsetLocation? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    val provider = providers.firstOrNull { it in manager.getProviders(true) } ?: return null
    val location = withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancellation,
                    ContextCompat.getMainExecutor(context)
                ) { continuation.resume(it) }
            } catch (_: SecurityException) {
                continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                continuation.resume(null)
            }
        }
    } ?: return null
    // A tenth of a degree is sufficient for daylight and avoids persisting a precise position.
    return SunsetLocation((location.latitude * 10).roundToInt() * 10, (location.longitude * 10).roundToInt() * 10)
}
