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
import com.lstepnio.egauge.ui.state.alertContextReading

/** Configuration only. Vehicle Start intents never enter saved setup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPagesSheet(
    state: CarUiState,
    onSave: (PageAction, List<IdlePreset>) -> Unit,
    onDismiss: () -> Unit,
    onCoolant: () -> Unit = {},
    onGesture: () -> Unit = {},
    assignmentId: String = state.activeId,
    setupPending: Boolean = false
) {
    val original = state.actions.firstOrNull { it.type == PageActionType.VehicleActions }
    var action by remember(assignmentId, state.activeId, original) {
        mutableStateOf(original ?: PageAction(type = PageActionType.VehicleActions, count = state.actions.firstOrNull()?.count ?: 3, windowMs = state.actions.firstOrNull()?.windowMs ?: 5000, airUpSeconds = state.idlePresets.firstOrNull { it.id == IdlePresetId.AirUp }?.seconds ?: 300, winchSeconds = state.idlePresets.firstOrNull { it.id == IdlePresetId.Winch }?.seconds ?: 60, pages = emptyList()))
    }
    var presets by remember(assignmentId, state.activeId, state.idlePresets) { mutableStateOf(state.idlePresets) }
    var editing by remember(assignmentId, state.activeId) { mutableStateOf<ActionPageId?>(null) }
    var adding by remember(assignmentId, state.activeId) { mutableStateOf(false) }
    var reorder by remember(assignmentId, state.activeId) { mutableStateOf(false) }
    var menu by remember(assignmentId, state.activeId) { mutableStateOf(false) }
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
                var rpmText by remember(assignmentId, selected) { mutableStateOf(saved.rpm?.toString().orEmpty()) }
                var targetOpen by remember(assignmentId, selected) { mutableStateOf(false) }
                var duration by remember(assignmentId, selected) { mutableStateOf(airUpDuration(seconds)) }
                val pressure = remember(assignmentId, selected) { AirUpTimerDraft(saved.airUp) }
                var durationOpen by remember(assignmentId, selected) { mutableStateOf(false) }
                var pressureOpen by remember(assignmentId, selected) { mutableStateOf(false) }
                val resolved = if (selected == ActionPageId.AirUp && pressure.enabled) pressure.timer?.let { runCatching { it.sessionSeconds }.getOrNull() } else airUpSeconds(duration)?.takeIf { it in 10..600 }
                if (selected.kind == VehicleCommandKind.Idle) {
                    if (!targetOpen) {
                        SettingsRow(IdleTargetDraft.label(idleId), saved.rpm?.let { "$it RPM · Saved on phone" } ?: "Car's fixed setting", editable, { targetOpen = true })
                    } else {
                        IdleTargetEditor(idleId, rpmText, { rpmText = it }, editable)
                    }
                    if (!durationOpen) {
                        SettingsRow("Duration", airUpDuration(resolved ?: seconds), editable, {
                            durationOpen = true
                        })
                    } else {
                        OutlinedTextField(duration, {
                            duration = it
                            pressure.enabled = false
                        }, label = { Text("Duration") }, supportingText = { Text("Minutes:seconds · 0:10 to 10:00") }, isError = resolved == null, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (state.idleCoolantLimitC == null) {
                        SettingsRow("Add a coolant limit", "Idle cannot start until a high-temperature alert is set.", editable, onCoolant)
                    } else {
                        SettingsRow("Coolant limit", alertContextReading(state.idleCoolantLimitC, "°C", state.measurementSystem), editable, onCoolant)
                    }
                    if (selected == ActionPageId.AirUp) {
                        if (!pressureOpen) {
                            SettingsRow("Estimate from tire pressure", if (pressure.enabled) "Estimated time · ${airUpDuration(resolved ?: seconds)}" else "Optional", editable, { pressureOpen = true })
                        } else {
                            AirUpTimerEditor(pressure, editable)
                        }
                    }
                } else {
                    Text(if (selected == ActionPageId.Burnout) "Hold on gauge. Release to stop." else "Disable ABS and traction control together. Restart while parked to restore them on the tested Jeep.")
                }
                PrimaryAction("Done", {
                    if (selected.kind == VehicleCommandKind.Idle) {
                        val timer = if (selected == ActionPageId.AirUp && pressure.enabled) pressure.timer else null
                        val next = saved.copy(rpm = IdleTargetDraft.parse(rpmText), seconds = requireNotNull(resolved), airUp = timer)
                        presets = presets.filterNot { it.id == idleId } + next
                        action = if (selected == ActionPageId.AirUp) action.copy(airUpSeconds = next.seconds) else action.copy(winchSeconds = next.seconds)
                    }
                    editing = null
                }, enabled = editable && (selected.kind != VehicleCommandKind.Idle || (resolved != null && IdleTargetDraft.valid(rpmText))))
                TextButton({
                    removed = action.pages.indexOf(selected) to selected
                    action = action.copy(pages = action.pages - selected)
                    editing = null
                }, enabled = editable) { Text("Remove action") }
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
                        } else if (id == ActionPageId.Burnout) {
                            "Hold on gauge · release not qualified"
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
                Box {
                    TextButton({ menu = true }, enabled = editable) { Text("Action page options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Reorder actions") }, enabled = action.pages.size > 1, onClick = {
                            reorder = !reorder
                            menu = false
                        })
                        DropdownMenuItem(text = { Text("Entry gesture") }, onClick = {
                            menu = false
                            onGesture()
                        })
                    }
                }
                action.pages.forEachIndexed { index, id ->
                    SettingsRow(
                        id.label,
                        if (id == ActionPageId.Burnout) {
                            "Hold on gauge · release not qualified"
                        } else if (id == ActionPageId.AbsTcs) {
                            if (state.vehicleCommands[id.kind]?.qualified == true) "Restart to restore" else "Not supported yet"
                        } else {
                            val duration = airUpDuration(if (id == ActionPageId.AirUp) action.airUpSeconds else action.winchSeconds)
                            val presetId = if (id == ActionPageId.AirUp) IdlePresetId.AirUp else IdlePresetId.Winch
                            presets.firstOrNull { it.id == presetId }?.rpm?.let { "$duration · $it RPM · Saved on phone" } ?: duration
                        },
                        editable && !reorder,
                        { editing = id }
                    )
                    if (reorder) {
                        Row {
                            TextButton({
                                action = action.copy(pages = action.pages.toMutableList().also { it.add(index - 1, it.removeAt(index)) })
                            }, enabled = editable && index > 0) { Text("Move ${id.label} up") }
                            TextButton({
                                action = action.copy(pages = action.pages.toMutableList().also { it.add(index + 1, it.removeAt(index)) })
                            }, enabled = editable && index < action.pages.lastIndex) { Text("Move ${id.label} down") }
                        }
                    }
                }
                if (reorder) TextButton({ reorder = false }) { Text("Done reordering") }
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
                OutlinedButton({ adding = true }, enabled = editable && action.pages.size < 4) { Text("Add action") }
                if (locked) Text("Finish the active action before changing its pages.")
                if (original == null && state.actions.any { it.type == PageActionType.JumpPage }) Text("Saving action pages replaces this gauge’s page-jump gesture. Other gauges keep their choices.")
                if (!state.actionsSupported) Text("Save on this phone now. Update the gauge before sending action pages.")
                PrimaryAction("Save on phone", {
                    onSave(action, presets)
                    onDismiss()
                }, enabled = editable && state.gaugeKnown)
                if (setupPending) StatusCard(StatusUi("Saved on phone", "Send setup to apply to the gauge."))
            }
        }
    }
}
