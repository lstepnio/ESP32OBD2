package com.lstepnio.egauge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.car.FaultCodeExplanation
import com.lstepnio.egauge.ui.state.CarFaultUi
import com.lstepnio.egauge.ui.state.alertContextReading
import com.lstepnio.egauge.ui.state.alertTitle
import com.lstepnio.egauge.ui.state.recordedFaultCodes
import java.text.DateFormat
import java.util.Date

/** A full-screen list keeps its tab and scroll position while inspecting an episode. */
@Composable
fun CarAlertWorkspace(
    model: AppViewModel,
    onExport: () -> Unit,
    onBack: () -> Unit,
    initialHistory: Boolean = false,
    initialAlertId: String? = null
) {
    val vehicle = model.profileCollection.activeId
    var history by rememberSaveable(vehicle) { mutableStateOf(initialHistory) }
    var selectedId by rememberSaveable(vehicle) { mutableStateOf(initialAlertId) }
    var selectedFaultKey by rememberSaveable(vehicle) { mutableStateOf<String?>(null) }
    val entries = model.visibleAlertHistory().filter { it.vehicle == vehicle }
    val selected = entries.firstOrNull { it.id == selectedId }
    val currentScroll = rememberScrollState()
    val historyScroll = rememberScrollState()
    val back = { if (selectedId != null) selectedId = null else onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(selectedId, entries, selectedFaultKey) {
        val faultMissing = selectedFaultKey != null && selected?.let { entry ->
            recordedFaultCodes(entry.context).none { "${it.source}:${it.code}" == selectedFaultKey }
        } == true
        if (selectedId != null && (selected == null || faultMissing)) selectedId = null
    }
    if (selected != null) {
        CarAlertDetail(
            model,
            selected,
            onBack = { selectedId = null },
            onExport = onExport,
            selectedFault = recordedFaultCodes(selected.context).firstOrNull { "${it.source}:${it.code}" == selectedFaultKey }
        )
        return
    }
    Column(Modifier.fillMaxSize().testTag("alert-history")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onBack) { Text("Back") }
            AlertOptions(model, entries, onExport)
        }
        Column(
            Modifier.weight(1f).verticalScroll(if (history) historyScroll else currentScroll)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenTitle("Alerts")
            TabRow(selectedTabIndex = if (history) 1 else 0) {
                Tab(selected = !history, onClick = { history = false }, text = { Text("Current") })
                Tab(selected = history, onClick = { history = true }, text = { Text("History") })
            }
            val visible = alertWorkspaceRows(entries, history, model::isAlertCurrent)
            if (visible.isEmpty()) {
                Text(if (history) "No recorded alerts" else "No current alerts available", style = MaterialTheme.typography.titleMedium)
                if (!history) Text("Previous activity is in History.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            visible.forEach { row ->
                val entry = row.entry
                Panel {
                    SettingsRow(
                        row.title,
                        listOfNotNull(
                            row.fault?.let { "${it.code} · ${it.source}" },
                            alertEntryStatus(entry.event, model.isAlertCurrent(entry)),
                            alertRecordedTime(entry.recordedAt)
                        ).joinToString(" · "),
                        onClick = {
                            selectedFaultKey = row.fault?.let { "${it.source}:${it.code}" }
                            selectedId = entry.id
                        }
                    )
                }
            }
            model.alertHistoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            model.clearMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Stable ordering retains the recorded order within each current severity group. */
internal fun alertWorkspaceEntries(
    entries: List<StoredAlert>,
    history: Boolean,
    isCurrent: (StoredAlert) -> Boolean
): List<StoredAlert> = if (history) {
    entries.sortedByDescending { it.recordedAt }
} else {
    entries.filter {
        isCurrent(it) && it.event.lifecycle == AlertLifecycle.Active
    }.sortedByDescending { it.event.severity }
}

/** One row per visible controller/code, without altering the episode or its immutable context. */
internal data class AlertWorkspaceRow(val entry: StoredAlert, val fault: CarFaultUi? = null) {
    val title: String get() = fault?.let { FaultCodeCatalog.lookup(it.code).title } ?: alertTitle(entry.event)
}

internal fun alertWorkspaceRows(
    entries: List<StoredAlert>,
    history: Boolean,
    isCurrent: (StoredAlert) -> Boolean
): List<AlertWorkspaceRow> {
    val rows = alertWorkspaceEntries(entries, history, isCurrent).flatMap { entry ->
        val source = when (entry.event.key) {
            32, 34 -> "Engine"
            33, 35 -> "Transmission"
            else -> null
        }
        val faults = if (source == null) emptyList() else recordedFaultCodes(entry.context).filter { it.source == source }
        if (faults.isEmpty()) listOf(AlertWorkspaceRow(entry)) else faults.map { AlertWorkspaceRow(entry, it) }
    }
    if (history) return rows
    // Code identity is scoped to the observation's vehicle and gauge, independent of event producer.
    return rows.distinctBy { row ->
        row.fault?.let { listOf(row.entry.vehicle, row.entry.gauge, it.source, it.code) }
            ?: listOf(row.entry.vehicle, row.entry.gauge, row.entry.id)
    }
}

internal fun alertRecordedTime(recordedAt: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT
).format(Date(recordedAt))

/** Human-readable state shared by overview, list and detail, independent of severity. */
internal fun alertEntryStatus(event: AlertEvent, current: Boolean): String {
    val state = when {
        event.lifecycle == AlertLifecycle.Resolved -> "Resolved"
        event.lifecycle == AlertLifecycle.Expired -> "Expired"
        !current -> "Last checked"
        event.unavailable -> "Data unavailable · not confirmed resolved"
        event.snoozed -> "Snoozed · active"
        event.acknowledged -> "Acknowledged · active"
        else -> "Active"
    }
    return "${if (event.simulated) "Simulated · " else ""}${event.severity.name} · $state"
}

@Composable
private fun AlertOptions(model: AppViewModel, entries: List<StoredAlert>, onExport: () -> Unit) {
    var options by remember { mutableStateOf(false) }
    var delete by rememberSaveable(model.profileCollection.activeId) { mutableStateOf(false) }
    Box {
        TextButton({ options = true }) { Text("Options") }
        DropdownMenu(options, { options = false }) {
            DropdownMenuItem(text = { Text("Save report") }, onClick = {
                options = false
                onExport()
            })
            if (model.clearStatus?.supported == true) {
                DropdownMenuItem(text = { Text("Clear engine codes") }, onClick = {
                    options = false
                    model.prepareCodeClear()
                })
            }
            DropdownMenuItem(
                text = { Text("Delete history") },
                enabled = entries.any { !it.current && !it.pinned },
                onClick = {
                    options = false
                    delete = true
                }
            )
        }
    }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete local history?") },
            text = { Text("Inactive reports that have not been kept will be removed from this phone. Active alerts and vehicle fault codes are retained.") },
            confirmButton = {
                TextButton({
                    delete = false
                    model.clearAlertHistory()
                }) { Text("Delete history") }
            },
            dismissButton = { TextButton({ delete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FullScreenAlertDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) { content() }
    }
}

/** Global banners and notification destinations use the same projected detail. */
@Composable
fun AlertContextSheet(model: AppViewModel, onExport: () -> Unit = {}) {
    val selectedId = model.selectedAlertId ?: return
    val raw = model.alertHistory.firstOrNull { it.id == selectedId }
        ?: model.notificationAlert?.takeIf { it.id == selectedId }
    val entry = raw?.let(model::visibleAlertEntry)
    val vehicle = model.profileCollection.activeId
    val openedVehicle = remember(selectedId) { vehicle }
    LaunchedEffect(vehicle, openedVehicle) {
        if (vehicle != openedVehicle) model.selectedAlertId = null
    }
    FullScreenAlertDialog({ model.selectedAlertId = null }) {
        if (entry == null) {
            CarAlertWorkspace(model, onExport, { model.selectedAlertId = null })
        } else {
            CarAlertDetail(model, entry, onBack = { model.selectedAlertId = null }, onExport = onExport)
        }
    }
}

@Composable
fun CarAlertDetail(
    model: AppViewModel,
    entry: StoredAlert,
    onBack: () -> Unit,
    onExport: (() -> Unit)? = null,
    selectedFault: CarFaultUi? = null
) {
    var options by remember(entry.id) { mutableStateOf(false) }
    val current = model.isAlertCurrent(entry)
    val system = model.displaySettings?.takeIf { it.version >= 2 }?.units
        ?: model.presentationPreferences.measurementSystem
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().testTag("alert-context")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onBack) { Text("Back") }
            Box {
                TextButton({ options = true }) { Text("Options") }
                DropdownMenu(options, { options = false }) {
                    onExport?.let { export ->
                        DropdownMenuItem(text = { Text("Save report") }, onClick = {
                            options = false
                            export()
                        })
                    }
                    DropdownMenuItem(text = { Text(if (entry.pinned) "Unpin report" else "Keep report") }, onClick = {
                        options = false
                        model.pinAlert(entry, !entry.pinned)
                    })
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (entry.vehicle != model.profileCollection.activeId) {
                Text(model.profileCollection.profiles.firstOrNull { it.id == entry.vehicle }?.name ?: "Recorded vehicle", style = MaterialTheme.typography.labelLarge)
            }
            AlertDetailContent(entry, current, system, selectedFault)
            if (current && entry.vehicle == model.profileCollection.activeId && entry.event.lifecycle == AlertLifecycle.Active) {
                PrimaryAction("Got it", { model.acknowledgeAlert(entry) }, enabled = !entry.event.acknowledged)
                if (entry.event.severity < AlertSeverity.Critical) {
                    OutlinedButton({ model.acknowledgeAlert(entry, true) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Snooze 5 min")
                    }
                }
            }
        }
    }
}

/** Receives visible evidence only; the raw repository and capture windows stay untouched. */
@Composable
internal fun AlertDetailContent(entry: StoredAlert, current: Boolean, system: MeasurementSystem, selectedFault: CarFaultUi? = null) {
    val event = entry.event
    val faults = recordedFaultCodes(entry.context)
    val selected = selectedFault?.let { requested -> faults.firstOrNull { it.source == requested.source && it.code == requested.code } }
    ScreenTitle(if (selected == null) alertTitle(event) else "Vehicle fault")
    Text(alertEntryStatus(event, current), style = MaterialTheme.typography.titleMedium)
    Text("Recorded ${alertRecordedTime(entry.recordedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    when {
        event.simulated -> Text("Simulated alert. This does not establish a vehicle fault.")
        event.lifecycle == AlertLifecycle.Resolved -> Text("Returned to normal, confirmed by a fresh observation.")
        event.lifecycle == AlertLifecycle.Expired -> Text("Notice expired.")
        !current || event.unavailable -> Text("Current status unavailable. This alert has not been confirmed resolved.")
    }
    val observation = entry.context.optJSONObject("entryEvent")?.let(::alertFromJson) ?: event
    if (observation.key < 32 && observation.unit != "codes" && observation.value.isFinite()) {
        Panel {
            SectionTitle("At alert")
            Text(alertContextReading(observation.value.toDouble(), observation.unit, system), style = MaterialTheme.typography.headlineMedium)
            if (observation.limit.isFinite()) {
                Text("Alert limit: ${alertContextReading(observation.limit.toDouble(), observation.unit, system)}")
            }
            Text(alertRecordedTime(entry.recordedAt), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (event.key >= 36 && event.unit.isNotBlank()) Text(event.unit)
    (selected?.let(::listOf) ?: faults).forEach { fault -> Panel { FaultCodeExplanation(fault, recorded = true) } }
    if (event.key in 32..35 && faults.isEmpty()) {
        Text("A vehicle warning was recorded. Code information was unavailable at the time.")
    }
}

@Composable
fun GlobalAlertBanner(model: AppViewModel, criticalOnly: Boolean = false) {
    val entry = model.visibleAlertHistory().filter {
        it.vehicle == model.profileCollection.activeId && model.isAlertCurrent(it) &&
            it.event.lifecycle == AlertLifecycle.Active && (!criticalOnly || it.event.severity == AlertSeverity.Critical)
    }.maxByOrNull { it.event.severity } ?: return
    Surface(
        color = if (entry.event.severity == AlertSeverity.Critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TextButton({ model.selectedAlertId = entry.id }) {
            Text("${entry.event.severity.name} · ${alertTitle(entry.event)}${if (entry.event.unavailable) " · unavailable" else ""}")
        }
    }
}

@Composable
fun ClearCodeConfirmation(model: AppViewModel) {
    if (!model.clearConfirmationOpen) return
    var parked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {
            model.clearConfirmationOpen = false
        },
        title = { Text("Clear engine codes?") },
        text = {
            DialogContent {
                Text(
                    "A diagnostic report is saved first. Clearing removes engine emissions codes and may erase freeze-frame data and reset readiness. It does not repair the cause. Permanent codes may remain."
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        parked,
                        role = Role.Checkbox,
                        onValueChange = {
                            parked = it
                        }
                    ),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Checkbox(parked, null)
                    Text("Parked, ignition on, engine off")
                }
            }
        },
        confirmButton = {
            TextButton(model::confirmCodeClear, enabled = parked) { Text("Clear engine codes") }
        },
        dismissButton = { TextButton({ model.clearConfirmationOpen = false }) { Text("Cancel") } }
    )
}
