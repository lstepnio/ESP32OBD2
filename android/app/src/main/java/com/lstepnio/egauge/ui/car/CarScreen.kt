package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    onDeleteProfile: (String) -> Unit = {},
    onSaveIdlePresets: (List<com.lstepnio.egauge.IdlePreset>) -> Unit = {},
    onVehicleCommand: (com.lstepnio.egauge.VehicleCommandKind, Boolean, com.lstepnio.egauge.IdlePreset?) -> Unit = { _, _, _ -> },
    currentAlerts: List<com.lstepnio.egauge.ui.state.CarAttentionAlert> = emptyList(),
    hasRecordedHistory: Boolean = false,
    onOpenAlert: (String) -> Unit = {},
    onHistory: () -> Unit = {},
    onAlertSettings: () -> Unit = {},
    onAdvanced: () -> Unit = {},
    onGaugeAssignment: () -> Unit = {}
) {
    var profilesOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var adapterOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var idleOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var destination by rememberSaveable(state.activeId) { mutableStateOf("overview") }
    var warningSource by rememberSaveable(state.activeId) { mutableStateOf("") }
    var selectedFault by rememberSaveable(state.activeId) { mutableStateOf<String?>(null) }
    val overviewScroll = key(state.activeId) { rememberScrollState() }
    val settingsScroll = key(state.activeId) { rememberScrollState() }
    val allScroll = key(state.activeId) { rememberScrollState() }
    val detailScroll = key(state.activeId) { rememberScrollState() }
    val faults = com.lstepnio.egauge.ui.state.carFaults(state)
    val issues = com.lstepnio.egauge.ui.state.carAttention(state, currentAlerts)
    val selectFault: (com.lstepnio.egauge.ui.state.CarFaultUi) -> Unit = { fault ->
        selectedFault = "${fault.source}:${fault.code}"
        destination = "fault"
    }
    androidx.activity.compose.BackHandler(destination != "overview") { destination = "overview" }
    LaunchedEffect(faults, selectedFault) {
        if (selectedFault != null && faults.none { "${it.source}:${it.code}" == selectedFault }) {
            selectedFault = null
            destination = "overview"
        }
    }
    var searched by remember(state.activeId, adapterOpen) { mutableStateOf(false) }
    LaunchedEffect(adapterOpen, state.adapterAvailable) {
        if (adapterOpen && state.adapterAvailable && !searched) {
            searched = true
            onFindAdapters()
        }
    }
    ScreenContent(
        scrollKey = state.activeId to destination,
        scrollState = when (destination) {
            "overview" -> overviewScroll
            "settings" -> settingsScroll
            "all" -> allScroll
            else -> detailScroll
        }
    ) {
        if (destination == "warning") {
            ScreenTitle("$warningSource warning", { destination = "overview" })
            Text("The car reports a warning. A fault code has not been identified yet.")
            Text("Checks continue automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (destination == "fault") {
            ScreenTitle("Vehicle fault", { destination = "overview" })
            faults.firstOrNull { "${it.source}:${it.code}" == selectedFault }?.let { FaultCodeExplanation(it) }
        } else if (destination == "settings") {
            ScreenTitle("Car settings", { destination = "overview" })
            Panel {
                SettingsRow("Your car", state.name, state.canManageVehicles && !state.profileError) { profilesOpen = true }
                SettingsRow("Gauge", "Assignment and connection", onClick = onGaugeAssignment)
                SettingsRow("Adapter", if (state.adapterSelected == null) "Choose vehicle adapter" else "Manage adapter", state.adapterAvailable) { adapterOpen = true }
            }
            Panel { SettingsRow("Advanced", onClick = onAdvanced) }
        } else if (destination == "all") {
            ScreenTitle("Needs attention", { destination = "overview" })
            Panel {
                issues.forEach { issue ->
                    CarAttentionRow(issue, onClick = {
                        if (issue.fault != null) {
                            selectFault(issue.fault)
                        } else if (issue.warningSource != null) {
                            warningSource = issue.warningSource
                            destination = "warning"
                        } else {
                            issue.alertId?.let(onOpenAlert)
                        }
                    })
                }
            }
        } else {
            ScreenTitle("Car")
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton({ profilesOpen = true }, Modifier.weight(1f), enabled = state.canManageVehicles && !state.profileError) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(state.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        EGaugeIcon(GaugeIcon.Next, modifier = Modifier.rotate(90f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton({ destination = "settings" }) { EGaugeIcon(GaugeIcon.Settings, "Car settings") }
            }
            if (!state.adapterAvailable && !state.canCheck && !state.gaugeKnown) {
                PrimaryAction("Set up gauge", onSetup)
            } else if (state.setupNeeded && state.adapterSelected != null && state.adapterAvailable) {
                PrimaryAction("Review car setup", onSendAdapter, enabled = state.canSendAdapter)
            }
            if (issues.isNotEmpty()) {
                Panel {
                    SectionTitle("Needs attention")
                    issues.take(3).forEach { issue ->
                        CarAttentionRow(issue, onClick = {
                            if (issue.fault != null) {
                                selectFault(issue.fault)
                            } else if (issue.warningSource != null) {
                                warningSource = issue.warningSource
                                destination = "warning"
                            } else {
                                issue.alertId?.let(onOpenAlert)
                            }
                        })
                    }
                    if (issues.size > 3) TextButton({ destination = "all" }) { Text("View all") }
                }
            } else if (com.lstepnio.egauge.ui.state.carChecksComplete(state)) {
                Panel {
                    Text("No active alerts", style = MaterialTheme.typography.titleMedium)
                    Text("Checked recently", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (faults.isNotEmpty()) {
                Panel {
                    SectionTitle("Last checked")
                    faults.take(3).forEach { fault ->
                        FaultCodeRow(fault, onClick = { selectFault(fault) })
                    }
                }
            } else if (hasRecordedHistory) {
                Panel {
                    SettingsRow("Previous activity", "View recorded issues", onClick = onHistory)
                }
            }
            Panel {
                SettingsRow("Vehicle actions", "Air up, Winch", state.canEditActions) { idleOpen = true }
                SettingsRow("Alert settings", onClick = onAlertSettings)
                SettingsRow("History", onClick = onHistory)
            }
        }
    }
    if (idleOpen) VehicleActionsSheet(state, onSaveIdlePresets, onVehicleCommand, { idleOpen = false })
    if (profilesOpen) ProfileDialog(state, { profilesOpen = false }, onSelectProfile, onCreateProfile, onDeleteProfile)
    if (adapterOpen) {
        ModalBottomSheet(onDismissRequest = { adapterOpen = false }) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                DialogContent {
                    SectionTitle("Vehicle adapter")
                    state.adapterCandidates.forEach { candidate ->
                        SettingsRow(candidate.name, candidate.binding.address.takeLast(5), state.adapterAvailable) { onChooseAdapter(candidate.binding) }
                    }
                    PrimaryAction("Find adapter", onFindAdapters, enabled = state.adapterAvailable && !state.profileError)
                    if (state.adapterSelected != null) TextButton({ onChooseAdapter(null) }, enabled = state.adapterAvailable) { Text("Remove adapter") }
                    if (state.setupNeeded && state.adapterSelected != null) {
                        PrimaryAction("Review car setup", {
                            onSendAdapter()
                            adapterOpen = false
                        }, enabled = state.canSendAdapter)
                    }
                    if (state.adapterMessage != null && state.adapterCandidates.isEmpty()) Text(state.adapterMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CarAttentionRow(issue: com.lstepnio.egauge.ui.state.CarAttentionIssue, onClick: () -> Unit) {
    val critical = issue.severity >= com.lstepnio.egauge.AlertSeverity.Critical.ordinal
    Surface(
        onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
            if (critical) stateDescription = "Critical alert"
        }
    ) {
        Row(
            Modifier.padding(vertical = 14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EGaugeIcon(GaugeIcon.Warning, color = if (critical) LocalSemanticColors.current.critical else LocalSemanticColors.current.warning)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(issue.title, style = MaterialTheme.typography.titleMedium)
                Text(issue.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EGaugeIcon(GaugeIcon.Next)
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
