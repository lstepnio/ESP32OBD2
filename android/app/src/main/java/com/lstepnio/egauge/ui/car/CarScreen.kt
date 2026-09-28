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

@Composable
fun CarScreen(state: CarUiState, onCheck: () -> Unit, onSetup: () -> Unit, onDetails: () -> Unit,
              onSelectProfile: (String) -> Unit, onCreateProfile: (String) -> Unit) {
    var profilesOpen by rememberSaveable { mutableStateOf(false) }
    ScreenContent {
        ScreenTitle(state.name)
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard(state.status)
                state.faults.forEach { fault ->
                    Panel {
                        Text(fault.code, style = MaterialTheme.typography.headlineSmall)
                        Text(fault.description, style = MaterialTheme.typography.bodyLarge)
                        Text(fault.category, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.incomplete) Text("Only the first code in each category is available. More codes may be present.",
                    style = MaterialTheme.typography.bodyLarge, color = LocalSemanticColors.current.warning)
                if (state.faults.isNotEmpty()) Text("A code points to a problem area. It does not identify a part to replace.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsRow("Vehicle profile", state.name, !state.profileError, { profilesOpen = true })
                EmptyState("Vehicle adapter", "Adapter setup is not available in this app yet.")
                PrimaryAction(if (state.canCheck) "Check car" else "Set up gauge",
                    if (state.canCheck) onCheck else onSetup)
                TextButton(onDetails, Modifier.fillMaxWidth()) { Text("Details") }
            }
        })
    }
    if (profilesOpen) ProfileDialog(state, { profilesOpen = false }, onSelectProfile, onCreateProfile)
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
    onSelect: (String) -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val duplicate = state.profiles.any { it.name.equals(name.trim(), true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your cars") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.profiles.forEach { profile ->
                TextButton({ onSelect(profile.id); onDismiss() }, Modifier.fillMaxWidth()) {
                    Text(profile.name)
                    if (profile.id == state.activeId) EGaugeIcon(GaugeIcon.Check)
                }
            }
            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Car name") }, singleLine = true,
                isError = duplicate, supportingText = {
                    if (duplicate) Text("This name is already used. Choose a different name.")
                    else if (state.profiles.size >= 8) Text("All eight profile spaces are in use.")
                })
        }
    }, confirmButton = {
        Button({ onCreate(name.trim()); onDismiss() }, enabled = name.isNotBlank() && !duplicate && state.profiles.size < 8) { Text("Add car") }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
