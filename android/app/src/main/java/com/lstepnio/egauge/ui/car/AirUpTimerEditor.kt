package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.AirUpTimer
import com.lstepnio.egauge.IdlePreset

internal class AirUpTimerDraft(initial: AirUpTimer?) {
    var enabled by mutableStateOf(initial != null)
    var current by mutableStateOf(initial?.currentPsi?.toString().orEmpty())
    var target by mutableStateOf(initial?.targetPsi?.toString().orEmpty())
    var from by mutableStateOf(initial?.referenceFromPsi?.toString().orEmpty())
    var to by mutableStateOf(initial?.referenceToPsi?.toString().orEmpty())
    var measured by mutableStateOf(initial?.referenceSeconds?.let(::airUpDuration).orEmpty())
    var override by mutableStateOf(initial?.overrideSeconds?.let(::airUpDuration).orEmpty())
    val timer get() = runCatching {
        require(override.isBlank() || airUpSeconds(override) != null)
        AirUpTimer(current.toInt(), target.toInt(), from.toInt(), to.toInt(), requireNotNull(airUpSeconds(measured)), if (override.isBlank()) null else airUpSeconds(override))
    }.getOrNull()
    val valid get() = !enabled || timer?.let { runCatching { it.sessionSeconds }.isSuccess } == true
    fun applyTo(value: IdlePreset): IdlePreset = if (enabled) {
        val timer = requireNotNull(timer)
        value.copy(seconds = timer.sessionSeconds, airUp = timer)
    } else {
        value.copy(airUp = null)
    }
}

internal fun airUpDuration(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
internal fun airUpSeconds(text: String): Int? {
    if (!Regex("[0-9]{1,3}:[0-5][0-9]").matches(text.trim())) return null
    val parts = text.trim().split(':')
    return parts[0].toInt() * 60 + parts[1].toInt()
}

@Composable
internal fun AirUpTimerEditor(draft: AirUpTimerDraft, editable: Boolean) {
    var calibrating by remember { mutableStateOf(draft.from.isBlank()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!draft.enabled, { draft.enabled = false }, label = { Text("Timer") }, enabled = editable)
        FilterChip(draft.enabled, { draft.enabled = true }, label = { Text("Pressure estimate") }, enabled = editable)
    }
    if (!draft.enabled) return
    Text("All four tires together", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(draft.current, { draft.current = it }, label = { Text("Starting PSI") }, isError = draft.current.isNotBlank() && (draft.current.toIntOrNull() ?: -1) !in 0..149, singleLine = true, enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        OutlinedTextField(draft.target, { draft.target = it }, label = { Text("Target PSI") }, isError = draft.target.isNotBlank() && (draft.target.toIntOrNull() ?: -1) !in 1..150, singleLine = true, enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
    }
    if (draft.current.toIntOrNull() != null && draft.target.toIntOrNull() != null && draft.target.toInt() <= draft.current.toInt()) Text("Target PSI must be above starting PSI.")
    TextButton({ calibrating = !calibrating }, enabled = editable) { Text(if (calibrating) "Hide calibration" else "Calibrate four-tire setup") }
    if (calibrating) {
        Text("Enter one measured fill using the same four tires, compressor and hoses.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(draft.from, { draft.from = it }, label = { Text("Measured from PSI") }, singleLine = true, enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(draft.to, { draft.to = it }, label = { Text("Measured to PSI") }, singleLine = true, enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(draft.measured, { draft.measured = it }, label = { Text("Measured fill time") }, supportingText = { Text("Minutes:seconds, for example 5:30. Maximum 120:00.") }, isError = draft.measured.isNotBlank() && (airUpSeconds(draft.measured) ?: 0) !in 1..7200, singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
    }
    if (calibrating && draft.from.toIntOrNull() != null && draft.to.toIntOrNull() != null && draft.to.toInt() <= draft.from.toInt()) Text("Measured ending PSI must be above measured starting PSI.")
    val timer = draft.timer
    if (timer != null) {
        Text("Estimated time · ${airUpDuration(timer.estimatedSeconds)}", style = MaterialTheme.typography.titleMedium)
        if (timer.outsideReferenceRange) Text("Outside the measured pressure range. Review the time.")
        if (!draft.valid) Text("Choose a time between 0:10 and 10:00. Longer fills need a separate session.")
    }
    OutlinedTextField(draft.override, { draft.override = it }, label = { Text("Time override") }, supportingText = { Text("Minutes:seconds. Leave blank to use the estimate.") }, singleLine = true, isError = draft.override.isNotBlank() && (airUpSeconds(draft.override) ?: 0) !in 10..600, enabled = editable, modifier = Modifier.fillMaxWidth())
    Text("Estimated from your setup, not live pressure. Check all four tires with a pressure gauge.", style = MaterialTheme.typography.bodySmall)
}
