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

/** Configuration only. Vehicle Start intents never enter saved setup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPagesSheet(
    state: CarUiState,
    onSave: (PageAction, List<IdlePreset>) -> Unit,
    onDismiss: () -> Unit,
    onCoolant: () -> Unit = {},
    onGesture: () -> Unit = {},
    assignmentId: String = state.activeId
) {
    val original = state.actions.firstOrNull { it.type == PageActionType.VehicleActions }
    var action by remember(assignmentId, state.activeId, original) {
        mutableStateOf(original ?: PageAction(type = PageActionType.VehicleActions, count = state.actions.firstOrNull()?.count ?: 3, windowMs = state.actions.firstOrNull()?.windowMs ?: 5000, airUpSeconds = state.idlePresets.firstOrNull { it.id == IdlePresetId.AirUp }?.seconds ?: 300, winchSeconds = state.idlePresets.firstOrNull { it.id == IdlePresetId.Winch }?.seconds ?: 60, pages = emptyList()))
    }
    var presets by remember(assignmentId, state.activeId, state.idlePresets) { mutableStateOf(state.idlePresets) }
    var editing by remember(assignmentId, state.activeId) { mutableStateOf<ActionPageId?>(null) }
    var adding by remember(assignmentId, state.activeId) { mutableStateOf(false) }
    var removed by remember(assignmentId, state.activeId) { mutableStateOf<Pair<Int, ActionPageId>?>(null) }
    val locked = state.vehicleCommands.values.any { it.busy }
    val editable = state.canEditActions && !locked
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenTitle(editing?.label ?: if (adding) "Add action" else "Action pages")
            if (editing != null) {
                val selected = requireNotNull(editing)
                val idleId = if (selected == ActionPageId.AirUp) IdlePresetId.AirUp else IdlePresetId.Winch
                val saved = presets.firstOrNull { it.id == idleId } ?: IdlePreset(idleId)
                val seconds = if (selected == ActionPageId.AirUp) action.airUpSeconds else action.winchSeconds
                var duration by remember(assignmentId, selected) { mutableStateOf(airUpDuration(seconds)) }
                val pressure = remember(assignmentId, selected) { AirUpTimerDraft(saved.airUp) }
                val resolved = if (selected == ActionPageId.AirUp && pressure.enabled) pressure.timer?.let { runCatching { it.sessionSeconds }.getOrNull() } else airUpSeconds(duration)?.takeIf { it in 10..600 }
                if (selected != ActionPageId.AbsTcs) {
                    Text("Use this car’s fixed raised-idle setting.")
                    if (selected == ActionPageId.AirUp) AirUpTimerEditor(pressure, editable)
                    if (selected != ActionPageId.AirUp || !pressure.enabled) OutlinedTextField(duration, { duration = it }, label = { Text("Duration") }, supportingText = { Text("Minutes:seconds · 0:10 to 10:00") }, isError = resolved == null, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                    IdleCoolantNotice(state.idleCoolantLimitC, state.measurementSystem)
                    TextButton(onCoolant) { Text(if (state.idleCoolantLimitC == null) "Set coolant limit" else "Edit coolant alert") }
                    Text("Without coolant readings, start on the gauge for up to one minute.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Disable ABS and traction control together. Restart while parked to restore them on the tested Jeep.")
                }
                PrimaryAction("Done", {
                    if (selected != ActionPageId.AbsTcs) {
                        val timer = if (selected == ActionPageId.AirUp && pressure.enabled) pressure.timer else null
                        val next = IdlePreset(idleId, seconds = requireNotNull(resolved), airUp = timer)
                        presets = presets.filterNot { it.id == idleId } + next
                        action = if (selected == ActionPageId.AirUp) action.copy(airUpSeconds = next.seconds) else action.copy(winchSeconds = next.seconds)
                    }
                    editing = null
                }, enabled = editable && (selected == ActionPageId.AbsTcs || resolved != null))
                TextButton({ editing = null }) { Text("Back") }
            } else if (adding) {
                val available = ActionPageId.entries.filter { it in action.pages || state.vehicleCommands[it.kind]?.qualified == true }
                if (available.none { it !in action.pages }) Text("No additional actions available for this car yet.")
                available.forEach { id ->
                    val added = id in action.pages
                    val compatible = state.vehicleCommands[id.kind]?.qualified == true
                    SettingsRow(
                        id.label,
                        if (added) {
                            "Already added"
                        } else if (!compatible) {
                            "Not available for this car yet"
                        } else if (id == ActionPageId.AbsTcs) {
                            "ABS and traction control"
                        } else {
                            "Temporary raised idle"
                        },
                        editable && !added && compatible,
                        {
                            action = action.copy(pages = action.pages + id)
                            removed = null
                            adding = false
                            editing = id
                        }
                    )
                }
                TextButton({ adding = false }) { Text("Back") }
            } else {
                Text("Swipe up on your gauge to open these actions.")
                TextButton(onGesture, enabled = editable) { Text("Gesture · ${action.count} swipes up") }
                action.pages.forEachIndexed { index, id ->
                    var menu by remember(id) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f)) {
                            SettingsRow(id.label, if (id == ActionPageId.AbsTcs) "Restart to restore" else airUpDuration(if (id == ActionPageId.AirUp) action.airUpSeconds else action.winchSeconds), editable, { editing = id })
                        }
                        Box {
                            TextButton({ menu = true }, enabled = editable) { Text("More") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = {
                                    action = action.copy(pages = action.pages.toMutableList().also { it.add(index - 1, it.removeAt(index)) })
                                    menu = false
                                })
                                DropdownMenuItem(text = { Text("Move down") }, enabled = index < action.pages.lastIndex, onClick = {
                                    action = action.copy(pages = action.pages.toMutableList().also { it.add(index + 1, it.removeAt(index)) })
                                    menu = false
                                })
                                DropdownMenuItem(text = { Text("Remove") }, onClick = {
                                    removed = index to id
                                    action = action.copy(pages = action.pages - id)
                                    menu = false
                                })
                            }
                        }
                    }
                }
                if (action.pages.isEmpty()) Text("No action pages. Add one when this car supports it.")
                removed?.let { (index, id) ->
                    Row {
                        Text("${id.label} removed", Modifier.weight(1f))
                        TextButton({
                            action = action.copy(pages = action.pages.toMutableList().also { it.add(index.coerceAtMost(it.size), id) })
                            removed = null
                        }, enabled = editable) { Text("Undo") }
                    }
                }
                OutlinedButton({ adding = true }, enabled = editable && action.pages.size < 3) { Text("Add action") }
                if (locked) Text("Finish the active action before changing its pages.")
                if (original == null && state.actions.any { it.type == PageActionType.JumpPage }) Text("Saving action pages replaces this gauge’s page-jump gesture. Other gauges keep their choices.")
                if (state.idleCoolantLimitC == null && action.pages.any { it != ActionPageId.AbsTcs }) IdleCoolantNotice(null, state.measurementSystem)
                if (!state.actionsSupported) Text("Save on this phone now. Update the gauge before sending action pages.")
                PrimaryAction("Save on phone", {
                    onSave(action, presets)
                    onDismiss()
                }, enabled = editable && state.gaugeKnown)
                Text("Send setup to apply. Saved to gauge is shown only after verified readback.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
