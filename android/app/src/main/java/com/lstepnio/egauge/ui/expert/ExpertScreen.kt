package com.lstepnio.egauge.ui.expert

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.ExpertUiState

data class ExpertActions(
    val query: (String) -> Unit, val filter: (String) -> Unit, val selectReading: (String) -> Unit,
    val editLab: (String) -> Unit, val editRequest: (String) -> Unit, val selectSource: (String) -> Unit,
    val secondAdapter: (Boolean) -> Unit, val readHardware: () -> Unit,
    val readSaved: () -> Unit, val readConfig: () -> Unit, val readDocument: () -> Unit,
    val readDiagnostics: () -> Unit, val readFirmware: () -> Unit, val selectBuiltIn: () -> Unit,
    val developmentUpdates: () -> Unit, val adoptGaugeDraft: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpertScreen(state: ExpertUiState, tool: String, onTool: (String) -> Unit, onBack: () -> Unit,
    canAdoptGaugeDraft: Boolean, actions: ExpertActions) {
    var discovery by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(state.selectedReadingId) }
    ScreenContent(scrollKey = tool) {
        ScreenTitle(tool.ifBlank { "Expert" }, if (tool.isNotBlank()) onBack else null)
        when (tool) {
            "" -> {
                PrimaryAction("Explore readings", { onTool("PID explorer") })
                listOf("Decoder lab", "Custom PIDs", "Second adapter", "Diagnostics").forEach { name ->
                    SettingsRow(name, onClick = { onTool(name) })
                }
                SettingsRow("Development updates", onClick = actions.developmentUpdates)
            }
            "PID explorer" -> {
                OutlinedTextField(state.query, actions.query, label = { Text("Search name, PID or source") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "ECM", "TCM").forEach { source ->
                        FilterChip(state.sourceFilter == source, { actions.filter(source) }, label = { Text(source) })
                    }
                }
                Text("Example definitions · Vehicle support not checked", color = LocalSemanticColors.current.warning)
                state.readings.forEach { reading ->
                    ReadingTile(reading.name, reading.unit, selected == reading.id, { selected = reading.id })
                    DetailContent(reading.details.filter { it.label in setOf("Service / request", "ECU / source") })
                }
                if (state.readings.isEmpty()) EmptyState("No matching definitions", "Try another name, PID or source.")
                PrimaryAction("Use reading on this page", { actions.selectReading(selected) }, enabled = !state.busy)
                OutlinedButton({ discovery = !discovery }) { Text(if (discovery) "Close discovery preview" else "Preview discovery states") }
                if (discovery) EmptyState("Example: no vehicle response", "Discovery has not run. No adapter or car has been queried.")
            }
            "Decoder lab" -> {
                Text("Example decoder · Nothing is sent to the car.", color = LocalSemanticColors.current.warning)
                Text("Selected reading: ${state.selectedReadingId}")
                OutlinedTextField(state.labInput, actions.editLab, label = { Text("Mode 01 response bytes") }, modifier = Modifier.fillMaxWidth())
                StatusCard(state.decoded)
                PrimaryAction("Choose a reading", { onTool("PID explorer") })
            }
            "Custom PIDs" -> {
                EmptyState("Offline request check", "Mode 01, 09 and 22 read syntax only. Vehicle-specific decoding and sending custom definitions are not available yet.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ECM", "TCM").forEach { source -> FilterChip(state.customSource == source,
                        { actions.selectSource(source) }, label = { Text(source) }) }
                }
                OutlinedTextField(state.customInput, actions.editRequest, label = { Text("Read request bytes") }, modifier = Modifier.fillMaxWidth())
                StatusCard(state.customResult)
                PrimaryAction("Open decoder lab", { onTool("Decoder lab") })
            }
            "Second adapter" -> {
                StatusCard(StatusUi("Second-adapter connection is not available yet", "This preference keeps engine and transmission sources separate on this phone. It does not connect another adapter.", StatusTone.Disabled))
                Panel {
                    Row(Modifier.fillMaxWidth().toggleable(state.secondAdapter, role = Role.Switch, onValueChange = actions.secondAdapter),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Use a second adapter", modifier = Modifier.weight(1f))
                        Switch(state.secondAdapter, null)
                    }
                    Text("Engine (ECM) and transmission (TCM) bindings are preserved. Simultaneous operation is unverified.")
                }
                PrimaryAction("Explore readings", { onTool("PID explorer") })
            }
            "Diagnostics" -> {
                PrimaryAction("Check running setup", actions.readConfig, enabled = state.canRead)
                OutlinedButton(actions.readSaved, enabled = state.canRead) { Text("Read saved selection") }
                OutlinedButton(actions.readDocument, enabled = state.canRead) { Text("Verify saved configuration") }
                OutlinedButton(actions.readHardware, enabled = state.canReadHardware) { Text("Read hardware capacity") }
                OutlinedButton(actions.readDiagnostics, enabled = state.canRead) { Text("Read fault snapshot") }
                OutlinedButton(actions.readFirmware, enabled = state.canRead) { Text("Read installed firmware") }
                OutlinedButton(actions.selectBuiltIn, enabled = state.canRead) { Text("Set selected built-in reading") }
                if (canAdoptGaugeDraft) OutlinedButton(actions.adoptGaugeDraft, enabled = !state.busy) {
                    Text("Use gauge settings")
                }
                SectionTitle("Technical data")
                DetailContent(state.details)
            }
        }
    }
}
