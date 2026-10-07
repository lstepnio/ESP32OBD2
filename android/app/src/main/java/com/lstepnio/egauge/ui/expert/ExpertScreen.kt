package com.lstepnio.egauge.ui.expert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.BuildConfig
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.ExpertUiState

data class ExpertActions(
    val refreshSettings: () -> Unit,
    val readDiagnostics: () -> Unit,
    val readHardware: () -> Unit,
    val readFirmware: () -> Unit,
    val adoptGaugeSettings: () -> Unit,
    val createTcmTest: () -> Unit = {},
)

@Composable
fun ExpertScreen(state: ExpertUiState, actions: ExpertActions) {
    ScreenContent {
        ScreenTitle("Expert")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Gauge checks")
            PrimaryAction("Refresh gauge data", actions.refreshSettings, enabled = state.canRead)
            SettingsRow("Check vehicle faults", enabled = state.canRead, onClick = actions.readDiagnostics)
            SettingsRow("Read hardware capacity", enabled = state.canReadHardware, onClick = actions.readHardware)
            SettingsRow("Check installed firmware", enabled = state.canRead, onClick = actions.readFirmware)
            if (state.canAdoptGaugeSettings) SettingsRow("Use gauge settings", onClick = actions.adoptGaugeSettings)
            if (BuildConfig.DEBUG) SettingsRow("Set up transmission", onClick = actions.createTcmTest)
            SectionTitle("Device data")
            DetailContent(state.details)
        }
    }
}
