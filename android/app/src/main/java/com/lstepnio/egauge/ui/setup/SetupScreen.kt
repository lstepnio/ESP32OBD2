package com.lstepnio.egauge.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.OwnerAccess
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.SetupUiState

@Composable
fun SetupScreen(state: SetupUiState, onFind: () -> Unit, onPair: () -> Unit,
    onChoose: (String) -> Unit, onCustomize: () -> Unit, onBack: () -> Unit, onDetails: () -> Unit) {
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
                state.candidates.forEachIndexed { index, item ->
                    ReadingTile("${item.name} · ${index + 1}", "Confirm its code next", candidate == item.id,
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
                Text("Long press the display, then enter the code shown on it when Android asks. Keep the gauge nearby until pairing finishes.",
                    style = MaterialTheme.typography.bodyLarge)
                StatusCard(state.status)
                PrimaryAction("Pair gauge", onPair, enabled = !state.busy)
            }
            else -> {
                StatusCard(StatusUi("Your phone is paired", "Your gauge confirmed access.", StatusTone.Success))
                EmptyState("Adapter setup is not available yet", "You can choose readings and send your display layout. Car readings stay unavailable until an adapter is connected and verified.")
                PrimaryAction("Choose readings", onCustomize)
            }
        }
        TextButton(onDetails, Modifier.fillMaxWidth()) { Text("Details") }
    }
}
