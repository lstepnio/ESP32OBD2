package com.lstepnio.egauge.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.SettingsUiState

@Composable
fun SettingsScreen(state: SettingsUiState, onAdvanced: (Boolean) -> Unit, onDynamic: (Boolean) -> Unit,
    onRename: (String) -> Unit, onRotate: (Int) -> Unit, onSaveDisplay: (Int, Int) -> Unit, onUpdates: () -> Unit,
    onSetup: () -> Unit, onBluetoothSettings: () -> Unit, onSaveUnits: (MeasurementSystem) -> Unit = {},
    onSaveCycle: (Int) -> Unit = {}, onSelectGauge: (String) -> Unit = {}, onAddGauge: () -> Unit = {}) {
    var gaugesOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    var nameOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    var rotationOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    var brightnessOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    var unitsOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    var cycleOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    var forgetOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    var moveOwnerOpen by rememberSaveable(state.selectedGaugeId) { mutableStateOf(false) }
    ScreenContent {
        ScreenTitle("Settings")
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Gauge")
                if (!state.settingsCurrent && state.brightness != null)
                    Text("Last checked settings", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsRow("Your gauges", state.name, !state.busy) { gaugesOpen = true }
                SettingsRow("Gauge name", state.name) { nameOpen = true }
                SettingsRow("Brightness", state.brightness?.let { "$it%" } ?:
                    if (state.displaySettingsVersion >= 1) "Reading settings…" else "Not available on this gauge",
                    state.displaySettingsVersion >= 1 && state.found && !state.busy && state.settingsCurrent && state.brightness != null) {
                    brightnessOpen = true
                }
                SettingsRow("Rotation", state.rotation?.let { "${it * 90}°" } ?: "Reading settings…", state.found && !state.busy && state.settingsCurrent && state.rotation != null) {
                    rotationOpen = true
                }
                SettingsRow("Units", if (state.displaySettingsVersion < 2) "Not available on this gauge"
                    else if (state.brightness == null) "Reading settings…" else state.measurementSystem.name,
                    state.displaySettingsVersion >= 2 && state.found && !state.busy && state.settingsCurrent && state.brightness != null) {
                    unitsOpen = true
                }
                SettingsRow("Auto-cycle pages", if (state.displaySettingsVersion < 3) "Not available on this gauge"
                    else when (state.cycleSeconds) { null -> "Reading settings…"; 0 -> "Off"; else -> "Every ${state.cycleSeconds} seconds" },
                    state.displaySettingsVersion >= 3 && state.found && !state.busy && state.settingsCurrent && state.cycleSeconds != null) {
                    cycleOpen = true
                }
                if (!state.found) TextButton(onSetup) { Text("Set up gauge") }
                TextButton({ forgetOpen = true }) { Text("Remove from this phone") }
                TextButton({ moveOwnerOpen = true }) { Text("Move gauge to another phone") }
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Appearance and tools")
                PreferenceToggle("Use phone colours", state.dynamicColor, onDynamic)
                PreferenceToggle("Show advanced tools", state.advanced, onAdvanced)
                PrimaryAction("Check updates", onUpdates)
            }
        })
    }
    if (gaugesOpen) AlertDialog(onDismissRequest = { gaugesOpen = false }, title = { Text("Your gauges") }, text = {
        DialogContent {
            state.gauges.forEach { gauge ->
                SettingsRow(gauge.name,
                    listOfNotNull(gauge.vehicleName,
                        if (gauge.needsReview) "Setup needs review" else if (gauge.id == state.selectedGaugeId) "Selected" else null).joinToString(" · "), !state.busy,
                    { onSelectGauge(gauge.id); gaugesOpen = false })
            }
            TextButton({ onAddGauge(); gaugesOpen = false }, enabled = !state.busy) { Text("Add gauge") }
        }
    }, confirmButton = { TextButton({ gaugesOpen = false }) { Text("Close") } })
    if (nameOpen) {
        var name by rememberSaveable(state.selectedGaugeId) { mutableStateOf(state.name) }
        AlertDialog(onDismissRequest = { nameOpen = false }, title = { Text("Name your gauge") }, text = {
            DialogContent {
                OutlinedTextField(name, { name = it.take(32) }, label = { Text("Gauge name") }, singleLine = true)
                Text("This name is used on this phone.", style = MaterialTheme.typography.bodyMedium)
            }
        }, confirmButton = { Button({ onRename(name); nameOpen = false }, enabled = name.isNotBlank()) { Text("Save name") } },
            dismissButton = { TextButton({ nameOpen = false }) { Text("Cancel") } })
    }
    if (rotationOpen) {
        var rotation by rememberSaveable(state.selectedGaugeId) { mutableIntStateOf(state.rotation ?: 0) }
        AlertDialog(onDismissRequest = { rotationOpen = false }, title = { Text("Rotate your display") }, text = {
            DialogContent {
                (0..3).forEach { value ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(rotation == value, role = Role.RadioButton, onClick = { rotation = value }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(rotation == value, null)
                        Text("${value * 90}°")
                    }
                }
                if (!state.canRotate) Text("Rotation is not available on this gauge.")
            }
        }, confirmButton = { Button({
            if (state.displaySettingsVersion >= 1) onSaveDisplay(rotation, state.brightness ?: 80)
            else onRotate(rotation)
            rotationOpen = false
        }, enabled = state.canRotate && state.settingsCurrent && !state.busy) { Text("Rotate gauge") } },
            dismissButton = { TextButton({ rotationOpen = false }) { Text("Cancel") } })
    }
    if (brightnessOpen) {
        var brightness by rememberSaveable(state.selectedGaugeId) { mutableIntStateOf(state.brightness ?: 80) }
        AlertDialog(onDismissRequest = { brightnessOpen = false }, title = { Text("Display brightness") }, text = {
            DialogContent {
                Text("$brightness%")
                Slider(value = brightness.toFloat(), onValueChange = { brightness = it.toInt().coerceIn(5, 100) },
                    valueRange = 5f..100f, modifier = Modifier.semantics {
                        contentDescription = "Brightness"; stateDescription = "$brightness percent"
                    })
            }
        }, confirmButton = { Button({
            onSaveDisplay(state.rotation ?: 0, brightness)
            brightnessOpen = false
        }, enabled = state.settingsCurrent && !state.busy) { Text("Save to gauge") } },
            dismissButton = { TextButton({ brightnessOpen = false }) { Text("Cancel") } })
    }
    if (unitsOpen) {
        var choice by rememberSaveable(state.selectedGaugeId) { mutableStateOf(state.measurementSystem) }
        AlertDialog(onDismissRequest = { unitsOpen = false }, title = { Text("Display units") }, text = {
            DialogContent {
                MeasurementSystem.entries.forEach { system ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(choice == system,
                        role = Role.RadioButton, onClick = { choice = system }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(choice == system, null)
                        Text(system.name)
                    }
                }
                Text("Temperature and speed change on the gauge and in app previews.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }, confirmButton = { Button({ onSaveUnits(choice); unitsOpen = false }, enabled = state.settingsCurrent && !state.busy) {
            Text("Save to gauge")
        } }, dismissButton = { TextButton({ unitsOpen = false }) { Text("Cancel") } })
    }
    if (cycleOpen) {
        var choice by rememberSaveable(state.selectedGaugeId) { mutableIntStateOf(state.cycleSeconds ?: 0) }
        AlertDialog(onDismissRequest = { cycleOpen = false }, title = { Text("Auto-cycle saved pages") }, text = {
            DialogContent {
                listOf(0, 5, 10, 15, 30, 60).forEach { seconds ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(choice == seconds,
                        role = Role.RadioButton, onClick = { choice = seconds }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(choice == seconds, null)
                        Text(if (seconds == 0) "Off" else "Every $seconds seconds")
                    }
                }
                Text("A tap changes the page and restarts the timer.", style = MaterialTheme.typography.bodyMedium)
            }
        }, confirmButton = { Button({ onSaveCycle(choice); cycleOpen = false }, enabled = state.settingsCurrent && !state.busy) {
            Text("Save to gauge")
        } }, dismissButton = { TextButton({ cycleOpen = false }) { Text("Cancel") } })
    }
    if (forgetOpen) AlertDialog(onDismissRequest = { forgetOpen = false }, title = { Text("Remove this phone's bond?") },
        text = { Text("This opens Android Bluetooth settings so you can remove the saved eGauge bond. The gauge owner remains saved. Your display settings stay on the gauge.") },
        confirmButton = { Button({ forgetOpen = false; onBluetoothSettings() }) { Text("Open Bluetooth settings") } },
        dismissButton = { TextButton({ forgetOpen = false }) { Text("Cancel") } })
    if (moveOwnerOpen) AlertDialog(onDismissRequest = { moveOwnerOpen = false }, title = { Text("Move gauge to another phone?") },
        text = { Text("On the gauge, hold the screen for 12 seconds, release, then tap when it says OWNER RESET? TAP TO CONFIRM. After it restarts and opens pairing, remove eGauge from this phone's Bluetooth settings. The new phone can then tap Pair gauge. Display settings stay saved.") },
        confirmButton = { Button({ moveOwnerOpen = false; onBluetoothSettings() }) { Text("Open Bluetooth settings") } },
        dismissButton = { TextButton({ moveOwnerOpen = false }) { Text("Close") } })
}

@Composable
private fun PreferenceToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChecked).padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked, null)
        }
    }
}
