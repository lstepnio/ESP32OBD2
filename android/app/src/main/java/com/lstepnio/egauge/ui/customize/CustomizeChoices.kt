package com.lstepnio.egauge.ui.customize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                IconButton(onDismiss) { EGaugeIcon(GaugeIcon.Close, "Close choices") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
internal fun ReadingPicker(readings: List<ReadingUi>, title: String, selectedId: String?, enabled: Boolean,
                           onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ChoiceSheet(title, onDismiss) {
        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(query, { query = it }, label = { Text("Search readings") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        val matches = readings.filter { "${it.name} ${it.unit}".contains(query.trim(), ignoreCase = true) }
        if (matches.isEmpty()) EmptyState("No matching readings", "Try a different name or clear your search.")
        matches.forEach { reading -> ReadingTile(reading.name, reading.unit, selectedId == reading.id,
            { onSelect(reading.id) }, enabled = enabled) }
        Text("Vehicle support has not been checked.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun LayoutPicker(state: CustomizeUiState, current: PageUi, onDismiss: () -> Unit, onSelect: (GaugeLayout) -> Unit) {
    ChoiceSheet("Choose layout", onDismiss) { LayoutChoices(state, current, onSelect) }
}

/** Shared by the actual sheet and the reviewable screenshot fixture. */
@Composable
fun LayoutChoices(state: CustomizeUiState, current: PageUi, onSelect: (GaugeLayout) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (fontScale > 1.3f || maxWidth < 300.dp) 1 else if (maxWidth >= 700.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeLayout.entries.filter { current.readingId != "tcmgear" || it in listOf(GaugeLayout.Numeric, GaugeLayout.Dual) }.chunked(columns).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    group.forEach { layout ->
                        val selected = current.layout == layout
                        Surface(onClick = { onSelect(layout) }, enabled = state.editingEnabled,
                            shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f).semantics { this.selected = selected },
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                RoundPreview(current.preview.copy(layout = PreviewLayout.valueOf(layout.name),
                                    secondaryName = current.preview.secondaryName ?: "Coolant temperature",
                                    secondaryValue = current.preview.secondaryValue ?: "92",
                                    secondaryUnit = current.preview.secondaryUnit ?: "°C"),
                                    Modifier.width(80.dp).clearAndSetSemantics {}, showDescription = false)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (selected) EGaugeIcon(GaugeIcon.Check, modifier = Modifier.size(18.dp))
                                    Text(layout.label, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(if (state.found && layout !in state.supportedLayouts) "Preview only on this gauge" else layoutDescription(layout),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun PageManager(state: CustomizeUiState, onBack: () -> Unit, onAdd: () -> Unit, actions: CustomizeActions) {
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    EditorScaffold("Manage pages", onBack, "Done", onBack) {
        OutlinedButton(onAdd, enabled = state.editingEnabled && state.pages.size < 8, modifier = Modifier.fillMaxWidth()) {
            EGaugeIcon(GaugeIcon.Add); Spacer(Modifier.width(8.dp)); Text("Add page")
        }
        if (state.pages.size == 1) Text("Keep at least one page.", style = MaterialTheme.typography.bodyMedium)
        if (state.pages.size >= 8) Text("All 8 pages are in use. Remove one to add another.", style = MaterialTheme.typography.bodyMedium)
        state.pages.forEachIndexed { index, page ->
            key(page.id) {
                var menu by remember { mutableStateOf(false) }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                    border = if (index == state.editingPage) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable { actions.selectPage(index); onBack() }.padding(10.dp)
                            .semantics { contentDescription = "Edit page ${index + 1}, ${page.name}" },
                            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(page.readingName, style = MaterialTheme.typography.titleMedium)
                                Text(page.layout.label + (page.secondaryId?.let { " · ${readingName(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box {
                            IconButton({ menu = true }, enabled = state.editingEnabled) { EGaugeIcon(GaugeIcon.Settings, "Options for page ${index + 1}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Move earlier") }, { actions.movePage(index, -1); menu = false }, enabled = index > 0)
                                DropdownMenuItem({ Text("Move later") }, { actions.movePage(index, 1); menu = false }, enabled = index < state.pages.lastIndex)
                                DropdownMenuItem({ Text("Remove page") }, { removeId = page.id; menu = false }, enabled = state.pages.size > 1 && (state.removablePageIds == null || page.id in state.removablePageIds))
                            }
                        }
                    }
                }
            }
        }
    }
    val removal = state.pages.indexOfFirst { it.id == removeId }
    if (removal >= 0) AlertDialog(onDismissRequest = { removeId = null },
        title = { Text("Remove page ${removal + 1}?") },
        text = { Text("${state.pages[removal].readingName} will leave this page list. Alerts for the reading will stay.") },
        confirmButton = { TextButton({ actions.removePage(removal); removeId = null },
            enabled = state.editingEnabled && state.pages.size > 1) { Text("Remove page") } },
        dismissButton = { TextButton({ removeId = null }) { Text("Keep page") } })
}

@Composable
internal fun AlertManager(state: CustomizeUiState, onBack: () -> Unit, onEdit: (String) -> Unit, actions: CustomizeActions) {
    EditorScaffold("All alerts", onBack, "Done", onBack) {
        Text("Alerts follow a reading across all pages, even when it is not on the screen.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.readings.filter { it.id in com.lstepnio.egauge.ConfigurationProjector.supportedPidIds }.forEach { reading ->
            Surface(shape = MaterialTheme.shapes.large) {
                EditorRow(reading.name, state.alerts.firstOrNull { it.readingId == reading.id }?.summary() ?: "Off", state.editingEnabled) {
                    onEdit(reading.id)
                }
            }
        }
    }
}

private fun layoutDescription(layout: GaugeLayout): String = when (layout) {
    GaugeLayout.Numeric -> "One large number"; GaugeLayout.Arc -> "A curved scale"
    GaugeLayout.Bar -> "A horizontal scale"; GaugeLayout.Trend -> "Changes over time"
    GaugeLayout.Dual -> "Two readings"
}
