package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
              onSaveAction: (com.lstepnio.egauge.PageAction?) -> Unit = {}, onDeleteProfile: (String) -> Unit = {}) {
    var profilesOpen by rememberSaveable { mutableStateOf(false) }
    var adapterOpen by rememberSaveable { mutableStateOf(false) }
    var actionsOpen by rememberSaveable(state.activeId) { mutableStateOf(false) }
    var coverageOpen by rememberSaveable { mutableStateOf(false) }
    ScreenContent(scrollKey = state.activeId) {
        ScreenTitle("Car")
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel {
                    SectionTitle(state.faultSources.firstOrNull()?.title?.let { "$it faults" } ?: "Vehicle faults")
                    Text(state.status.title, style = MaterialTheme.typography.titleMedium,
                        color = if (state.status.tone == StatusTone.Critical) LocalSemanticColors.current.critical
                            else MaterialTheme.colorScheme.onSurface)
                    Text(state.status.detail, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.faults.forEach { fault ->
                        HorizontalDivider()
                        Text(fault.code, style = MaterialTheme.typography.headlineSmall)
                        Text(fault.description, style = MaterialTheme.typography.bodyLarge)
                        Text(fault.category, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.incomplete) Text("Partial code list. More codes may be present.",
                        style = MaterialTheme.typography.bodyMedium, color = LocalSemanticColors.current.warning)
                    if (state.faultSources.any { it.categories.isNotEmpty() }) {
                        TextButton({ coverageOpen = !coverageOpen }) {
                            Text(if (coverageOpen) "Hide check coverage" else "Check coverage")
                        }
                        if (coverageOpen) state.faultSources.forEach { source ->
                            source.categories.forEach { category ->
                                Text("${category.name}: ${category.status}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsRow("Actions", if (state.actions.isEmpty()) "No gesture assigned" else "Jump to page",
                    state.canEditActions, { actionsOpen = true })
                SettingsRow("Your car", state.name, !state.profileError, { profilesOpen = true })
                SettingsRow("Adapter", state.connectionStatus.title,
                    state.adapterAvailable, { adapterOpen = true })
                if (state.setupNeeded && state.adapterSelected != null)
                    PrimaryAction("Use this car on gauge", onSendAdapter, enabled = state.canSendAdapter)
                else if (state.adapterSelected == null)
                    PrimaryAction("Choose adapter", { adapterOpen = true }, enabled = state.adapterAvailable)
                if (!state.adapterAvailable && !state.canCheck) TextButton(onSetup) { Text("Set up gauge") }
            }
        })
    }
    if (actionsOpen) PageActionsSheet(state, { actionsOpen = false }, onSaveAction)
    if (profilesOpen) ProfileDialog(state, { profilesOpen = false }, onSelectProfile, onCreateProfile, onDeleteProfile)
    if (adapterOpen) ModalBottomSheet(onDismissRequest = { adapterOpen = false }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Vehicle adapter")
            StatusCard(state.connectionStatus)
            Text(state.adapterSelected ?: "No adapter selected", style = MaterialTheme.typography.bodyLarge)
            if (state.adapterCandidates.isNotEmpty()) Text("Choose the adapter plugged into this car.")
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
