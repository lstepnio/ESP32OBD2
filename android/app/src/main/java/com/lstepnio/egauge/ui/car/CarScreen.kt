package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.CarUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarScreen(
    state: CarUiState,
    onSetup: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
    onFindAdapters: () -> Unit = {},
    onChooseAdapter: (com.lstepnio.egauge.AdapterBinding?) -> Unit = {},
    onSendAdapter: () -> Unit = {},
    onSaveAction: (com.lstepnio.egauge.PageAction?) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    alerts: @Composable () -> Unit = {},
    onSaveIdlePresets: (List<com.lstepnio.egauge.IdlePreset>) -> Unit = {},
    onClearCodes: (() -> Unit)? = null,
    faultActionStatus: String? = null
) {
    var profilesOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var adapterOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var actionsOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var idleOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var faultsOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var selectedFault by rememberSaveable(state.activeId) { mutableStateOf<String?>(null) }
    val selectFault: (com.lstepnio.egauge.ui.state.CarFaultUi) -> Unit = { fault ->
        selectedFault = "${fault.source}:${fault.code}"
        faultsOpen = false
    }
    var searched by remember(state.activeId, adapterOpen) { mutableStateOf(false) }
    LaunchedEffect(adapterOpen, state.adapterAvailable) {
        if (adapterOpen && state.adapterAvailable && !searched) {
            searched = true
            onFindAdapters()
        }
    }
    val faults = com.lstepnio.egauge.ui.state.carFaults(state)
    LaunchedEffect(faults, selectedFault) {
        if (selectedFault != null && faults.none { "${it.source}:${it.code}" == selectedFault }) selectedFault = null
    }
    ScreenContent(scrollKey = state.activeId) {
        ScreenTitle("Car")
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsRow("Your car", state.name, state.canManageVehicles && !state.profileError, {
                    profilesOpen =
                        true
                })
                if (!state.adapterAvailable && !state.canCheck && !state.gaugeKnown) {
                    PrimaryAction("Set up gauge", onSetup)
                } else if (state.setupNeeded && state.adapterSelected != null &&
                    state.adapterAvailable
                ) {
                    PrimaryAction(
                        "Use this car on gauge",
                        onSendAdapter,
                        enabled = state.canSendAdapter
                    )
                }
                Panel {
                    SectionTitle("Fault codes")
                    val summary = com.lstepnio.egauge.ui.state.carHealthStatus(state)
                    Text(
                        summary.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (summary.tone in
                            setOf(StatusTone.Neutral, StatusTone.Disabled, StatusTone.Loading)
                        ) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            toneColor(summary.tone)
                        },
                        modifier = Modifier.semantics {
                            if (summary.tone in
                                setOf(
                                    StatusTone.Warning,
                                    StatusTone.Critical,
                                    StatusTone.Stale,
                                    StatusTone.Error
                                )
                            ) {
                                liveRegion = LiveRegionMode.Polite
                            }
                        }
                    )
                    Text(
                        summary.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (faults.isNotEmpty()) {
                        HorizontalDivider()
                        faults.take(3).forEach { fault -> FaultCodeRow(fault) { selectFault(fault) } }
                        if (faults.size > 3) {
                            TextButton({ faultsOpen = true }) { Text("View all ${faults.size} codes") }
                        }
                    }
                    onClearCodes?.let { action -> TextButton(action) { Text("Clear engine codes") } }
                    faultActionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                alerts()
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsRow(
                    if (state.adapterSelected == null &&
                        state.adapterAvailable
                    ) {
                        "Choose adapter"
                    } else {
                        "Adapter"
                    },
                    if (state.adapterSelected ==
                        null
                    ) {
                        "Select the adapter plugged into this car"
                    } else {
                        "Manage adapter"
                    },
                    state.adapterAvailable,
                    { adapterOpen = true }
                )
                SettingsRow("Air up & winch", "Idle presets", state.canEditActions, { idleOpen = true })
                SettingsRow(
                    "Gauge gestures",
                    if (state.actions.isEmpty()) "No gesture assigned" else "Jump to page",
                    state.canEditActions,
                    { actionsOpen = true }
                )
            }
        })
    }
    if (idleOpen) IdleActionsSheet(state.activeId, state.idlePresets, state.canEditActions, onSaveIdlePresets, { idleOpen = false })
    if (faultsOpen) {
        ModalBottomSheet(onDismissRequest = { faultsOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                DialogContent {
                    SectionTitle("Fault codes")
                    Text(com.lstepnio.egauge.ui.state.carHealthStatus(state).detail)
                    if (faults.isEmpty()) Text("No recorded codes. Checks continue automatically.")
                    faults.forEach { fault ->
                        HorizontalDivider()
                        FaultCodeRow(fault) { selectFault(fault) }
                    }
                }
            }
        }
    }
    faults.firstOrNull { "${it.source}:${it.code}" == selectedFault }?.let { fault ->
        ModalBottomSheet(onDismissRequest = { selectedFault = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                DialogContent {
                    FaultCodeExplanation(fault)
                    TextButton({ selectedFault = null }) { Text("Close") }
                }
            }
        }
    }
    if (actionsOpen) PageActionsSheet(state, { actionsOpen = false }, onSaveAction)
    if (profilesOpen) {
        ProfileDialog(state, {
            profilesOpen = false
        }, onSelectProfile, onCreateProfile, onDeleteProfile)
    }
    if (adapterOpen) {
        ModalBottomSheet(onDismissRequest = { adapterOpen = false }) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                DialogContent {
                    SectionTitle("Vehicle adapter")
                    Text(
                        if (state.adapterSelected ==
                            null
                        ) {
                            "Choose the adapter plugged into this car."
                        } else {
                            "An adapter is saved for this car. Connection checks run automatically."
                        }
                    )
                    state.adapterCandidates.forEach { candidate ->
                        SettingsRow(
                            candidate.name,
                            candidate.binding.address.takeLast(5),
                            state.adapterAvailable,
                            { onChooseAdapter(candidate.binding) }
                        )
                    }
                    PrimaryAction(
                        "Find adapter",
                        onFindAdapters,
                        enabled =
                            state.adapterAvailable && !state.profileError
                    )
                    if (state.adapterSelected != null) {
                        TextButton({
                            onChooseAdapter(null)
                        }, enabled = state.adapterAvailable) { Text("Remove adapter") }
                    }
                    if (state.setupNeeded && state.adapterSelected != null) {
                        PrimaryAction("Use this car on gauge", {
                            onSendAdapter()
                            adapterOpen =
                                false
                        }, enabled = state.canSendAdapter)
                    }
                    if (state.adapterMessage != null && state.adapterCandidates.isEmpty()) {
                        Text(state.adapterMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Coverage, controller attribution and category freshness belong in Expert. */
@Composable
fun VehicleFaultCoverage(state: CarUiState) {
    Panel {
        SectionTitle("Vehicle fault coverage")
        state.faultSources.forEach { source ->
            SectionTitle(source.title)
            Text(source.status.title, color = toneColor(source.status.tone))
            Text(source.status.detail, style = MaterialTheme.typography.bodyMedium)
            source.categories.forEach { category ->
                Text(category.name, style = MaterialTheme.typography.labelLarge)
                Text(category.status, style = MaterialTheme.typography.bodySmall)
                category.faults.forEach { Text("${it.code} · ${it.description} · ${it.category}") }
                if (category.incomplete) {
                    Text(
                        "Partial list. More codes may be present.",
                        color = LocalSemanticColors.current.warning
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    state: CarUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val duplicate = state.profiles.any { it.name.equals(name.trim(), true) }
    if (deleteId ==
        null
    ) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Your cars") }, text = {
            DialogContent {
                state.profiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth()) {
                        TextButton({
                            onSelect(profile.id)
                            onDismiss()
                        }, Modifier.weight(1f), enabled = state.canManageVehicles) {
                            Text(profile.name)
                            if (profile.id == state.activeId) EGaugeIcon(GaugeIcon.Check)
                        }
                        TextButton(
                            { deleteId = profile.id },
                            enabled =
                                state.canManageVehicles && state.profiles.size > 1
                        ) {
                            Text("Delete")
                        }
                    }
                }
                if (state.profiles.size ==
                    1
                ) {
                    Text(
                        "Keep at least one car on this phone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    name,
                    {
                        name = it.take(32)
                    },
                    label = { Text("Car name") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = {
                        if (duplicate) {
                            Text("This name is already used. Choose a different name.")
                        } else if (state.profiles.size >= 8) {
                            Text("All eight profile spaces are in use.")
                        }
                    }
                )
            }
        }, confirmButton = {
            Button(
                {
                    onCreate(name.trim())
                    onDismiss()
                },
                enabled =
                    state.canManageVehicles && name.isNotBlank() && !duplicate &&
                        state.profiles.size < 8
            ) {
                Text("Add car")
            }
        }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
    }
    state.profiles.firstOrNull { it.id == deleteId }?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete ${profile.name}?") },
            text = {
                Text(
                    "This deletes its saved pages, alerts and adapter setup from this phone, including any transmission child. Installed gauge settings stay unchanged. Gauges assigned to this car will need reassignment."
                )
            },
            confirmButton = {
                TextButton(
                    {
                        onDelete(profile.id)
                        deleteId = null
                        onDismiss()
                    },
                    enabled = state.canManageVehicles && state.profiles.size > 1
                ) { Text("Delete car") }
            },
            dismissButton = { TextButton({ deleteId = null }) { Text("Keep car") } }
        )
    }
}
