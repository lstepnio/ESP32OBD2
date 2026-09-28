package com.lstepnio.egauge.ui.customize

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.*

/** Actions supplied by the existing ViewModel. */
data class CustomizeActions(
    val selectPage: (Int) -> Unit, val selectReading: (String) -> Unit, val selectSecondary: (String) -> Unit,
    val addPage: () -> Unit, val removePage: (Int) -> Unit, val movePage: (Int, Int) -> Unit,
    val layout: (GaugeLayout) -> Unit, val warning: (Int) -> Unit, val critical: (Int) -> Unit,
    val resetMargin: (Int) -> Unit, val trigger: (Int) -> Unit, val clear: (Int) -> Unit,
    val check: () -> Unit, val send: () -> Unit, val setup: () -> Unit,
)

/** Preview-led editor for configuring the gauge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardEditorScreen(state: CustomizeUiState, destination: Int, onDestination: (Int) -> Unit,
                          onBack: () -> Unit, onDetails: () -> Unit, actions: CustomizeActions) {
    val current = state.pages.getOrNull(state.editingPage) ?: state.pages.first()
    fun done() = onDestination(0)
    ScreenContent(scrollKey = destination) {
        when (destination) {
            0 -> Dashboard(state, current, onDestination, onBack, actions)
            1 -> PageEditor(state, current, ::done, actions)
            2 -> PageManager(state, current, ::done, actions)
            3 -> AlertEditor(state, ::done, actions)
            else -> SendReview(state, current, ::done, actions)
        }
        TextButton(onDetails, Modifier.fillMaxWidth()) { Text("Details") }
    }
}

@Composable
private fun Dashboard(state: CustomizeUiState, current: PageUi, onDestination: (Int) -> Unit, onBack: () -> Unit,
                      actions: CustomizeActions) {
    ScreenTitle("Customize", onBack = onBack)
    ResponsivePanels(first = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RoundPreview(current.preview)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Page ${state.editingPage + 1} of ${state.pages.size}", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton({ actions.selectPage(state.editingPage - 1) }, enabled = state.editingPage > 0) { Text("Previous") }
                    OutlinedButton({ actions.selectPage(state.editingPage + 1) }, enabled = state.editingPage < state.pages.lastIndex) { Text("Next") }
                }
            }
        }
    }, second = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsRow("Edit page", "${current.readingName} · ${current.layout.label}", state.editingEnabled) { onDestination(1) }
            SettingsRow("Manage pages", "${state.pages.size} pages", state.editingEnabled) { onDestination(2) }
            SettingsRow("Coolant alerts", "Warn above ${state.warning} °C · Critical above ${state.critical} °C", state.editingEnabled) { onDestination(3) }
            state.blockers.firstOrNull()?.let { StatusCard(StatusUi("Check your settings", it, StatusTone.Error)) }
            PrimaryAction("Review and send", { onDestination(4) }, enabled = state.editingEnabled)
        }
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageEditor(state: CustomizeUiState, current: PageUi, onDone: () -> Unit, actions: CustomizeActions) {
    var query by remember { mutableStateOf("") }
    ScreenTitle("Edit page", onBack = onDone)
    RoundPreview(current.preview)
    SectionTitle("Reading")
    OutlinedTextField(query, { query = it }, label = { Text("Search readings") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    state.readings.filter { it.name.contains(query.trim(), true) }.forEach { reading ->
        ReadingTile(reading.name, reading.unit, current.readingId == reading.id, { actions.selectReading(reading.id) }, enabled = state.editingEnabled)
    }
    SectionTitle("Layout")
    GaugeLayout.entries.forEach { layout ->
        ReadingTile(layout.label, layoutDescription(layout), current.layout == layout, { actions.layout(layout) }, enabled = state.editingEnabled,
            supporting = if (state.found && layout !in state.supportedLayouts) "Preview only on this gauge" else null)
    }
    if (current.layout == GaugeLayout.Dual) {
        SectionTitle("Second reading")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.readings.filter { it.id != current.readingId }.forEach { reading ->
                FilterChip(current.secondaryId == reading.id, { actions.selectSecondary(reading.id) }, label = { Text(reading.name) })
            }
        }
    }
    PrimaryAction("Done", onDone, enabled = state.editingEnabled)
}

@Composable
private fun PageManager(state: CustomizeUiState, current: PageUi, onDone: () -> Unit, actions: CustomizeActions) {
    ScreenTitle("Manage pages", onBack = onDone)
    Text("Choose a page to edit or change the order.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.pages.forEachIndexed { index, page ->
        Panel {
            Text("${index + 1}. ${page.readingName}", style = MaterialTheme.typography.titleMedium)
            Text(page.layout.label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ actions.selectPage(index); onDone() }) { Text("Edit") }
                TextButton({ actions.movePage(index, -1) }, enabled = index > 0) { Text("Move earlier") }
                TextButton({ actions.movePage(index, 1) }, enabled = index < state.pages.lastIndex) { Text("Move later") }
                TextButton({ actions.removePage(index) }, enabled = state.pages.size > 1) { Text("Remove") }
            }
        }
    }
    OutlinedButton(actions.addPage, enabled = state.editingEnabled && state.pages.size < 8, modifier = Modifier.fillMaxWidth()) { Text("Add page") }
    PrimaryAction("Done", onDone)
}

@Composable
private fun AlertEditor(state: CustomizeUiState, onDone: () -> Unit, actions: CustomizeActions) {
    var preview by remember { mutableStateOf(PreviewCondition.Normal) }
    ScreenTitle("Coolant alerts", onBack = onDone)
    RoundPreview(ReadingPreviewUi("Coolant temperature", when (preview) {
        PreviewCondition.Warning -> "${state.warning + 1}"; PreviewCondition.Critical -> "${state.critical + 1}"; else -> "92"
    }, "°C", PreviewLayout.Arc, preview))
    Text("These alerts apply to coolant temperature.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LimitEditor("Warn above", state.warning, false, actions.warning, state.editingEnabled)
    LimitEditor("Critical above", state.critical, true, actions.critical, state.editingEnabled)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PreviewCondition.Normal, PreviewCondition.Warning, PreviewCondition.Critical).forEach { condition ->
            FilterChip(preview == condition, { preview = condition }, label = { Text(condition.name.lowercase().replaceFirstChar(Char::titlecase)) })
        }
    }
    PrimaryAction("Done", onDone, enabled = state.editingEnabled)
}

@Composable
private fun SendReview(state: CustomizeUiState, current: PageUi, onDone: () -> Unit, actions: CustomizeActions) {
    ScreenTitle("Review and send", onBack = onDone)
    RoundPreview(current.preview)
    Panel {
        SectionTitle("Your gauge")
        state.pages.forEachIndexed { index, page -> Text("${index + 1}. ${page.readingName} · ${page.layout.label}") }
        Text("Coolant alerts: warn above ${state.warning} °C, critical above ${state.critical} °C.")
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

private fun layoutDescription(layout: GaugeLayout): String = when (layout) {
    GaugeLayout.Numeric -> "One large number"; GaugeLayout.Arc -> "A number with a curved scale"
    GaugeLayout.Bar -> "A number with a horizontal scale"; GaugeLayout.Trend -> "Recent changes over time"
    GaugeLayout.Dual -> "Two readings together"
}
