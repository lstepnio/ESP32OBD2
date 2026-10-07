package com.lstepnio.egauge.ui.expert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import com.lstepnio.egauge.AdapterBinding
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.BuildConfig
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.ExpertUiState

data class ExpertActions(
    val refreshSettings: () -> Unit,
    val readDiagnostics: () -> Unit,
    val readHardware: () -> Unit,
    val readFirmware: () -> Unit,
    val adoptGaugeSettings: () -> Unit,
    val addTransmission: () -> Unit = {},
    val selectSource: (String) -> Unit = {},
    val removeTransmission: () -> Unit = {},
    val findAdapters: () -> Unit = {},
    val chooseAdapter: (AdapterBinding?) -> Unit = {},
    val attachLegacy: (String) -> Unit = {},
    val useBoth: (Boolean) -> Unit = {},
)

@Composable
fun ExpertScreen(state: ExpertUiState, actions: ExpertActions) {
    var removeOpen by rememberSaveable { mutableStateOf(false) }
    ScreenContent {
        ScreenTitle("Expert")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Gauge checks")
            PrimaryAction("Refresh gauge data", actions.refreshSettings, enabled = state.canRead)
            SettingsRow("Check vehicle faults", enabled = state.canRead, onClick = actions.readDiagnostics)
            SettingsRow("Read hardware capacity", enabled = state.canReadHardware, onClick = actions.readHardware)
            SettingsRow("Check installed firmware", enabled = state.canRead, onClick = actions.readFirmware)
            if (state.canAdoptGaugeSettings) SettingsRow("Use saved gauge setup", enabled = state.canRead, onClick = actions.adoptGaugeSettings)
            if (BuildConfig.DEBUG && state.vehicleName.isNotBlank()) {
                SectionTitle("Vehicle connections")
                Text(state.vehicleName, style = MaterialTheme.typography.titleMedium)
                if (state.primarySource == "ECM") {
                    SettingsRow("Primary adapter", "Engine (ECM)", state.canEditVehicle) { actions.selectSource("ECM") }
                    if (state.hasTransmission || state.bothAdapters) {
                        SettingsRow("Use both adapters on this gauge", if (state.bothAdapters) "On · review and send setup" else "Off",
                            state.canEditVehicle && (state.canUseBothAdapters || state.bothAdapters)) { actions.useBoth(!state.bothAdapters) }
                        if (!state.canUseBothAdapters && !state.bothAdapters) Text("Choose two different adapters and update the gauge to enable this development feature.")
                    }
                    if (state.hasTransmission) {
                        SettingsRow("Child adapter", "Transmission (TCM)", state.canEditVehicle) { actions.selectSource("TCM") }
                        Text("Editing ${if (state.selectedSource == "TCM") "transmission" else "engine"} pages. Both connections belong to this car.")
                        TextButton({ removeOpen = true }, enabled = state.canEditVehicle) { Text("Remove transmission child") }
                    } else {
                        SettingsRow("Add transmission child", "For a swap with a separate diagnostic connector",
                            state.canEditVehicle && state.hasPrimaryAdapter, actions.addTransmission)
                        if (!state.hasPrimaryAdapter) Text("Choose the primary engine adapter in Car first.")
                    }
                } else {
                    Text("Legacy transmission setup. Choose its engine vehicle to move the pages and adapter into a child connection.")
                    state.legacyParents.forEach { parent ->
                        SettingsRow("Attach to ${parent.name}", "Keeps transmission pages; review and send afterward",
                            state.canEditVehicle, { actions.attachLegacy(parent.id) })
                    }
                    if (state.legacyParents.isEmpty()) Text("Set up an engine vehicle with a different primary adapter first.")
                }
                if (state.hasTransmission && state.selectedSource == "TCM") {
                    Text(state.selectedAdapter?.let { "Selected transmission adapter · $it" } ?: "Choose a different adapter for the transmission child.")
                    state.adapterCandidates.forEach { candidate ->
                        SettingsRow(candidate.name, candidate.binding.address.takeLast(5), state.canEditVehicle,
                            { actions.chooseAdapter(candidate.binding) })
                    }
                    SettingsRow("Find transmission adapter", enabled = state.canFindAdapter, onClick = actions.findAdapters)
                    state.adapterMessage?.let { Text(it) }
                    if (state.selectedAdapter != null) TextButton({ actions.chooseAdapter(null) }, enabled = state.canEditVehicle) {
                        Text("Remove child adapter selection")
                    }
                }
            }
            state.vehicleMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            SectionTitle("Device data")
            DetailContent(state.details)
        }
    }
    if (removeOpen) AlertDialog(onDismissRequest = { removeOpen = false },
        title = { Text("Remove transmission child?") },
        text = { Text("This removes its saved pages and adapter selection from this phone. Gauges using this child keep their installed setup until you review and send a replacement.") },
        confirmButton = { TextButton({ actions.removeTransmission(); removeOpen = false }, enabled = state.canEditVehicle) { Text("Remove") } },
        dismissButton = { TextButton({ removeOpen = false }) { Text("Cancel") } })
}
