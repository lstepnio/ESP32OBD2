package com.lstepnio.egauge.ui.customize

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.AlertDirection
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.*

/** Actions supplied by the existing ViewModel. */
data class CustomizeActions(
    val selectPage: (Int) -> Unit, val selectReading: (String) -> Unit, val selectSecondary: (String) -> Unit,
    val addPage: () -> Unit, val removePage: (Int) -> Unit, val movePage: (Int, Int) -> Unit,
    val layout: (GaugeLayout) -> Unit, val addAlert: (String) -> Unit, val removeAlert: (String) -> Unit,
    val warning: (String, Int) -> Unit, val critical: (String, Int) -> Unit,
    val direction: (String, AlertDirection) -> Unit, val resetMargin: (String, Int) -> Unit,
    val trigger: (String, Int) -> Unit, val clear: (String, Int) -> Unit,
    val check: () -> Unit, val send: () -> Unit, val setup: () -> Unit,
)

/** Preview-led editor for configuring the gauge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardEditorScreen(state: CustomizeUiState, destination: Int, onDestination: (Int) -> Unit,
                          onBack: () -> Unit, actions: CustomizeActions) {
    val current = state.pages.getOrNull(state.editingPage) ?: state.pages.first()
    var editingAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    fun done() = onDestination(0)
    ScreenContent(scrollKey = destination) {
        when (destination) {
            0 -> Dashboard(state, current, onDestination, onBack, actions)
            1 -> PageEditor(state, current, ::done, actions)
            2 -> PageManager(state, current, ::done, actions)
            3 -> AlertManager(state, ::done, { id -> editingAlertId = id; onDestination(4) },
                { editingAlertId = null; onDestination(4) }, actions)
            4 -> AlertEditor(state, state.alerts.firstOrNull { it.id == editingAlertId }, ::done,
                { id -> editingAlertId = id }, actions)
            else -> SendReview(state, current, ::done, actions)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dashboard(state: CustomizeUiState, current: PageUi, onDestination: (Int) -> Unit, onBack: () -> Unit,
                      actions: CustomizeActions) {
    val pager = rememberPagerState(initialPage = state.editingPage.coerceIn(0, state.pages.lastIndex), pageCount = { state.pages.size })
    LaunchedEffect(state.editingPage, state.pages.size) {
        val selected = state.editingPage.coerceIn(0, state.pages.lastIndex)
        if (pager.currentPage != selected) pager.scrollToPage(selected)
    }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage != state.editingPage) actions.selectPage(pager.currentPage)
    }
    ScreenTitle("Customize", onBack = onBack)
    ResponsivePanels(first = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth().testTag("page-carousel")) { index ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CustomizePreviewPage(state.pages[index], index, state.pages.size) { onDestination(1) }
                }
            }
            Text("Swipe between pages · Hold to edit", modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }, second = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsRow("Edit page", "${current.readingName} · ${current.layout.label}", state.editingEnabled) { onDestination(1) }
            SettingsRow("Manage pages", "${state.pages.size} pages · Add, remove, or reorder", state.editingEnabled) { onDestination(2) }
            SettingsRow("Alerts", if (state.alerts.isEmpty()) "No alerts" else "${state.alerts.size} alert${if (state.alerts.size == 1) "" else "s"}", state.editingEnabled) { onDestination(3) }
            state.blockers.firstOrNull()?.let { StatusCard(StatusUi("Check your settings", it, StatusTone.Error)) }
            PrimaryAction("Review and send", { onDestination(5) }, enabled = state.editingEnabled)
        }
    })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomizePreviewPage(page: PageUi, index: Int, total: Int, onEdit: () -> Unit) {
    Box(Modifier.widthIn(max = 280.dp).fillMaxWidth().combinedClickable(
        onClick = {}, onLongClick = onEdit, onLongClickLabel = "Edit ${page.name}"
    ).semantics(mergeDescendants = true) {
        contentDescription = "Page ${index + 1} of $total, ${page.name}. Swipe to change page. Hold to edit."
    }) {
        RoundPreview(page.preview, Modifier.fillMaxWidth())
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Text("${index + 1}/$total", style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
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
    Text("Add a page, remove one, or put them in the order you want.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(actions.addPage, enabled = state.editingEnabled && state.pages.size < 8, modifier = Modifier.fillMaxWidth()) {
        Text("Add page")
    }
    SectionTitle("Pages")
    state.pages.forEachIndexed { index, page ->
        Panel {
            Text("${index + 1}. ${page.readingName}", style = MaterialTheme.typography.titleMedium)
            Text(page.layout.label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ actions.selectPage(index); onDone() }) { Text("Edit page") }
                TextButton({ actions.removePage(index) }, enabled = state.editingEnabled && state.pages.size > 1) { Text("Remove page") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ actions.movePage(index, -1) }, enabled = state.editingEnabled && index > 0) { Text("Move earlier") }
                OutlinedButton({ actions.movePage(index, 1) }, enabled = state.editingEnabled && index < state.pages.lastIndex) { Text("Move later") }
            }
        }
    }
    PrimaryAction("Done", onDone)
}

@Composable
private fun AlertManager(state: CustomizeUiState, onDone: () -> Unit, onEdit: (String) -> Unit,
                         onAdd: () -> Unit, actions: CustomizeActions) {
    ScreenTitle("Alerts", onBack = onDone)
    Text("Choose readings to watch and the limits that matter to you.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onAdd, enabled = state.editingEnabled && state.alerts.size < state.readings.size,
        modifier = Modifier.fillMaxWidth()) { Text("Add alert") }
    if (state.alerts.isEmpty()) EmptyState("No alerts yet", "Add an alert for a reading on your gauge.")
    state.alerts.forEach { alert ->
        Panel {
            Text(alert.readingName, style = MaterialTheme.typography.titleMedium)
            Text("Warn ${alert.direction} ${alert.warning} ${alert.unit} · Critical ${alert.direction} ${alert.critical} ${alert.unit}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ onEdit(alert.id) }) { Text("Edit alert") }
                TextButton({ actions.removeAlert(alert.id) }, enabled = state.editingEnabled) { Text("Remove alert") }
            }
        }
    }
    PrimaryAction("Done", onDone)
}

@Composable
private fun AlertEditor(state: CustomizeUiState, alert: AlertUi?, onDone: () -> Unit,
                        onAlertAdded: (String) -> Unit, actions: CustomizeActions) {
    if (alert == null) {
        ScreenTitle("Choose a reading", onBack = onDone)
        Text("Add an alert for a reading you want to keep an eye on.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.readings.filter { reading -> state.alerts.none { it.readingId == reading.id } }.forEach { reading ->
            ReadingTile(reading.name, reading.unit, false, {
                actions.addAlert(reading.id)
                onAlertAdded("alert.${reading.id}")
            }, enabled = state.editingEnabled)
        }
        return
    }
    var preview by remember(alert.id) { mutableStateOf(PreviewCondition.Normal) }
    ScreenTitle("${alert.readingName} alert", onBack = onDone)
    val normalValue = if (alert.direction == "above")
        (alert.warning - ((alert.range.last - alert.range.first) / 10).coerceAtLeast(1)).coerceAtLeast(alert.range.first)
    else (alert.warning + ((alert.range.last - alert.range.first) / 10).coerceAtLeast(1)).coerceAtMost(alert.range.last)
    val warningValue = if (alert.direction == "above") alert.warning + 1 else alert.warning - 1
    val criticalValue = if (alert.direction == "above") alert.critical + 1 else alert.critical - 1
    RoundPreview(ReadingPreviewUi(alert.readingName, when (preview) {
        PreviewCondition.Warning -> "$warningValue"; PreviewCondition.Critical -> "$criticalValue"; else -> "$normalValue"
    }, alert.unit, PreviewLayout.Arc, preview))
    Text("Alert me when this reading ${if (alert.direction == "above") "rises above" else "falls below"} these limits.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(alert.direction == "above", { actions.direction(alert.id, AlertDirection.Above) }, label = { Text("Rises above") })
        FilterChip(alert.direction == "below", { actions.direction(alert.id, AlertDirection.Below) }, label = { Text("Falls below") })
    }
    LimitEditor("Warn ${alert.direction}", alert.warning, false, { actions.warning(alert.id, it) }, state.editingEnabled)
    LimitEditor("Critical ${alert.direction}", alert.critical, true, { actions.critical(alert.id, it) }, state.editingEnabled)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PreviewCondition.Normal, PreviewCondition.Warning, PreviewCondition.Critical).forEach { condition ->
            FilterChip(preview == condition, { preview = condition }, label = { Text(condition.name.lowercase().replaceFirstChar(Char::titlecase)) })
        }
    }
    PrimaryAction("Save alert", onDone, enabled = state.editingEnabled)
}

@Composable
private fun SendReview(state: CustomizeUiState, current: PageUi, onDone: () -> Unit, actions: CustomizeActions) {
    ScreenTitle("Review and send", onBack = onDone)
    RoundPreview(current.preview)
    Panel {
        SectionTitle("Your gauge")
        state.pages.forEachIndexed { index, page -> Text("${index + 1}. ${page.readingName} · ${page.layout.label}") }
        Text(if (state.alerts.isEmpty()) "No alerts set." else state.alerts.joinToString("\n") { alert ->
            "${alert.readingName}: warn ${alert.direction} ${alert.warning} ${alert.unit}; critical ${alert.direction} ${alert.critical} ${alert.unit}." })
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
