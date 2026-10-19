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
import com.lstepnio.egauge.readingBounds
import com.lstepnio.egauge.gearPositions
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
    val definition = demoCatalog.first { it.id == reading.id }
    val canonicalUnit = definition.unit
    val gear = definition.alertKind == "gear"
    LaunchedEffect(gear) { if (gear) form = form.copy(direction = AlertDirection.Equals, reset = "0") }
    val range = MeasurementUnits.range(readingBounds(reading.id), canonicalUnit, state.measurementSystem)
    val maxReset = MeasurementUnits.distance((definition.maximum - definition.minimum), canonicalUnit, state.measurementSystem)
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
        ).takeIf { it.warning in readingBounds(reading.id) && it.critical in readingBounds(reading.id) && it.hysteresis >= 0 && it.hysteresis < definition.maximum - definition.minimum &&
            com.lstepnio.egauge.ConfigurationProjector.blockers(com.lstepnio.egauge.Draft(alerts = listOf(it))).isEmpty() }
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
                    AlertDirection.entries.filter { if (gear) it == AlertDirection.Equals else it != AlertDirection.Equals }.forEach { direction ->
                        FilterChip(form.direction == direction, { form = form.copy(direction = direction) },
                            enabled = state.editingEnabled, label = { Text(when (direction) { AlertDirection.Above -> "Rises above"; AlertDirection.Below -> "Falls below"; AlertDirection.Equals -> "Matches position" }) })
                    }
                }
                if (gear) {
                    Text("Warning position")
                    GearPositionField(form.warning, { form = form.copy(warning = it) }, state.editingEnabled)
                    Text("Critical position")
                    GearPositionField(form.critical, { form = form.copy(critical = it) }, state.editingEnabled)
                    Text("If both positions match, Critical takes priority.", style = MaterialTheme.typography.bodySmall)
                } else {
                LimitField("Warning", form.warning, reading.unit, { form = form.copy(warning = it) }, state.editingEnabled,
                    errors["warning"]?.takeIf { form.warning.isNotBlank() }, "${MeasurementUnits.format(range.start)} to ${MeasurementUnits.format(range.endInclusive)} ${reading.unit}")
                LimitField("Critical", form.critical, reading.unit, { form = form.copy(critical = it) }, state.editingEnabled,
                    errors["critical"]?.takeIf { form.critical.isNotBlank() }, "${MeasurementUnits.format(range.start)} to ${MeasurementUnits.format(range.endInclusive)} ${reading.unit}")
                }
                TextButton({ behavior = !behavior }) { Text(if (behavior) "Hide alert behavior" else "Alert behavior") }
                if (behavior) {
                    Text("Wait before showing or clearing an alert to avoid brief changes.", style = MaterialTheme.typography.bodyMedium)
                    LimitField("Show after", form.trigger, "s", { form = form.copy(trigger = it) }, state.editingEnabled, errors["trigger"])
                    LimitField("Clear after", form.clear, "s", { form = form.copy(clear = it) }, state.editingEnabled, errors["clear"])
                    if (!gear) LimitField("Reset distance", form.reset, reading.unit, { form = form.copy(reset = it) }, state.editingEnabled,
                        errors["reset"], "0 to $maxReset ${reading.unit}")
                    if (!gear) Text("The reading must move back past the limit by this amount before the alert clears.",
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
                    val warn = form.warning.toDoubleOrNull()
                    val urgent = form.critical.toDoubleOrNull()
                    val step = if (form.direction == AlertDirection.Above) 1.0 else -1.0
                    val value = if (gear) when (preview) {
                        PreviewCondition.Warning -> gearPositions[warn]
                        PreviewCondition.Critical -> gearPositions[urgent]
                        else -> "--"
                    } ?: "--" else when (preview) {
                        PreviewCondition.Warning -> warn?.plus(step)
                        PreviewCondition.Critical -> urgent?.plus(step)
                        else -> warn?.minus(step * ((range.endInclusive - range.start) / 10).coerceAtLeast(0.001))
                    }?.coerceIn(range.start, range.endInclusive)?.let(MeasurementUnits::format) ?: "--"
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearPositionField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        gearPositions.forEach { (code, label) ->
            FilterChip(value.toDoubleOrNull() == code, { onChange(com.lstepnio.egauge.alertNumber(code)) },
                enabled = enabled, label = { Text(label) })
        }
    }
}
