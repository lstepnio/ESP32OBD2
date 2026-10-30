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
    val burnout = state.vehicleCommands[VehicleCommandKind.Burnout] ?: VehicleCommandStatus(VehicleCommandKind.Burnout)
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
                    PrimaryAction("Stop raised idle", { onCommand(VehicleCommandKind.Idle, true, null) }, enabled = VehicleCommandKind.Idle in state.freshCommandKinds)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdlePresetId.entries.forEach { id ->
                            val preset = state.idlePresets.firstOrNull { it.id == id } ?: IdlePreset(id)
                            OutlinedButton({ confirm = VehicleCommandKind.Idle to preset }, enabled = VehicleCommandKind.Idle in state.freshCommandKinds && idle.canStart && state.runningIdleCoolantLimitC != null && preset.rpm == null) { Text(id.label) }
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
                if (!idle.busy) {
                    state.idlePresets.firstOrNull { it.id == IdlePresetId.AirUp }?.let { preset ->
                        preset.airUp?.let { timer ->
                            Text("Four tires · ${timer.currentPsi} to ${timer.targetPsi} PSI · ${airUpDuration(preset.seconds)} ${if (timer.overrideSeconds == null) "estimate" else "override"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton({ presetsOpen = true }, enabled = state.canEditActions && !idle.busy) { Text("Edit idle presets") }
                if (idle.qualified) Text("Uses this car's fixed raised-idle setting, approximately 1,700 RPM.", style = MaterialTheme.typography.bodySmall)
                if (state.idlePresets.any { it.rpm != null }) Text("Numeric targets remain saved drafts. Clear the target in a preset to use the verified fixed setting.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("ABS + TCS", style = MaterialTheme.typography.titleMedium)
                Text(abs.message)
                if (!abs.busy && !abs.gaugeStartRequired) {
                    OutlinedButton({ confirm = VehicleCommandKind.AbsTcs to null }, enabled = VehicleCommandKind.AbsTcs in state.freshCommandKinds && abs.canStart) { Text("Disable ABS + TCS") }
                }
                if (burnout.busy) {
                    HorizontalDivider()
                    Text("Burnout recovery", style = MaterialTheme.typography.titleMedium)
                    Text(burnout.message)
                    if (burnout.canStop) {
                        PrimaryAction("Stop Burnout Mode", { onCommand(VehicleCommandKind.Burnout, true, null) }, enabled = VehicleCommandKind.Burnout in state.freshCommandKinds)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    confirm?.let { (kind, preset) ->
        var sessionTime by remember(confirm) { mutableStateOf(preset?.seconds?.let(::airUpDuration).orEmpty()) }
        val sessionPreset = if (preset?.airUp != null) {
            runCatching {
                val seconds = requireNotNull(airUpSeconds(sessionTime))
                require(seconds in 10..600)
                if (seconds == preset.seconds) preset else preset.copy(seconds = seconds, airUp = preset.airUp.copy(overrideSeconds = seconds))
            }.getOrNull()
        } else {
            preset
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (kind == VehicleCommandKind.Idle) "Start ${preset?.id?.label}?" else "Switch off ABS & traction control?") },
            text = {
                if (preset?.airUp != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("All four tires · ${preset.airUp.currentPsi} to ${preset.airUp.targetPsi} PSI. This is a time estimate; check tire pressure afterward.")
                        OutlinedTextField(sessionTime, { sessionTime = it }, label = { Text("Time for this session") }, supportingText = { Text("Minutes:seconds, 0:10 to 10:00. Your saved preset stays unchanged.") }, isError = sessionPreset == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text(if (kind == VehicleCommandKind.Idle) "Use this car's fixed raised-idle setting for ${preset!!.seconds} seconds while parked." else "Use only while parked for off-road setup. Use Restore on the gauge to return control, then reset the ignition and check the dash.")
                }
            },
            confirmButton = {
                TextButton({
                    confirm = null
                    onCommand(kind, false, sessionPreset)
                }, enabled = kind in state.freshCommandKinds && (if (kind == VehicleCommandKind.Idle) idle.canStart && state.runningIdleCoolantLimitC != null && sessionPreset != null else abs.canStart)) { Text("Confirm") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } }
        )
    }
}
