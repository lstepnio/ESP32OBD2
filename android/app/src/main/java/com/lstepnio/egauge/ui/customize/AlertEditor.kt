package com.lstepnio.egauge.ui.customize

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.AlertDirection
import com.lstepnio.egauge.MeasurementUnits
import com.lstepnio.egauge.demoCatalog
import com.lstepnio.egauge.readingRange
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ResponsivePanels
import com.lstepnio.egauge.ui.state.*

private val formSaver = listSaver<AlertForm, String>(
    save = { listOf(it.direction.name, it.warning, it.critical, it.reset, it.trigger, it.clear) },
    restore = { AlertForm(AlertDirection.valueOf(it[0]), it[1], it[2], it[3], it[4], it[5]) })

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertEditor(state: CustomizeUiState, reading: ReadingUi, original: AlertUi?, onBack: () -> Unit,
                actions: CustomizeActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 600.dp && LocalDensity.current.fontScale <= 1.3f
    var form by rememberSaveable(reading.id, stateSaver = formSaver) { mutableStateOf(AlertForm.from(original)) }
    var behavior by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(PreviewCondition.Normal) }
    val canonicalUnit = demoCatalog.first { it.id == reading.id }.unit
    val range = MeasurementUnits.range(readingRange(reading.id), canonicalUnit, state.measurementSystem)
    val maxReset = MeasurementUnits.distance(20, canonicalUnit, state.measurementSystem)
    val errors = form.errors(range, maxReset)
    val displayedResult = form.saved(reading.id, range, original, maxReset)
    val result = displayedResult?.let { draft ->
        draft.copy(
            warning = if (original != null && draft.warning == original.warning) original.canonicalWarning
                else MeasurementUnits.canonical(draft.warning, canonicalUnit, state.measurementSystem),
            critical = if (original != null && draft.critical == original.critical) original.canonicalCritical
                else MeasurementUnits.canonical(draft.critical, canonicalUnit, state.measurementSystem),
            hysteresis = if (original != null && draft.hysteresis == original.resetMargin) original.canonicalResetMargin
                else MeasurementUnits.canonicalDistance(draft.hysteresis, canonicalUnit, state.measurementSystem),
        ).takeIf { it.warning in readingRange(reading.id) && it.critical in readingRange(reading.id) && it.hysteresis in 0..20 }
    }
    EditorScaffold("Edit alert", onBack, "Save alert", { result?.let { actions.saveAlert(it); onBack() } },
        state.editingEnabled && result != null) {
        Text(reading.name, style = MaterialTheme.typography.titleLarge)
        Text("Applies wherever this reading is used.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Alert when the reading")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertDirection.entries.forEach { direction ->
                        FilterChip(form.direction == direction, { form = form.copy(direction = direction) },
                            enabled = state.editingEnabled, label = { Text(if (direction == AlertDirection.Above) "Rises above" else "Falls below") })
                    }
                }
                LimitField("Warning", form.warning, reading.unit, { form = form.copy(warning = it) }, state.editingEnabled,
                    errors["warning"]?.takeIf { form.warning.isNotBlank() }, "${range.first} to ${range.last} ${reading.unit}")
                LimitField("Critical", form.critical, reading.unit, { form = form.copy(critical = it) }, state.editingEnabled,
                    errors["critical"]?.takeIf { form.critical.isNotBlank() }, "${range.first} to ${range.last} ${reading.unit}")
                TextButton({ behavior = !behavior }) { Text(if (behavior) "Hide alert behavior" else "Alert behavior") }
                if (behavior) {
                    Text("Wait before showing or clearing an alert to avoid brief changes.", style = MaterialTheme.typography.bodyMedium)
                    LimitField("Show after", form.trigger, "s", { form = form.copy(trigger = it) }, state.editingEnabled, errors["trigger"])
                    LimitField("Clear after", form.clear, "s", { form = form.copy(clear = it) }, state.editingEnabled, errors["clear"])
                    LimitField("Reset distance", form.reset, reading.unit, { form = form.copy(reset = it) }, state.editingEnabled,
                        errors["reset"], "0 to $maxReset ${reading.unit}")
                    Text("The reading must move back past the limit by this amount before the alert clears.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (errors.keys.any { it in setOf("reset", "trigger", "clear") }) {
                    Text("Check the reset distance or delay. Open Alert behavior to adjust it.",
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!wide) TextButton({ showPreview = !showPreview }) { Text(if (showPreview) "Hide preview" else "Preview alert") }
                if (wide || showPreview) {
                    val warn = form.warning.toIntOrNull()
                    val urgent = form.critical.toIntOrNull()
                    val step = if (form.direction == AlertDirection.Above) 1L else -1L
                    val value = when (preview) {
                        PreviewCondition.Warning -> warn?.toLong()?.plus(step)
                        PreviewCondition.Critical -> urgent?.toLong()?.plus(step)
                        else -> warn?.toLong()?.minus(step * ((range.last - range.first) / 10).coerceAtLeast(1))
                    }?.coerceIn(range.first.toLong(), range.last.toLong())?.toString() ?: "--"
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RoundPreview(ReadingPreviewUi(reading.name, value, reading.unit, PreviewLayout.Arc, preview), Modifier.widthIn(max = 200.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PreviewCondition.Normal, PreviewCondition.Warning, PreviewCondition.Critical, PreviewCondition.Stale).forEach { condition ->
                            FilterChip(preview == condition, { preview = condition }, label = { Text(condition.name) })
                        }
                    }
                }
                if (original != null) TextButton({ actions.removeAlert(original.id); onBack() }, enabled = state.editingEnabled) {
                    Text("Remove alert", color = MaterialTheme.colorScheme.error)
                }
            }
        })
    }
    }
}
