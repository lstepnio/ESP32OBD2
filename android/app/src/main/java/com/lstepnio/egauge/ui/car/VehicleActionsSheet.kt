package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.CarUiState

/** App and gauge menu submit the same typed intent; no action-specific transport. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleActionsSheet(
    state: CarUiState,
    onSave: (List<IdlePreset>) -> Unit,
    onCommand: (VehicleCommandKind, Boolean, IdlePreset?) -> Unit,
    onDismiss: () -> Unit
) {
    var presetsOpen by remember(state.activeId) { mutableStateOf(false) }
    var confirm by remember(state.activeId) { mutableStateOf<Pair<VehicleCommandKind, IdlePreset?>?>(null) }
    val idle = state.vehicleCommands[VehicleCommandKind.Idle] ?: VehicleCommandStatus(VehicleCommandKind.Idle)
    val abs = state.vehicleCommands[VehicleCommandKind.AbsTcs] ?: VehicleCommandStatus(VehicleCommandKind.AbsTcs)
    if (presetsOpen) {
        IdleActionsSheet(state.activeId, state.idlePresets, state.canEditActions && !idle.busy, onSave, { presetsOpen = false }, coolantLimitC = state.idleCoolantLimitC, system = state.measurementSystem)
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Vehicle actions")
                Text("Air up & winch", style = MaterialTheme.typography.titleMedium)
                Text(idle.message)
                if (idle.canStop) {
                    PrimaryAction("Stop raised idle", { onCommand(VehicleCommandKind.Idle, true, null) }, enabled = state.commandsFresh)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdlePresetId.entries.forEach { id ->
                            val preset = state.idlePresets.firstOrNull { it.id == id } ?: IdlePreset(id)
                            OutlinedButton({ confirm = VehicleCommandKind.Idle to preset }, enabled = state.commandsFresh && idle.canStart && state.runningIdleCoolantLimitC != null && preset.rpm == null) { Text(id.label) }
                        }
                    }
                }
                if (state.runningIdleCoolantLimitC != null) {
                    IdleCoolantNotice(state.runningIdleCoolantLimitC, state.measurementSystem)
                } else if (state.idleCoolantLimitC != null) {
                    Text("Send the coolant alert to the gauge before starting raised idle.")
                } else {
                    IdleCoolantNotice(null, state.measurementSystem)
                }
                TextButton({ presetsOpen = true }, enabled = state.canEditActions && !idle.busy) { Text("Edit idle presets") }
                if (idle.qualified) Text("Uses this car's fixed raised-idle setting, approximately 1,700 RPM.", style = MaterialTheme.typography.bodySmall)
                if (state.idlePresets.any { it.rpm != null }) Text("Numeric targets remain saved drafts. Clear the target in a preset to use the verified fixed setting.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("ABS & traction control", style = MaterialTheme.typography.titleMedium)
                Text(abs.message)
                if (abs.canStop) {
                    PrimaryAction("End ABS / traction control command", { onCommand(VehicleCommandKind.AbsTcs, true, null) }, enabled = state.commandsFresh)
                } else {
                    OutlinedButton({ confirm = VehicleCommandKind.AbsTcs to null }, enabled = state.commandsFresh && abs.canStart) { Text("Switch off ABS & traction control") }
                }
                if (abs.qualified) Text("Remains off until the next ignition cycle. A green button does not confirm restoration.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    confirm?.let { (kind, preset) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (kind == VehicleCommandKind.Idle) "Start ${preset?.id?.label}?" else "Switch off ABS & traction control?") },
            text = { Text(if (kind == VehicleCommandKind.Idle) "Use this car's fixed raised-idle setting for ${preset!!.seconds} seconds while parked." else "Use only while parked for off-road setup. Restart the vehicle to restore normal operation.") },
            confirmButton = {
                TextButton({
                    confirm = null
                    onCommand(kind, false, preset)
                }, enabled = state.commandsFresh && (if (kind == VehicleCommandKind.Idle) idle.canStart && state.runningIdleCoolantLimitC != null else abs.canStart)) { Text("Confirm") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } }
        )
    }
}
