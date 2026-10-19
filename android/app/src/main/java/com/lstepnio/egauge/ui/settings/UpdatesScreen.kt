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
fun UpdatesScreen(state: UpdatesUiState, operation: OperationUi,
    onBack: () -> Unit, onCheck: () -> Unit, onInstall: () -> Unit,
    onReadInstalled: () -> Unit, onChoosePackage: () -> Unit) {
    ScreenContent {
        ScreenTitle("Updates", onBack)
        if (state.installedVersion != "Not checked") Text("Installed: ${state.installedVersion}", style = MaterialTheme.typography.bodyMedium)
        state.availableVersion?.let { Text("Available: $it", style = MaterialTheme.typography.bodyLarge) }
        if (state.installedVersion == "Not checked" && !state.ready &&
            state.availableVersion == null && !state.held && !state.recoveryRequired)
            EmptyState("Waiting for your gauge", "Connect your paired gauge to check for a signed update.")
        else
            StatusCard(state.status)
        if (operation.busy && operation.update) Panel {
            ProgressStepper(listOf("Downloading", "Sending to gauge", "Restarting", "Done"),
                operation.step, operation.progress, finished = false)
        }
        if (!operation.busy && (state.ready || state.availableVersion != null))
            Text("Keep your gauge powered and the app open until it confirms the update.",
                style = MaterialTheme.typography.bodyLarge)
        if (!(operation.busy && operation.update)) PrimaryAction(when {
            state.recoveryRequired -> "Check installed version"
            state.held -> "Check your gauge"
            state.feedUnavailable -> "Choose signed package"
            state.ready -> "Install update"
            state.availableVersion != null -> "Download and install"
            else -> "Check for updates"
        }, {
            when {
                state.recoveryRequired -> onReadInstalled()
                state.held -> onCheck()
                state.feedUnavailable -> onChoosePackage()
                state.ready || state.availableVersion != null -> onInstall()
                else -> onCheck()
            }
        }, enabled = !state.busy && when {
            state.recoveryRequired || state.held -> state.canCheck
            state.feedUnavailable -> true
            state.ready -> state.canInstall
            else -> state.canCheck
        })
        if (state.feedUnavailable && !state.recoveryRequired && !state.held) TextButton(onCheck, enabled = state.canCheck) { Text("Try online check") }
    }
}
