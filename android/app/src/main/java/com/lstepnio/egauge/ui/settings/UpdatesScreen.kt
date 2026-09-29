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
        Text("Installed version: ${state.installedVersion}", style = MaterialTheme.typography.bodyLarge)
        if (!development) {
            EmptyState("Updates are not available yet", "This gauge currently supports development updates only. They are available in Expert when advanced tools are on.")
            if (state.recoveryRequired || operation.update && operation.needsCheck) StatusCard(state.status)
            PrimaryAction("Check installed version", onReadInstalled, enabled = state.canCheck)
        } else {
            StatusCard(state.status)
            if (operation.busy && operation.update) Panel {
                ProgressStepper(listOf("Downloading", "Sending to gauge", "Restarting", "Done"),
                    operation.step, operation.progress, finished = false)
            }
            Text("Use a signed test release. Keep the app open and your gauge powered until it confirms the update.",
                style = MaterialTheme.typography.bodyLarge)
            PrimaryAction(when { state.recoveryRequired -> "Check gauge"; state.ready || state.availableVersion != null -> "Install"
                else -> "Check for updates" },
                { when { state.recoveryRequired -> onReadInstalled(); state.ready || state.availableVersion != null -> onInstall()
                    else -> onCheck() } },
                enabled = if (state.recoveryRequired) state.canCheck else if (state.ready) state.canInstall else state.canCheck)
            OutlinedButton(onChoosePackage, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Choose development package") }
            TextButton(onReadInstalled, enabled = state.canCheck) { Text("Check installed version") }
        }
    }
}
