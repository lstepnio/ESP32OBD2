package com.lstepnio.egauge.ui.customize

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.GaugeAlertDraft
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ResponsivePanels
import com.lstepnio.egauge.ui.state.*
import kotlinx.coroutines.launch

/** All changes stay on the phone until the separate review and send action. */
data class CustomizeActions(
    val selectPage: (Int) -> Unit, val selectReading: (String) -> Unit, val selectSecondary: (String) -> Unit,
    val addPage: (String) -> Unit, val removePage: (Int) -> Unit, val movePage: (Int, Int) -> Unit,
    val layout: (GaugeLayout) -> Unit, val saveAlert: (GaugeAlertDraft) -> Unit, val removeAlert: (String) -> Unit,
    val check: () -> Unit, val send: () -> Unit, val setup: () -> Unit,
)

@Composable
fun DashboardEditorScreen(state: CustomizeUiState, destination: Int, onDestination: (Int) -> Unit,
                          onBack: () -> Unit, actions: CustomizeActions) {
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var alertReading by rememberSaveable { mutableStateOf<String?>(null) }
    var alertReturn by rememberSaveable { mutableIntStateOf(0) }
    val current = state.pages.getOrNull(state.editingPage) ?: state.pages.firstOrNull()
    fun editAlert(id: String) { alertReading = id; alertReturn = destination; onDestination(4) }
    fun done() = onDestination(0)
    PredictiveBackHandler(enabled = destination == 4 && picker == null) { progress ->
        progress.collect { }
        onDestination(alertReturn)
    }
    when {
        current == null -> EditorScaffold("Customize", onBack, "Add page", { picker = "add" },
            state.editingEnabled) { EmptyState("No pages yet", "Add a reading to start your gauge.") }
        destination == 2 -> PageManager(state, ::done, { picker = "add" }, actions)
        destination == 3 -> AlertManager(state, ::done, ::editAlert, actions)
        destination == 4 -> {
            val reading = state.readings.firstOrNull { it.id == alertReading } ?: state.readings.first()
            AlertEditor(state, reading, state.alerts.firstOrNull { it.readingId == reading.id },
                { onDestination(alertReturn) }, actions)
        }
        destination == 5 -> SendReview(state, ::done, actions)
        else -> EditorScaffold("Customize", onBack, "Review and send", { onDestination(5) },
            state.editingEnabled) {
            ResponsivePanels(first = {
                PagePreview(state, { actions.selectPage(it) }, { picker = "reading" })
            }, second = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PageToolbar(state, { picker = "add" }, { onDestination(2) })
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        Column {
                            EditorRow("Reading", current.readingName, state.editingEnabled) { picker = "reading" }
                            HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                            EditorRow("Layout", current.layout.label, state.editingEnabled) { picker = "layout" }
                            current.secondaryId?.let { id ->
                                HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                                EditorRow("Second reading", state.readings.firstOrNull { it.id == id }?.name ?: readingName(id),
                                    state.editingEnabled) { picker = "secondary" }
                            }
                        }
                    }
                    if (state.readings.any { it.id in com.lstepnio.egauge.ConfigurationProjector.supportedPidIds }) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                            Column {
                                listOfNotNull(current.readingId, current.secondaryId).filter { id ->
                                    state.readings.firstOrNull { it.id == id }?.source == "ECM"
                                }.forEachIndexed { index, id ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                                    val reading = state.readings.firstOrNull { it.id == id }
                                    val alert = state.alerts.firstOrNull { it.readingId == id }
                                    EditorRow("${reading?.name ?: readingName(id)} alert",
                                        if (reading == null) "Choose an available reading first" else alert?.summary() ?: "Off",
                                        state.editingEnabled && reading != null) { editAlert(id) }
                                }
                            }
                        }
                        TextButton({ onDestination(3) }, Modifier.fillMaxWidth()) { Text("All alerts (${state.alerts.size})") }
                    }
                    (state.editingIssue ?: state.blockers.firstOrNull())?.let { StatusCard(StatusUi("Check your settings", it, StatusTone.Error)) }
                }
            })
        }
    }
    if (picker != null) {
        val purpose = picker
        if (purpose == "layout" && current != null) LayoutPicker(state, current, { picker = null }) {
            actions.layout(it); picker = null
        } else ReadingPicker(state.readings.filter { purpose != "secondary" || (it.id != current?.readingId &&
            it.source == state.readings.firstOrNull { reading -> reading.id == current?.readingId }?.source) },
            if (purpose == "add") "Add page" else if (purpose == "secondary") "Second reading" else "Choose reading",
            if (purpose == "add") null else if (purpose == "secondary") current?.secondaryId else current?.readingId,
            state.editingEnabled, { picker = null }) { id ->
                when (purpose) {
                    "add" -> { actions.addPage(id); done() }
                    "secondary" -> actions.selectSecondary(id)
                    else -> actions.selectReading(id)
                }
                picker = null
            }
    }
}

/** Keep the one primary action in reach, including with the keyboard or large text. */
@Composable
internal fun EditorScaffold(title: String, onBack: () -> Unit, primary: String, onPrimary: () -> Unit,
                            enabled: Boolean = true,
                            content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            ScreenTitle(title, onBack)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                PrimaryAction(primary, onPrimary, Modifier.widthIn(max = 480.dp), enabled)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagePreview(state: CustomizeUiState, onSelect: (Int) -> Unit, onEdit: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val previewSize = if (maxWidth > 390.dp) 280.dp else 200.dp
    // Recreate only for an external selection/reorder. During a swipe, commit after settling.
    key(state.pages.map { it.id }, state.editingPage) {
        val pager = rememberPagerState(initialPage = state.editingPage.coerceIn(state.pages.indices), pageCount = { state.pages.size })
        val scope = rememberCoroutineScope()
        LaunchedEffect(pager.settledPage) { if (pager.settledPage != state.editingPage) onSelect(pager.settledPage) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalPager(pager, Modifier.fillMaxWidth().testTag("page-carousel")) { index ->
                val page = state.pages[index]
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = previewSize).fillMaxWidth().combinedClickable(
                        onClick = {}, onLongClick = { if (state.editingEnabled) onEdit() }, onLongClickLabel = "Change reading"
                    ).semantics(mergeDescendants = true) {
                        contentDescription = "Page ${index + 1} of ${state.pages.size}, ${page.name}. Swipe to change page. Hold to edit."
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction("Previous page") { scope.launch { pager.animateScrollToPage(index - 1) }; true })
                            if (index < state.pages.lastIndex) add(CustomAccessibilityAction("Next page") { scope.launch { pager.animateScrollToPage(index + 1) }; true })
                        }
                    }) {
                        RoundPreview(page.preview, Modifier.fillMaxWidth())
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                            Text("${index + 1}/${state.pages.size}", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            Text("Swipe between pages · Hold to edit", modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageToolbar(state: CustomizeUiState, onAdd: () -> Unit, onManage: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onAdd, enabled = state.editingEnabled && state.pages.size < 8) {
            EGaugeIcon(GaugeIcon.Add, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add page")
        }
        TextButton(onManage) { Text("Manage pages") }
    }
    if (state.pages.size >= 8) Text("All 8 pages are in use. Remove one to add another.", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun EditorRow(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail, style = MaterialTheme.typography.bodyLarge)
            }
            EGaugeIcon(GaugeIcon.Next)
        }
    }
}

internal fun AlertUi.summary() = "Warn $direction $warning $unit · Critical $direction $critical $unit"

@Composable
private fun SendReview(state: CustomizeUiState, onBack: () -> Unit, actions: CustomizeActions) {
    val pages = state.reviewPages.ifEmpty { state.pages }
    EditorScaffold("Review and send", onBack,
        when { !state.found -> "Set up gauge"; state.needsCheck -> "Check gauge"; else -> "Send to gauge" },
        { when { !state.found -> actions.setup(); state.needsCheck -> actions.check(); else -> actions.send() } },
        !state.busy && (!state.found || state.needsCheck || state.canSend)) {
        SectionTitle("${pages.size} pages")
        Panel {
            pages.forEachIndexed { index, page ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${index + 1}. ${page.readingName}" + (page.secondaryId?.let { " + ${readingName(it)}" } ?: ""),
                        style = MaterialTheme.typography.titleMedium)
                    Text(page.layout.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.actionSummary?.let { SectionTitle("Gesture action"); Text(it) }
        SectionTitle("Alerts")
        if (state.alerts.isEmpty()) Text("No alerts set") else Panel {
            state.alerts.forEach { alert ->
                Text(alert.readingName, style = MaterialTheme.typography.titleMedium)
                Text(alert.summary(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.blockers.forEach { StatusCard(StatusUi("Check your settings", it, StatusTone.Error)) }
        if (state.needsCheck && state.found) StatusCard(StatusUi("Check your gauge first",
            "We will read its current settings before you send your changes."))
        Text("Preview values are examples. Your car has not been checked.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
