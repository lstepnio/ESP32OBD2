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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdleActionsSheet(vehicleId: String, saved: List<IdlePreset>, editable: Boolean, onSave: (List<IdlePreset>) -> Unit, onDismiss: () -> Unit, control: IdleControlState = IdleControlState(), onStart: ((IdlePreset) -> Unit)? = null, onStop: (() -> Unit)? = null, coolantLimitC: Double? = null, system: MeasurementSystem = MeasurementSystem.Metric) {
    var selected by remember(vehicleId) { mutableStateOf(IdlePresetId.AirUp) }
    var values by remember(vehicleId, saved) { mutableStateOf(saved) }
    val value = values.firstOrNull { it.id == selected } ?: IdlePreset(selected)
    var rpmText by remember(vehicleId, selected, value.rpm) { mutableStateOf(value.rpm?.toString().orEmpty()) }
    val airUpDraft = remember(vehicleId, selected) { AirUpTimerDraft(value.airUp) }
    val pressureValid = selected != IdlePresetId.AirUp || airUpDraft.valid
    fun editedPreset() = if (selected == IdlePresetId.AirUp) airUpDraft.applyTo(value) else value
    val rpm = IdleTargetDraft.parse(rpmText)
    val valid = IdleTargetDraft.valid(rpmText)
    fun saveValue(next: IdlePreset) {
        values = values.filterNot { it.id == next.id } + next
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Air up & winch")
            Text("Temporarily raise idle while using your equipment.")
            Text(control.message)
            IdleCoolantNotice(coolantLimitC, system)
            if (control.canStop) PrimaryAction("Stop raised idle", { onStop?.invoke() }, enabled = onStop != null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdlePresetId.entries.forEach { id ->
                    FilterChip(selected == id, {
                        if (valid && pressureValid) saveValue(editedPreset().copy(rpm = rpm))
                        selected = id
                    }, label = { Text(id.label) }, enabled = editable && !control.configurationLocked && pressureValid)
                }
            }
            IdleTargetEditor(selected, rpmText, { rpmText = it }, editable && !control.configurationLocked)
            if (selected == IdlePresetId.AirUp) AirUpTimerEditor(airUpDraft, editable && !control.configurationLocked)
            if (selected != IdlePresetId.AirUp || !airUpDraft.enabled) {
                Text("Duration")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 300, 600).forEach { seconds -> FilterChip(value.seconds == seconds, { saveValue(value.copy(seconds = seconds, airUp = null)) }, label = { Text("${seconds / 60} min") }, enabled = editable && !control.configurationLocked) }
                }
            }
            PrimaryAction("Save preset", {
                val next = values.filterNot { it.id == selected } + editedPreset().copy(rpm = rpm)
                onSave(next)
                onDismiss()
            }, enabled = editable && valid && pressureValid && !control.configurationLocked)
            PrimaryAction("Start ${selected.label}", { onStart?.invoke(editedPreset().copy(rpm = rpm)) }, enabled = onStart != null && control.canStart && valid && pressureValid && rpm == null && editable && coolantLimitC != null)
            if (saved.any { it.id == selected }) {
                TextButton({
                    onSave(saved.filterNot { it.id == selected })
                    onDismiss()
                }, enabled = editable && !control.configurationLocked) { Text("Remove preset") }
            }
        }
    }
}
