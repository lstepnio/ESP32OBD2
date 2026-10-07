package com.lstepnio.egauge.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.OwnerAccess
import com.lstepnio.egauge.PairingWindowState
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.SetupUiState

@Composable
fun SetupScreen(state: SetupUiState, onFind: () -> Unit, onPair: () -> Unit,
    onRefreshPairing: () -> Unit, onBluetoothSettings: () -> Unit,
    onChoose: (String) -> Unit, onCustomize: () -> Unit, onBack: () -> Unit) {
    val step = when { state.owner == OwnerAccess.AUTHENTICATED -> 2; state.found -> 1; else -> 0 }
    var candidate by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenContent(scrollKey = step) {
        ScreenTitle(when (step) { 0 -> "Set up gauge"; 1 -> "Pair your gauge"; else -> "Connect your car" }, onBack)
        Text("${step + 1} of 3", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    EGaugeIcon(if (step == 1) GaugeIcon.Shield else if (step == 2) GaugeIcon.Car else GaugeIcon.Gauge,
                        modifier = Modifier.size(52.dp))
                }
            }
        }
        when (step) {
            0 -> {
                state.candidates.forEach { item ->
                    ReadingTile("${item.name} · ${item.shortId}", "Match this ID to the gauge screen", candidate == item.id,
                        { candidate = item.id }, enabled = !state.busy)
                }
                StatusCard(state.status)
                PrimaryAction(if (state.busy) "Finding gauge" else if (state.candidates.isEmpty()) "Find gauge" else "Connect to gauge",
                    { if (state.candidates.isEmpty()) onFind() else candidate?.let(onChoose) },
                    enabled = !state.busy && (state.candidates.isEmpty() || candidate != null))
                TextButton(onCustomize, Modifier.fillMaxWidth()) { Text("Explore the preview") }
            }
            1 -> {
                Text("Open pairing on your gauge", style = MaterialTheme.typography.headlineSmall)
                val pairingWindow = state.pairingWindow
                val pairingInstructions = if (state.androidBonded)
                    "Android paired this phone. The app is checking that your gauge saved it as owner. Keep the gauge powered and nearby."
                else when (pairingWindow?.state) {
                    PairingWindowState.READY -> "Your gauge is ready. Tap Pair gauge, then enter the six-digit code shown on the gauge in Android's pairing prompt. Pairing stays open for about ${pairingWindow.secondsRemaining} seconds."
                    PairingWindowState.CODE_DISPLAYED -> "A pairing request is already open. Enter the six-digit code shown on your gauge in Android's prompt. If you dismissed the prompt, check pairing status."
                    PairingWindowState.CLOSED -> "Pairing is closed on the gauge. Hold the display briefly until it says OPEN APP TO PAIR GAUGE, then tap Pair gauge."
                    PairingWindowState.OWNER_PRESENT -> "This gauge still has an owner. To move it to this phone, hold the display for 12 seconds, release, then tap once when it says OWNER RESET? TAP TO CONFIRM. Display settings stay saved."
                    null -> "Tap Pair gauge to open Android's pairing prompt, then enter the code shown on the gauge. If it says HOLD TO OPEN PAIRING, hold the screen briefly first."
                }
                Text(pairingInstructions,
                    style = MaterialTheme.typography.bodyLarge)
                StatusCard(state.status)
                if (state.status.title == "This phone's saved bond does not own the gauge")
                    TextButton(onBluetoothSettings, Modifier.fillMaxWidth()) { Text("Open Bluetooth settings") }
                val status = state.pairingWindow?.state
                val action = if (!state.androidBonded && status in setOf(PairingWindowState.CLOSED, PairingWindowState.OWNER_PRESENT,
                        PairingWindowState.CODE_DISPLAYED))
                    onRefreshPairing else onPair
                val actionLabel = if (state.androidBonded)
                    if (state.busy) "Checking..." else "Check gauge access"
                else when (status) {
                    PairingWindowState.CLOSED, PairingWindowState.OWNER_PRESENT,
                    PairingWindowState.CODE_DISPLAYED -> "Check pairing status"
                    else -> if (state.busy) "Pairing..." else "Pair gauge"
                }
                PrimaryAction(actionLabel, action, enabled = !state.busy)
            }
            else -> {
                StatusCard(StatusUi("Your phone is paired", "Your gauge confirmed access.", StatusTone.Success))
                EmptyState("Adapter setup is not available yet", "You can choose readings and send your display layout. Car readings stay unavailable until an adapter is connected and verified.")
                PrimaryAction("Choose readings", onCustomize)
            }
        }
    }
}
