package com.lstepnio.egauge.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.*

@Composable
fun UpdatesScreen(state: UpdatesUiState, operation: OperationUi, development: Boolean,
    onBack: () -> Unit, onCheck: () -> Unit, onInstall: () -> Unit,
    onReadInstalled: () -> Unit, onChoosePackage: () -> Unit) {
    ScreenContent {
        ScreenTitle(if (development) "Development updates" else "Updates", onBack)
        if (development) Text("Installed version: ${state.installedVersion}", style = MaterialTheme.typography.bodyLarge)
        if (!development && state.installedVersion == "Not checked" && !state.ready &&
            state.availableVersion == null && !state.held && !state.recoveryRequired)
            EmptyState("Waiting for your gauge", "Connect your paired gauge to check for a signed update.")
        else
            StatusCard(state.status)
        if (operation.busy && operation.update) Panel {
            ProgressStepper(listOf("Downloading", "Sending to gauge", "Restarting", "Done"),
                operation.step, operation.progress, finished = false)
        }
        if (state.ready || state.availableVersion != null)
            Text("Keep your gauge powered and the app open until it confirms the update.",
                style = MaterialTheme.typography.bodyLarge)
        PrimaryAction(when {
            state.recoveryRequired -> "Check installed version"
            state.held -> "Check your gauge"
            state.ready -> "Install update"
            state.availableVersion != null -> "Download and install"
            else -> "Check for updates"
        }, {
            when {
                state.recoveryRequired -> onReadInstalled()
                state.ready || state.availableVersion != null -> onInstall()
                else -> onCheck()
            }
        }, enabled = if (state.ready) state.canInstall else state.canCheck)
        if (development) {
            OutlinedButton(onChoosePackage, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Choose development package") }
            TextButton(onReadInstalled, enabled = state.canCheck) { Text("Check installed version") }
        }
    }
}
