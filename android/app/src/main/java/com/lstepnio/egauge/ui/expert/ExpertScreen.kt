package com.lstepnio.egauge.ui.expert

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import com.lstepnio.egauge.AdapterBinding
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.BuildConfig
import com.lstepnio.egauge.samePhysicalAdapter
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
    val phoneAlertTest: () -> Unit = {},
    val openPrimaryAdapter: () -> Unit = {},
    val displayShowcase: (Boolean) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertScreen(state: ExpertUiState, actions: ExpertActions, faultCoverage: @Composable () -> Unit = {}) {
    var secondOpen by rememberSaveable(state.vehicleId) { mutableStateOf(false) }
    var removeOpen by rememberSaveable(state.vehicleId) { mutableStateOf(false) }
    ScreenContent {
        ScreenTitle("Expert")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Gauge checks")
            if(BuildConfig.DEBUG)SettingsRow("Phone alert test","Simulated road notice · expires after 30 seconds",state.canRead,actions.phoneAlertTest)
            if(BuildConfig.DEBUG && state.canShowcase) SettingsRow(
                if(state.showcaseActive) "Stop display demo" else "Showcase gauge screens",
                if(state.showcaseActive) "Generated data · hold gauge to exit" else "Temporary generated examples · keeps saved dashboard",
                true, { actions.displayShowcase(!state.showcaseActive) })
            PrimaryAction("Refresh gauge data", actions.refreshSettings, enabled = state.canRead)
            SettingsRow("Check vehicle faults", enabled = state.canRead, onClick = actions.readDiagnostics)
            SettingsRow("Read hardware capacity", enabled = state.canReadHardware, onClick = actions.readHardware)
            SettingsRow("Check installed firmware", enabled = state.canRead, onClick = actions.readFirmware)
            if (state.canAdoptGaugeSettings) SettingsRow("Use saved gauge setup", enabled = state.canRead, onClick = actions.adoptGaugeSettings)
            if (BuildConfig.DEBUG && state.vehicleName.isNotBlank()) {
                SectionTitle("Vehicle connections")
                Text(state.vehicleName, style = MaterialTheme.typography.titleMedium)
                if (state.primarySource == "ECM") {
                    SettingsRow("Primary adapter", "Engine (ECM)", state.canEditVehicle, actions.openPrimaryAdapter)
                    if (state.hasTransmission) {
                        SettingsRow("Second adapter", if (state.secondAdapter == null) "Choose transmission adapter" else "Transmission adapter selected",
                            state.canEditVehicle) { secondOpen = true }
                        SettingsRow("Use both adapters on this gauge", if (state.bothAdapters) "On · review and send setup" else "Off",
                            state.canEditVehicle && (state.canUseBothAdapters || state.bothAdapters)) { actions.useBoth(!state.bothAdapters) }
                        if (!state.bothAdapters) state.secondAdapterSetupIssue?.let { Text(it) }
                    } else {
                        SettingsRow("Add second adapter", "For a swap with a separate transmission connector",
                            state.canEditVehicle && state.hasPrimaryAdapter) { actions.addTransmission(); secondOpen = true }
                        if (!state.hasPrimaryAdapter) Text("Choose the primary engine adapter in Car first.")
                    }
                }
                if (state.legacyParents.isNotEmpty()) {
                    Text("Legacy transmission setup. Choose its engine vehicle to move the pages and adapter into a child connection.")
                    state.legacyParents.forEach { parent ->
                        SettingsRow("Attach to ${parent.name}", "Keeps transmission pages; review and send afterward",
                            state.canEditVehicle, { actions.attachLegacy(parent.id) })
                    }
                    if (state.legacyParents.isEmpty()) Text("Set up an engine vehicle and its primary adapter first.")
                }

            }
            state.vehicleMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            faultCoverage()
            SectionTitle("Device data")
            DetailContent(state.details)
        }
    }
    if (secondOpen) {
        ModalBottomSheet(onDismissRequest = { secondOpen = false }) {
            SecondAdapterChoices(state, actions, onRemove = { secondOpen = false; removeOpen = true })
        }
    }
    if (removeOpen) AlertDialog(onDismissRequest = { removeOpen = false },
        title = { Text("Remove transmission child?") },
        text = { Text("This removes the second adapter selection. All vehicle pages and gestures stay saved and will use the primary adapter after you review and send. Installed gauge settings stay unchanged.") },
        confirmButton = { TextButton({ actions.removeTransmission(); removeOpen = false }, enabled = state.canEditVehicle) { Text("Remove") } },
        dismissButton = { TextButton({ removeOpen = false }) { Text("Cancel") } })
}

@Composable
private fun SecondAdapterChoices(state: ExpertUiState, actions: ExpertActions, onRemove: () -> Unit) {
    var searched by remember(state.vehicleId) { mutableStateOf(false) }
    LaunchedEffect(state.canFindAdapter, state.hasTransmission) {
        if (state.canFindAdapter && state.hasTransmission && !searched) {
            searched = true
            actions.findAdapters()
        }
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
        DialogContent {
            SectionTitle("Second adapter")
            Text("Choose the adapter plugged into the transmission connector. Both adapters share this car's dashboard.")
            if (state.secondAdapter != null) Text("Transmission adapter selected · ${state.secondAdapter.address.takeLast(5)}")
            state.adapterDiscoveryBlocker?.let { Text(it) }
            state.adapterCandidates.forEach { candidate ->
                val primary = samePhysicalAdapter(state.primaryAdapter, candidate.binding)
                SettingsRow(candidate.name, if (primary) "Primary engine adapter · already in use" else candidate.binding.address.takeLast(5),
                    state.canEditVehicle && state.hasTransmission && !primary, { actions.chooseAdapter(candidate.binding) })
            }
            PrimaryAction("Find transmission adapter", actions.findAdapters, enabled = state.canFindAdapter && state.hasTransmission)
            state.adapterMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.secondAdapterSetupIssue?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (state.secondAdapter != null) TextButton({ actions.chooseAdapter(null) }, enabled = state.canEditVehicle) { Text("Remove adapter selection") }
            TextButton(onRemove, enabled = state.canEditVehicle && state.hasTransmission) { Text("Remove second adapter") }
        }
    }
}
