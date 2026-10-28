package com.lstepnio.egauge.ui.expert

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.AdapterBinding
import com.lstepnio.egauge.BuildConfig
import com.lstepnio.egauge.DiagnosticCodeFilter
import com.lstepnio.egauge.DiagnosticCodeFilters
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.samePhysicalAdapter
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.ExpertUiState

data class ExpertActions(
    val refreshSettings: () -> Unit,
    val readDiagnostics: () -> Unit,
    val readHardware: () -> Unit,
    val adoptGaugeSettings: () -> Unit,
    val addTransmission: () -> Unit = {},
    val removeTransmission: () -> Unit = {},
    val findAdapters: () -> Unit = {},
    val chooseAdapter: (AdapterBinding?) -> Unit = {},
    val attachLegacy: (String) -> Unit = {},
    val useBoth: (Boolean) -> Unit = {},
    val displayShowcase: (Boolean) -> Unit = {},
    val addCodeFilter: (String, String, String) -> Unit = { _, _, _ -> },
    val removeCodeFilter: (String, DiagnosticCodeFilter) -> Unit = { _, _ -> }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertScreen(
    state: ExpertUiState,
    actions: ExpertActions,
    faultCoverage: @Composable () -> Unit = {},
    carAdvanced: Boolean = false,
    onBack: () -> Unit = {},
    onReview: () -> Unit = {}
) {
    var tool by rememberSaveable(state.vehicleId) { mutableStateOf<String?>(null) }
    var filtersOpen by rememberSaveable(state.vehicleId) { mutableStateOf(false) }
    var secondOpen by rememberSaveable(state.vehicleId) { mutableStateOf(false) }
    var removeOpen by rememberSaveable(state.vehicleId) { mutableStateOf(false) }
    if (filtersOpen) {
        HiddenFaultCodesScreen(state, actions, { filtersOpen = false }, onReview)
        return
    }
    BackHandler(carAdvanced) { onBack() }
    ScreenContent {
        ScreenTitle(if (carAdvanced) "Advanced" else "Expert", if (carAdvanced) onBack else null)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!carAdvanced) {
                Text(
                    "Advanced setup and troubleshooting",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.vehicleId.isNotBlank() || state.vehicleName.isNotBlank()) {
                SectionTitle(state.vehicleName.ifBlank { "Vehicle" })
                if (BuildConfig.DEBUG && state.primarySource == "ECM") {
                    SettingsRow(
                        "Second adapter",
                        when {
                            state.bothAdapters -> "Engine and transmission share one dashboard"
                            state.hasTransmission -> "Transmission connection saved"
                            else -> "Optional · for a separate transmission connector"
                        },
                        state.canEditVehicle && (state.hasTransmission || state.hasPrimaryAdapter)
                    ) {
                        if (!state.hasTransmission) actions.addTransmission()
                        secondOpen = true
                    }
                }
                if (BuildConfig.DEBUG && !state.hasPrimaryAdapter && state.primarySource == "ECM") {
                    Text(
                        "Choose your vehicle adapter in Car before adding a second one.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                SettingsRow(
                    "Hidden fault codes",
                    null,
                    state.canEditVehicle
                ) { filtersOpen = true }
                if (state.legacyParents.isNotEmpty()) {
                    SettingsRow(
                        "Connect transmission setup",
                        "Link an existing setup to its vehicle"
                    ) { tool = "legacy" }
                }
            }
            if (!carAdvanced) {
                SectionTitle("Troubleshooting")
                SettingsRow("Fault diagnostics", "See which vehicle checks have completed") {
                    tool =
                        "faults"
                }
                SettingsRow("Device data", "Gauge information and support checks") { tool = "device" }
                if (BuildConfig.DEBUG && state.canShowcase) {
                    SettingsRow(
                        if (state.showcaseActive) "Stop display demo" else "Preview gauge screens",
                        "Generated examples · hold the gauge to exit",
                        onClick = {
                            actions.displayShowcase(!state.showcaseActive)
                        }
                    )
                }
            }
        }
    }
    if (tool != null) {
        ModalBottomSheet(onDismissRequest = { tool = null }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(
                    rememberScrollState()
                ).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tool) {
                    "faults" -> {
                        SectionTitle("Fault diagnostics")
                        Text(
                            "Checks update automatically while connected. Missing checks do not mean there are no faults."
                        )
                        faultCoverage()
                        SettingsRow(
                            "Check again",
                            "Retry the vehicle fault check",
                            state.canRead,
                            actions.readDiagnostics
                        )
                    }

                    "device" -> {
                        SectionTitle("Device data")
                        SectionTitle("Support checks")
                        SettingsRow(
                            "Reload gauge data",
                            "Retry reading the saved setup",
                            state.canRead,
                            actions.refreshSettings
                        )
                        SettingsRow(
                            "Hardware information",
                            "Read display, memory and processor information",
                            state.canReadHardware,
                            actions.readHardware
                        )
                        if (state.canAdoptGaugeSettings) {
                            SettingsRow(
                                "Use saved gauge setup",
                                "Import the setup stored on this gauge",
                                state.canRead,
                                actions.adoptGaugeSettings
                            )
                        }
                        if (state.details.isNotEmpty()) {
                            SectionTitle("Gauge information")
                            DetailContent(state.details)
                        }
                    }

                    "legacy" -> {
                        SectionTitle("Connect transmission setup")
                        Text(
                            "Choose the vehicle this transmission belongs to. Pages and adapter settings stay together. Review and send afterward."
                        )
                        state.legacyParents.forEach { parent ->
                            SettingsRow(parent.name, enabled = state.canEditVehicle) {
                                actions.attachLegacy(parent.id)
                                tool =
                                    null
                            }
                        }
                    }
                }
            }
        }
    }
    if (secondOpen) {
        ModalBottomSheet(onDismissRequest = { secondOpen = false }) {
            SecondAdapterChoices(state, actions, onRemove = {
                secondOpen = false
                removeOpen = true
            })
        }
    }
    if (removeOpen) {
        AlertDialog(
            onDismissRequest = { removeOpen = false },
            title = { Text("Remove second adapter?") },
            text = {
                Text(
                    "This removes the second adapter selection. All vehicle pages and gestures stay saved and will use the primary adapter after you review and send. Installed gauge settings stay unchanged."
                )
            },
            confirmButton = {
                TextButton({
                    actions.removeTransmission()
                    removeOpen = false
                }, enabled = state.canEditVehicle) { Text("Remove") }
            },
            dismissButton = { TextButton({ removeOpen = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SecondAdapterChoices(
    state: ExpertUiState,
    actions: ExpertActions,
    onRemove: () -> Unit
) {
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
            Text(
                "Choose the adapter plugged into the transmission connector. Both adapters share this car's dashboard."
            )
            if (state.secondAdapter !=
                null
            ) {
                Text(
                    "Transmission adapter selected · ${state.secondAdapter.address.takeLast(5)}"
                )
            }
            if (state.hasTransmission) {
                SettingsRow(
                    "Use both adapters on this gauge",
                    if (state.bothAdapters) "On · review and send changes" else "Off · use the primary adapter",
                    state.canEditVehicle && (state.canUseBothAdapters || state.bothAdapters)
                ) {
                    actions.useBoth(!state.bothAdapters)
                }
            }
            state.adapterDiscoveryBlocker?.let { Text(it) }
            state.adapterCandidates.forEach { candidate ->
                val primary = samePhysicalAdapter(state.primaryAdapter, candidate.binding)
                SettingsRow(
                    candidate.name,
                    if (primary) {
                        "Primary engine adapter · already in use"
                    } else {
                        candidate.binding.address.takeLast(
                            5
                        )
                    },
                    state.canEditVehicle && state.hasTransmission && !primary,
                    {
                        actions.chooseAdapter(candidate.binding)
                    }
                )
            }
            PrimaryAction(
                "Find transmission adapter",
                actions.findAdapters,
                enabled =
                    state.canFindAdapter && state.hasTransmission
            )
            state.adapterMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.secondAdapterSetupIssue?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.secondAdapter !=
                null
            ) {
                TextButton({
                    actions.chooseAdapter(null)
                }, enabled = state.canEditVehicle) { Text("Remove adapter selection") }
            }
            TextButton(onRemove, enabled = state.canEditVehicle && state.hasTransmission) {
                Text("Remove second adapter")
            }
        }
    }
}
