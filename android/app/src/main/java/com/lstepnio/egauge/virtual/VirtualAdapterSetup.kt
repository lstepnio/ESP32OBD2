package com.lstepnio.egauge.virtual

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent

@Composable
internal fun VirtualAdapterSetup(
    bindings: VirtualBindings,
    statuses: Map<VirtualSource, VirtualStatus>,
    radioStatus: VirtualStatus?,
    picker: VirtualSource?,
    scanning: Boolean,
    results: List<VirtualCandidate>,
    units: MeasurementSystem,
    onBack: () -> Unit,
    onPick: (VirtualSource) -> Unit,
    onChoose: (VirtualSource, String) -> Unit,
    onPermission: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onManageAccess: (() -> Unit)?,
    onRemoveTransmission: () -> Unit,
    onUnits: (MeasurementSystem) -> Unit
) {
    var unitsMenu by remember { mutableStateOf(false) }
    ScreenContent {
        ScreenTitle(picker?.label ?: "Adapter setup", onBack, trailing = null)
        when (radioStatus) {
            VirtualStatus.Permission -> PrimaryAction("Allow nearby devices", onPermission)
            VirtualStatus.BluetoothOff -> PrimaryAction("Turn on Bluetooth", onBluetoothSettings)
            VirtualStatus.UnsupportedPhone -> Text("This phone needs Bluetooth LE to connect to an adapter.")
            else -> Unit
        }
        if (picker != null) {
            Text(
                if (picker == VirtualSource.Engine) {
                    "Choose the adapter in your car’s main OBD-II port."
                } else {
                    "Choose the adapter in your swap’s separate transmission port."
                }
            )
            if (radioStatus == null) {
                if (scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Looking for adapters…")
                } else {
                    val candidates = results.filter { bindings.accepts(picker, it.address) }
                    candidates.forEach {
                        SettingsRow(it.name, "Ends in ${it.address.takeLast(5)}", true, { onChoose(picker, it.address) })
                    }
                    if (candidates.isEmpty()) {
                        Text("No available adapters found. Turn on the ignition and disconnect other apps or gauges using the adapter.")
                    }
                    TextButton({ onPick(picker) }) { Text("Search again") }
                }
            }
        } else {
            val engine = bindings.address(VirtualSource.Engine)
            val transmission = bindings.address(VirtualSource.Transmission)
            SettingsRow(
                "Main adapter",
                engine?.let { "${statuses[VirtualSource.Engine]?.label} · Ends in ${it.takeLast(5)}" } ?: "Connect to your car’s OBD-II port",
                radioStatus == null,
                { onPick(VirtualSource.Engine) }
            )
            if (engine != null && VirtualSource.Transmission.available) {
                SettingsRow(
                    if (transmission == null) "Add transmission adapter" else "Transmission adapter",
                    transmission?.let { "${statuses[VirtualSource.Transmission]?.label} · Ends in ${it.takeLast(5)}" }
                        ?: "Optional for swaps with a separate transmission port",
                    radioStatus == null,
                    { onPick(VirtualSource.Transmission) }
                )
            }
            if (statuses[VirtualSource.Transmission] == VirtualStatus.CheckPort && transmission != null) {
                Text("Use the separate transmission port with ignition on. This setup checks for the supported JSS controller.")
            }
            if (transmission != null) {
                TextButton(onRemoveTransmission) { Text("Remove transmission adapter") }
            }
            onManageAccess?.let {
                SettingsRow("Subscription", "Manage or restore access", true, it)
            }
            androidx.compose.foundation.layout.Box {
                SettingsRow("Measurement units", units.name, true, { unitsMenu = true })
                DropdownMenu(unitsMenu, { unitsMenu = false }) {
                    MeasurementSystem.entries.forEach { system ->
                        DropdownMenuItem({ Text(system.name) }, {
                            unitsMenu = false
                            onUnits(system)
                        })
                    }
                }
            }
        }
    }
}
