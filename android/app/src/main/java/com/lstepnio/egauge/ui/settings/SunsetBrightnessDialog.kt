package com.lstepnio.egauge.ui.settings

import android.Manifest
import android.content.Context
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
import com.lstepnio.egauge.SunsetBrightness
import com.lstepnio.egauge.SunsetLocation
import com.lstepnio.egauge.core.designsystem.DialogContent
import kotlin.math.roundToInt

@Composable
internal fun SunsetBrightnessDialog(
    current: SunsetBrightness,
    dayBrightness: Int,
    onBack: () -> Unit,
    locate: suspend (Context) -> SunsetPhoneLocation = ::readSunsetLocation,
    onSave: (Boolean, Int, SunsetLocation?) -> Unit
) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(current.nightBrightness.coerceAtMost(dayBrightness)) }
    var automatic by remember { mutableStateOf(current.automatic) }
    var location by remember { mutableStateOf(current.location.takeIf { current.automatic }) }
    var looking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (automatic) {
            if (granted) looking = true else message = "Allow approximate location for eGauge in Android settings, then try again."
        }
    }
    fun requestLocation() {
        message = null
        looking = true
    }
    LaunchedEffect(automatic, looking) {
        if (!automatic || !looking) return@LaunchedEffect
        val result = locate(context)
        looking = false
        when (result) {
            is SunsetPhoneLocation.Ready -> location = result.location
            SunsetPhoneLocation.PermissionRequired -> permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            SunsetPhoneLocation.LocationOff -> message = "Turn on Location in Android settings, then try again."
            SunsetPhoneLocation.Unavailable -> message = "Your phone couldn’t find a location. Try again in a spot with better reception."
        }
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
                        { enabled ->
                            automatic = enabled
                            if (enabled && location == null) requestLocation()
                            if (!enabled) looking = false
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Sunset to sunrise"
                        }
                    )
                }
                if (automatic) {
                    Text("The gauge uses your approximate location to adjust for seasonal daylight.")
                    TextButton(
                        onClick = ::requestLocation,
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
                    if (location != null && !looking && message == null) Text("Location ready")
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
