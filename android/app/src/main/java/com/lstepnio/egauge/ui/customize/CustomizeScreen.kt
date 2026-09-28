package com.lstepnio.egauge.ui.customize

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.*

/** All mutations still go through the existing ViewModel's page and alert methods. */
data class CustomizeActions(
    val selectPage: (Int) -> Unit, val selectReading: (String) -> Unit, val selectSecondary: (String) -> Unit,
    val addPage: () -> Unit, val removePage: (Int) -> Unit, val movePage: (Int, Int) -> Unit,
    val layout: (GaugeLayout) -> Unit, val warning: (Int) -> Unit, val critical: (Int) -> Unit,
    val resetMargin: (Int) -> Unit, val trigger: (Int) -> Unit, val clear: (Int) -> Unit,
    val check: () -> Unit, val send: () -> Unit, val setup: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen(state: CustomizeUiState, step: Int, onStep: (Int) -> Unit,
    onBack: () -> Unit, onDetails: () -> Unit, actions: CustomizeActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var previewState by rememberSaveable { mutableStateOf(PreviewCondition.Normal) }
    val current = state.pages.getOrNull(state.editingPage) ?: state.pages.first()
    val titles = listOf("Choose readings", "Choose layouts", "Set limits", "Review your gauge")
    ScreenContent(scrollKey = step) {
        ScreenTitle(titles[step], onBack)
        Text("Customize · ${step + 1} of 4", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
        if (!state.editingEnabled && !state.busy) StatusCard(StatusUi("Editing is paused",
            "Your saved settings need attention. Open Details to review the problem.", StatusTone.Error))
        if (step <= 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.pages.forEachIndexed { index, page ->
                    FilterChip(selected = state.editingPage == index, onClick = { actions.selectPage(index) },
                        enabled = state.editingEnabled, label = { Text("${index + 1}. ${page.name}") })
                }
            }
        }
        when (step) {
            0 -> {
                ResponsivePanels(first = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(query, { query = it }, label = { Text("Search readings") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        val readings = state.readings.filter { it.name.contains(query.trim(), true) }
                        if (readings.isEmpty()) EmptyState("No matching readings", "Try a different reading name.")
                        readings.forEach { reading ->
                            ReadingTile(reading.name, reading.unit, current.readingId == reading.id,
                                { actions.selectReading(reading.id) }, enabled = state.editingEnabled)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(actions.addPage, enabled = state.editingEnabled && state.pages.size < 8) { Text("Add page") }
                            TextButton({ actions.removePage(state.editingPage) }, enabled = state.editingEnabled && state.pages.size > 1) { Text("Remove page") }
                            TextButton({ actions.movePage(state.editingPage, -1) }, enabled = state.editingEnabled && state.editingPage > 0) { Text("Move earlier") }
                            TextButton({ actions.movePage(state.editingPage, 1) }, enabled = state.editingEnabled && state.editingPage < state.pages.lastIndex) { Text("Move later") }
                        }
                    }
                }, second = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RoundPreview(current.preview)
                        Text("Car compatibility has not been checked.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryAction("Choose layouts", { onStep(1) }, enabled = state.editingEnabled)
                    }
                })
            }
            1 -> ResponsivePanels(first = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GaugeLayout.entries.forEach { layout ->
                        ReadingTile(layout.label, when (layout) {
                            GaugeLayout.Numeric -> "One large number"; GaugeLayout.Arc -> "A number with a curved scale"
                            GaugeLayout.Bar -> "A number with a horizontal scale"; GaugeLayout.Trend -> "Recent changes over time"
                            GaugeLayout.Dual -> "Two readings together"
                        }, current.layout == layout, { actions.layout(layout) }, enabled = state.editingEnabled,
                            supporting = if (state.found && layout !in state.supportedLayouts) "Preview only on this gauge" else null)
                    }
                    if (current.layout == GaugeLayout.Dual) {
                        SectionTitle("Second reading")
                        state.readings.filter { it.id != current.readingId }.forEach { reading ->
                            ReadingTile(reading.name, reading.unit, current.secondaryId == reading.id,
                                { actions.selectSecondary(reading.id) }, enabled = state.editingEnabled)
                        }
                    }
                }
            }, second = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundPreview(current.preview)
                    PrimaryAction("Set limits", { onStep(2) }, enabled = state.editingEnabled)
                }
            })
            2 -> ResponsivePanels(first = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle("Coolant temperature")
                    LimitEditor("Warn above", state.warning, false, actions.warning, state.editingEnabled)
                    LimitEditor("Critical above", state.critical, true, actions.critical, state.editingEnabled)
                    Text("Limits for other readings are not available yet.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.advanced) {
                        SectionTitle("Alert timing")
                        ChoiceRow("Reset below limit", listOf(0, 2, 3, 5), state.resetMargin, "°C", state.editingEnabled, actions.resetMargin)
                        ChoiceRow("Alert after", listOf(0, 1, 2, 5), state.triggerSeconds.toInt(), "s", state.editingEnabled) { actions.trigger(it * 1000) }
                        ChoiceRow("Clear after", listOf(1, 2, 5, 10), state.clearSeconds.toInt(), "s", state.editingEnabled) { actions.clear(it * 1000) }
                    }
                }
            }, second = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundPreview(ReadingPreviewUi("Coolant temperature",
                        when (previewState) { PreviewCondition.Warning -> "${state.warning + 1}"
                            PreviewCondition.Critical -> "${state.critical + 1}"; else -> "92" }, "°C",
                        PreviewLayout.Arc, previewState))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PreviewCondition.entries.filter { it != PreviewCondition.Offline }.forEach { condition ->
                            FilterChip(previewState == condition, { previewState = condition }, label = { Text(condition.name) })
                        }
                    }
                    state.blockers.forEach { StatusCard(StatusUi("Check your settings", it, StatusTone.Error)) }
                    PrimaryAction("Review pages", { onStep(3) }, enabled = state.editingEnabled)
                }
            })
            3 -> {
                ResponsivePanels(first = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoundPreview(current.preview.copy(condition = previewState))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PreviewCondition.entries.filter { it != PreviewCondition.Offline }.forEach { condition ->
                                FilterChip(previewState == condition, { previewState = condition }, label = { Text(condition.name) })
                            }
                        }
                    }
                }, second = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Panel {
                            SectionTitle("${state.pages.size} pages, in this order")
                            state.pages.forEachIndexed { index, page ->
                                Text("${index + 1}. ${page.readingName}${page.preview.secondaryName?.let { " + $it" } ?: ""} · ${page.layout.label}",
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                            Text("Coolant: warn above ${state.warning} °C, critical above ${state.critical} °C.")
                            Text("Alert after ${state.triggerSeconds}s. Clear ${state.resetMargin} °C below the limit after ${state.clearSeconds}s.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.blockers.forEach { StatusCard(StatusUi("Check your settings", it, StatusTone.Error)) }
                        if (state.needsCheck && state.found) StatusCard(StatusUi("Check your gauge first",
                            "We will read its current settings before you send your changes."))
                        PrimaryAction(when { !state.found -> "Set up gauge"; state.needsCheck -> "Check gauge"; else -> "Send to gauge" },
                            { when { !state.found -> actions.setup(); state.needsCheck -> actions.check(); else -> actions.send() } },
                            enabled = !state.busy && (!state.found || state.needsCheck || state.canSend))
                        Text("Preview values are examples. Your car has not been checked.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
            }
        }
        TextButton(onDetails, Modifier.fillMaxWidth()) { Text("Details") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(label: String, values: List<Int>, selected: Int, unit: String, enabled: Boolean,
                      onSelect: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { onSelect(value) }, enabled = enabled,
            label = { Text("$value $unit") }) }
    }
}
