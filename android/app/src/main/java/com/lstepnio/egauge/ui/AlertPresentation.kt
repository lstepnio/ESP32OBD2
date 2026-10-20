package com.lstepnio.egauge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.car.SavedFaultCodes
import com.lstepnio.egauge.ui.state.alertContextReading
import com.lstepnio.egauge.ui.state.alertNotificationText
import com.lstepnio.egauge.ui.state.alertTitle
import com.lstepnio.egauge.ui.state.recordedFaultCodes
import java.text.DateFormat
import java.util.Date

/** Model boundary: UI fixtures use the same cards without a Bluetooth or storage owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryPanel(model: AppViewModel, onExport: () -> Unit, onManage: () -> Unit = {}) {
    val vehicle = model.profileCollection.activeId
    var historyOpen by rememberSaveable(vehicle) { mutableStateOf(false) }
    val system = model.displaySettings?.takeIf { it.version >= 2 }?.units ?: model.presentationPreferences.measurementSystem
    val entries = model.alertHistory.filter { it.vehicle == vehicle }
    val items = entries.filterNot { model.isAlertCurrent(it) && model.isDiagnosticAlertFiltered(it.event) }
        .map { AlertListItem(it, model.isAlertCurrent(it), system, model.operation.stage != OperationStage.IDLE && !model.operation.terminal) }
    AlertsCard(
        items,
        model.alertFrameworkSupported != false,
        model.alertHistoryError,
        { model.selectedAlertId = it.id },
        { model.acknowledgeAlert(it) },
        { model.acknowledgeAlert(it, true) },
        { historyOpen = true },
        onManage
    )
    if (historyOpen) {
        ModalBottomSheet(
            onDismissRequest = { historyOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("alert-history")
        ) {
            AlertHistoryContent(
                items,
                entries.any { !it.current && !it.pinned },
                model.alertHistoryError,
                {
                    historyOpen = false
                    model.selectedAlertId = it.id
                },
                onExport,
                model::clearAlertHistory
            )
        }
    }
}

internal data class AlertListItem(val entry: StoredAlert, val current: Boolean, val system: MeasurementSystem, val busy: Boolean = false) {
    val active get() = current && entry.event.lifecycle == AlertLifecycle.Active
    val needsAttention get() = active && !entry.event.acknowledged && !entry.event.snoozed
    val offersMarkSeen get() = active && !entry.event.simulated && !entry.event.acknowledged && !entry.event.snoozed
    val canMarkSeen get() = offersMarkSeen && !busy
    val offersSnooze get() = active && !entry.event.simulated && !entry.event.snoozed && entry.event.severity < AlertSeverity.Critical
    val canSnooze get() = offersSnooze && !busy
}

/** Active conditions stay on Car. Past episodes have one home in history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlertsCard(
    items: List<AlertListItem>,
    supported: Boolean = true,
    error: String? = null,
    onOpen: (StoredAlert) -> Unit,
    onSeen: (StoredAlert) -> Unit,
    onSnooze: (StoredAlert) -> Unit,
    onHistory: () -> Unit,
    onManage: () -> Unit
) {
    val active = items.filter { it.active }.sortedWith(compareByDescending<AlertListItem> { it.entry.event.severity }.thenByDescending { it.entry.recordedAt })
    Panel {
        SectionTitle("Alerts")
        if (!supported) {
            Text("Update the gauge to use alerts.")
        } else if (active.isEmpty()) {
            Text(
                if (items.any { it.entry.event.lifecycle == AlertLifecycle.Active }) {
                    "Alerts will update when connected."
                } else if (items.isEmpty()) {
                    "No alerts recorded"
                } else {
                    "Past alerts are in history."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        active.take(3).forEach { item ->
            AlertEventRow(item, onOpen)
            AlertAttentionActions(item, onSeen, onSnooze)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onHistory) { Text(if (active.size > 3) "View all alerts (${active.size})" else "Alert history") }
            TextButton(onManage) { Text("Alert rules") }
        }
    }
}

@Composable
private fun AlertEventRow(item: AlertListItem, onOpen: (StoredAlert) -> Unit, showTime: Boolean = false) {
    val entry = item.entry
    val description = alertNotificationText(entry.event, item.system, recordedFaultCodes(entry.context))
    SettingsRow(
        alertTitle(entry.event),
        listOf(description, alertEntryStatus(entry.event, item.current), if (showTime) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.recordedAt)) else "").filter { it.isNotBlank() }.joinToString("\n"),
        onClick = { onOpen(entry) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertAttentionActions(item: AlertListItem, onSeen: (StoredAlert) -> Unit, onSnooze: (StoredAlert) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (item.offersMarkSeen) TextButton({ onSeen(item.entry) }, Modifier.semantics { contentDescription = "Mark ${alertTitle(item.entry.event)} seen" }, enabled = item.canMarkSeen) { Text("Mark seen") }
        if (item.offersSnooze) TextButton({ onSnooze(item.entry) }, Modifier.semantics { contentDescription = "Snooze ${alertTitle(item.entry.event)} for 5 minutes" }, enabled = item.canSnooze) { Text("Snooze 5 min") }
    }
}

/** Human-readable state shared by recent alerts and history, independent of severity. */
internal fun alertEntryStatus(event: AlertEvent, current: Boolean): String {
    val state = when {
        event.lifecycle == AlertLifecycle.Resolved -> "Resolved"
        event.lifecycle == AlertLifecycle.Expired -> "Expired"
        !current -> "Last checked"
        event.unavailable -> "Data unavailable · not confirmed resolved"
        event.snoozed -> "Snoozed · active"
        event.acknowledged -> "Seen · still active"
        else -> "Active"
    }
    return "${if (event.simulated) "Simulated · " else ""}${event.severity.name} · $state"
}

@Composable
internal fun AlertHistoryContent(
    entries: List<AlertListItem>,
    canDelete: Boolean,
    error: String?,
    onOpen: (StoredAlert) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var delete by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Alert history", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            if (entries.isNotEmpty()) {
                Box {
                    TextButton({ menu = true }, Modifier.semantics { contentDescription = "History options" }) { Text("Options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Share report") }, onClick = {
                            menu = false
                            onExport()
                        })
                        DropdownMenuItem(text = { Text("Delete history") }, enabled = canDelete, onClick = {
                            menu = false
                            delete = true
                        })
                    }
                }
            }
        }
        if (entries.isEmpty()) Text("No recorded alerts")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f).testTag("alert-history-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries.sortedByDescending { it.entry.recordedAt }, key = { it.entry.id }) { item ->
                AlertEventRow(item, onOpen, showTime = true)
            }
        }
    }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete history from this phone?") },
            text = { Text("Saved reports that are no longer active will be removed. Kept reports, active alerts and vehicle fault codes stay unchanged.") },
            confirmButton = {
                TextButton({
                    delete = false
                    onDelete()
                }) { Text("Delete history") }
            },
            dismissButton = { TextButton({ delete = false }) { Text("Keep history") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertContextSheet(model: AppViewModel) {
    val entry = model.alertHistory.firstOrNull { it.id == model.selectedAlertId }
        ?: model.notificationAlert?.takeIf { it.id == model.selectedAlertId } ?: return
    val system = model.displaySettings?.takeIf { it.version >= 2 }?.units ?: model.presentationPreferences.measurementSystem
    ModalBottomSheet(
        onDismissRequest = { model.selectedAlertId = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("alert-context")
    ) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
            DialogContent {
                AlertContextContent(
                    AlertListItem(entry, model.isAlertCurrent(entry) && entry.vehicle == model.profileCollection.activeId, system, model.operation.stage != OperationStage.IDLE && !model.operation.terminal),
                    { model.acknowledgeAlert(it) },
                    { model.acknowledgeAlert(it, true) },
                    { model.pinAlert(it, !it.pinned) }
                )
            }
        }
    }
}

@Composable
internal fun AlertContextContent(
    item: AlertListItem,
    onSeen: (StoredAlert) -> Unit,
    onSnooze: (StoredAlert) -> Unit,
    onKeep: (StoredAlert) -> Unit
) {
    val entry = item.entry
    val e = entry.event
    val system = item.system
    var readingsOpen by rememberSaveable(entry.id) { mutableStateOf(false) }
    SectionTitle(alertTitle(e))
    Text(alertEntryStatus(e, item.current), color = if (item.active && e.severity == AlertSeverity.Critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if (e.simulated) Text("Simulated alert. This does not establish a vehicle fault.")
    if (!item.current && e.lifecycle == AlertLifecycle.Active) Text("Last checked. Current status has not been established.")
    if (e.unavailable && e.lifecycle == AlertLifecycle.Active) Text("Data is unavailable. This alert has not been confirmed resolved.")
    val observation = entry.context.optJSONObject("entryEvent")?.let(::alertFromJson) ?: e
    if (observation.key < 32 && observation.unit != "codes" && observation.value.isFinite()) {
        Text(alertContextReading(observation.value.toDouble(), observation.unit, system), style = MaterialTheme.typography.headlineLarge)
        if (observation.limit.isFinite()) Text("Your limit: ${alertContextReading(observation.limit.toDouble(), observation.unit, system)}")
    }
    if (e.key >= 36 && e.unit.isNotBlank()) Text(e.unit)
    AlertAttentionActions(item, onSeen, onSnooze)
    if (item.canMarkSeen || item.canSnooze) Text("Silences attention; the alert stays active until the condition clears.", style = MaterialTheme.typography.bodySmall)
    val recordedCodes = recordedFaultCodes(entry.context)
    if (e.key in 32..35 || recordedCodes.isNotEmpty()) SavedFaultCodes(recordedCodes, entry.id)
    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.recordedAt)), style = MaterialTheme.typography.bodySmall)
    if (entry.context.optBoolean("coverageGap")) Text("Some events were not recorded while disconnected.", style = MaterialTheme.typography.bodySmall)
    if (entry.context.optJSONObject("capture") != null) {
        TextButton({ readingsOpen = !readingsOpen }) { Text(if (readingsOpen) "Hide recorded readings" else "Recorded readings") }
        if (readingsOpen) RecordedAlertReadings(entry, system)
    }
    TextButton({ onKeep(entry) }) { Text(if (entry.pinned) "Remove from kept reports" else "Keep report") }
}

@Composable
private fun RecordedAlertReadings(entry: StoredAlert, system: MeasurementSystem) {
    val capture = entry.context.optJSONObject("capture") ?: return
    Text(
        when {
            !capture.optBoolean("available") -> "No readings were saved with this alert."
            !capture.optBoolean("complete") || capture.optBoolean("partial") -> "Some readings are missing."
            else -> "Readings recorded around this alert."
        },
        style = MaterialTheme.typography.bodySmall
    )
    capture.optJSONArray("samples")?.let { samples ->
        val groups = (0 until samples.length()).map {
            samples.getJSONObject(it)
        }.filter { it.optBoolean("valid") }.groupBy { it.getInt("pidIndex") }
        val definitions = entry.context.optJSONArray("definitions")
        groups.forEach { (pid, values) ->
            val definition = definitions?.optJSONObject(pid)
            Text(
                "${definition?.optString(
                    "name"
                ) ?: "Reading ${pid + 1}"}: ${alertContextReading(
                    values.first().optDouble("value"),
                    definition?.optString("unit").orEmpty(),
                    system
                )} → ${alertContextReading(
                    values.last().optDouble("value"),
                    definition?.optString("unit").orEmpty(),
                    system
                )}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun GlobalAlertBanner(model: AppViewModel) {
    val entry =
        model.alertHistory.filter {
            it.vehicle == model.profileCollection.activeId &&
                model.isAlertCurrent(it) &&
                it.event.lifecycle == AlertLifecycle.Active &&
                !it.event.acknowledged && !it.event.snoozed &&
                !model.isDiagnosticAlertFiltered(it.event)
        }.maxByOrNull { it.event.severity }
            ?: return
    Surface(
        color = if (entry.event.severity ==
            AlertSeverity.Critical
        ) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TextButton({
            model.selectedAlertId = entry.id
        }) {
            Text(
                "${entry.event.severity.name} · ${alertTitle(entry.event)}${if (entry.event.unavailable) " · unavailable" else ""}"
            )
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
