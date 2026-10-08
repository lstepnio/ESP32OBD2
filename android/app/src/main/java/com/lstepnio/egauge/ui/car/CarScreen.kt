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
fun CarScreen(state: CarUiState, onSetup: () -> Unit,
              onSelectProfile: (String) -> Unit, onCreateProfile: (String) -> Unit,
              onFindAdapters: () -> Unit = {}, onChooseAdapter: (com.lstepnio.egauge.AdapterBinding?) -> Unit = {},
              onSendAdapter: () -> Unit = {},
              onSaveAction: (com.lstepnio.egauge.PageAction?) -> Unit = {}, onDeleteProfile: (String) -> Unit = {}, alerts: @Composable () -> Unit = {}) {
    var profilesOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var adapterOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var actionsOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var faultsOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    val faults = com.lstepnio.egauge.ui.state.carFaults(state)
    ScreenContent(scrollKey = state.activeId) {
        ScreenTitle("Car")
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsRow("Your car", state.name, state.canManageVehicles && !state.profileError, { profilesOpen = true })
                if (!state.adapterAvailable && !state.canCheck && !state.gaugeKnown)
                    PrimaryAction("Set up gauge", onSetup)
                else if (state.setupNeeded && state.adapterSelected != null && state.adapterAvailable)
                    PrimaryAction("Use this car on gauge", onSendAdapter, enabled = state.canSendAdapter)
                else if (state.adapterSelected == null && state.adapterAvailable)
                    PrimaryAction("Choose adapter", { adapterOpen = true }, enabled = state.adapterAvailable)
                Panel {
                    SectionTitle("Vehicle health")
                    val summary = com.lstepnio.egauge.ui.state.carHealthStatus(state)
                    Text(summary.title, style = MaterialTheme.typography.titleMedium, color = if (summary.tone in setOf(StatusTone.Neutral, StatusTone.Disabled, StatusTone.Loading))
                            MaterialTheme.colorScheme.onSurfaceVariant else toneColor(summary.tone),
                        modifier = Modifier.semantics {
                            if (summary.tone in setOf(StatusTone.Warning, StatusTone.Critical, StatusTone.Stale, StatusTone.Error))
                                liveRegion = LiveRegionMode.Polite
                        })
                    Text(summary.detail, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (faults.isNotEmpty()) SettingsRow("Fault codes", "${faults.size} recorded", onClick = { faultsOpen = true })
                }
                alerts()
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsRow("Adapter", if (state.adapterSelected == null) "Not selected" else "Manage adapter",
                    state.adapterAvailable, { adapterOpen = true })
                SettingsRow("Gauge gestures", if (state.actions.isEmpty()) "No gesture assigned" else "Jump to page",
                    state.canEditActions, { actionsOpen = true })
            }
        })
    }
    if (faultsOpen) {
        ModalBottomSheet(onDismissRequest = { faultsOpen = false }) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                DialogContent {
                    SectionTitle("Fault codes")
                    Text(com.lstepnio.egauge.ui.state.carHealthStatus(state).detail)
                    if (faults.isEmpty()) Text("No recorded codes. Checks continue automatically.")
                    faults.forEach { fault ->
                        HorizontalDivider()
                        Text(fault.code, style = MaterialTheme.typography.titleLarge)
                        Text(fault.description, style = MaterialTheme.typography.bodyLarge)
                        Text("${fault.source} · ${fault.categories}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (actionsOpen) PageActionsSheet(state, { actionsOpen = false }, onSaveAction)
    if (profilesOpen) ProfileDialog(state, { profilesOpen = false }, onSelectProfile, onCreateProfile, onDeleteProfile)
    if (adapterOpen) {
        ModalBottomSheet(onDismissRequest = { adapterOpen = false }) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
                DialogContent {
                    SectionTitle("Vehicle adapter")
                    Text(if (state.adapterSelected == null) "Choose the adapter plugged into this car."
                        else "An adapter is saved for this car. Connection checks run automatically.")
                    if (state.adapterCandidates.isNotEmpty()) {
                        Text("Choose the adapter plugged into this car.")
                    }
                    state.adapterCandidates.forEach { candidate ->
                        SettingsRow(candidate.name, candidate.binding.address.takeLast(5), state.adapterAvailable,
                            { onChooseAdapter(candidate.binding) })
                    }
                    PrimaryAction("Find adapter", onFindAdapters, enabled = state.adapterAvailable && !state.profileError)
                    if (state.adapterSelected != null)
                        TextButton({ onChooseAdapter(null) }, enabled = state.adapterAvailable) { Text("Remove adapter") }
                    if (state.setupNeeded && state.adapterSelected != null)
                        PrimaryAction("Use this car on gauge", { onSendAdapter(); adapterOpen = false }, enabled = state.canSendAdapter)
                    if (state.adapterMessage != null && state.adapterCandidates.isEmpty())
                        Text(state.adapterMessage, style = MaterialTheme.typography.bodyMedium)
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
                if (category.incomplete) Text("Partial list. More codes may be present.", color = LocalSemanticColors.current.warning)
            }
        }
    }
}

@Composable
fun ClearCodesDialog(onDismiss: () -> Unit, example: Boolean = false, onExampleClear: () -> Unit = {}) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (example) "Example: clear fault codes?" else "Clearing codes is not available yet") },
        text = { Text("Clearing codes erases diagnostic information and may reset emissions checks. It does not fix the cause. Permanent codes may remain." +
            if (example) " This example never sends a command to a car." else " Keep the codes for diagnosis.") },
        confirmButton = {
            if (example) Button(onExampleClear) { Text("Clear example codes") }
            else TextButton(onDismiss) { Text("Close") }
        }, dismissButton = { if (example) TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun ProfileDialog(state: CarUiState, onDismiss: () -> Unit,
    onSelect: (String) -> Unit, onCreate: (String) -> Unit, onDelete: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val duplicate = state.profiles.any { it.name.equals(name.trim(), true) }
    if (deleteId == null) AlertDialog(onDismissRequest = onDismiss, title = { Text("Your cars") }, text = {
        DialogContent {
            state.profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth()) {
                    TextButton({ onSelect(profile.id); onDismiss() }, Modifier.weight(1f), enabled = state.canManageVehicles) {
                        Text(profile.name)
                        if (profile.id == state.activeId) EGaugeIcon(GaugeIcon.Check)
                    }
                    TextButton({ deleteId = profile.id }, enabled = state.canManageVehicles && state.profiles.size > 1) {
                        Text("Delete")
                    }
                }
            }
            if (state.profiles.size == 1) Text("Keep at least one car on this phone.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Car name") }, singleLine = true,
                isError = duplicate, supportingText = {
                    if (duplicate) Text("This name is already used. Choose a different name.")
                    else if (state.profiles.size >= 8) Text("All eight profile spaces are in use.")
                })
        }
    }, confirmButton = {
        Button({ onCreate(name.trim()); onDismiss() }, enabled = state.canManageVehicles && name.isNotBlank() && !duplicate && state.profiles.size < 8) { Text("Add car") }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
    state.profiles.firstOrNull { it.id == deleteId }?.let { profile ->
        AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("This deletes its saved pages, alerts and adapter setup from this phone, including any transmission child. Installed gauge settings stay unchanged. Gauges assigned to this car will need reassignment.") },
            confirmButton = { TextButton({ onDelete(profile.id); deleteId = null; onDismiss() },
                enabled = state.canManageVehicles && state.profiles.size > 1) { Text("Delete car") } },
            dismissButton = { TextButton({ deleteId = null }) { Text("Keep car") } })
    }
}
