package com.lstepnio.egauge.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.SettingsUiState

@Composable
fun SettingsScreen(state: SettingsUiState, onAdvanced: (Boolean) -> Unit, onDynamic: (Boolean) -> Unit,
    onRename: (String) -> Unit, onRotate: (Int) -> Unit, onReadSaved: () -> Unit,
    onReadDisplay: () -> Unit, onSaveDisplay: (Int, Int) -> Unit, onUpdates: () -> Unit,
    onSetup: () -> Unit, onDetails: () -> Unit, onBluetoothSettings: () -> Unit) {
    var nameOpen by rememberSaveable { mutableStateOf(false) }
    var rotationOpen by rememberSaveable { mutableStateOf(false) }
    var brightnessOpen by rememberSaveable { mutableStateOf(false) }
    var forgetOpen by rememberSaveable { mutableStateOf(false) }
    ScreenContent {
        ScreenTitle("Settings")
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Gauge")
                SettingsRow("Gauge name", state.name) { nameOpen = true }
                SettingsRow("Brightness", state.brightness?.let { "$it%" } ?:
                    if (state.displaySettingsVersion == 1) "Check current settings" else "Not available on this gauge",
                    state.displaySettingsVersion == 1 && state.found && !state.busy) {
                    if (state.brightness == null) onReadDisplay() else brightnessOpen = true
                }
                SettingsRow("Rotation", state.rotation?.let { "${it * 90}°" } ?: "Check current settings", state.found && !state.busy) {
                    if (state.displaySettingsVersion == 1 && state.brightness == null) onReadDisplay()
                    else if (state.rotation == null) onReadSaved() else rotationOpen = true
                }
                if (!state.found) TextButton(onSetup) { Text("Set up gauge") }
                TextButton({ forgetOpen = true }) { Text("Forget gauge") }
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Appearance and tools")
                PreferenceToggle("Use phone colours", state.dynamicColor, onDynamic)
                PreferenceToggle("Show advanced tools", state.advanced, onAdvanced)
                PrimaryAction("Check updates", onUpdates)
                TextButton(onDetails, Modifier.fillMaxWidth()) { Text("Details") }
            }
        })
    }
    if (nameOpen) {
        var name by rememberSaveable { mutableStateOf(state.name) }
        AlertDialog(onDismissRequest = { nameOpen = false }, title = { Text("Name your gauge") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(32) }, label = { Text("Gauge name") }, singleLine = true)
                Text("This name is used on this phone.", style = MaterialTheme.typography.bodyMedium)
            }
        }, confirmButton = { Button({ onRename(name); nameOpen = false }, enabled = name.isNotBlank()) { Text("Save name") } },
            dismissButton = { TextButton({ nameOpen = false }) { Text("Cancel") } })
    }
    if (rotationOpen) {
        var rotation by rememberSaveable { mutableIntStateOf(state.rotation ?: 0) }
        AlertDialog(onDismissRequest = { rotationOpen = false }, title = { Text("Rotate your display") }, text = {
            Column {
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
            if (state.displaySettingsVersion == 1) onSaveDisplay(rotation, state.brightness ?: 80)
            else onRotate(rotation)
            rotationOpen = false
        }, enabled = state.canRotate && !state.busy) { Text("Rotate gauge") } },
            dismissButton = { TextButton({ rotationOpen = false }) { Text("Cancel") } })
    }
    if (brightnessOpen) {
        var brightness by rememberSaveable { mutableIntStateOf(state.brightness ?: 80) }
        AlertDialog(onDismissRequest = { brightnessOpen = false }, title = { Text("Display brightness") }, text = {
            Column {
                Text("$brightness%")
                Slider(value = brightness.toFloat(), onValueChange = { brightness = it.toInt().coerceIn(5, 100) },
                    valueRange = 5f..100f)
            }
        }, confirmButton = { Button({
            onSaveDisplay(state.rotation ?: 0, brightness)
            brightnessOpen = false
        }, enabled = !state.busy) { Text("Save to gauge") } },
            dismissButton = { TextButton({ brightnessOpen = false }) { Text("Cancel") } })
    }
    if (forgetOpen) AlertDialog(onDismissRequest = { forgetOpen = false }, title = { Text("Forget this gauge?") },
        text = { Text("Remove eGauge from paired devices in Android Bluetooth settings. You will need the code on your gauge to pair again. Your display settings stay on the gauge.") },
        confirmButton = { Button({ forgetOpen = false; onBluetoothSettings() }) { Text("Open Bluetooth settings") } },
        dismissButton = { TextButton({ forgetOpen = false }) { Text("Cancel") } })
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
