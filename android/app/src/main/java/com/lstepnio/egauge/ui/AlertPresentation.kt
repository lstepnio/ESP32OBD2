package com.lstepnio.egauge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import java.util.Date

/** Keep the main Car page concise; history and report actions share one focused sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryPanel(model: AppViewModel, onExport: () -> Unit) {
    val vehicle = model.profileCollection.activeId
    val entries = model.alertHistory.filter { it.vehicle == vehicle }
    var historyOpen by rememberSaveable(vehicle) { mutableStateOf(false) }
    val active = entries.filter { model.isAlertCurrent(it) && it.event.lifecycle == AlertLifecycle.Active }
    val recent = (active.sortedByDescending { it.event.severity } + entries).distinctBy { it.id }.take(3)
    Panel {
        SectionTitle("Alerts")
        if (model.alertFrameworkSupported == false) Text("Update the gauge to use alert history.")
        else if (entries.isEmpty()) Text("No recorded alerts", color = MaterialTheme.colorScheme.onSurfaceVariant)
        recent.forEach { entry ->
            SettingsRow(entry.event.label, alertEntryStatus(entry.event, model.isAlertCurrent(entry)),
                onClick = { model.selectedAlertId = entry.id })
        }
        model.alertHistoryError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        SettingsRow("Alert history", if (active.size > 3) "${active.size} active · ${entries.size} recorded"
            else if (entries.isEmpty()) "Reports and saved alerts" else "${entries.size} recorded",
            onClick = { historyOpen = true })
    }
    if (historyOpen) ModalBottomSheet(onDismissRequest = { historyOpen = false }, modifier = Modifier.testTag("alert-history")) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
            DialogContent { AlertHistoryContent(model, onExport, { historyOpen = false }) }
        }
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
        event.acknowledged -> "Acknowledged · active"
        else -> "Active"
    }
    return "${if (event.simulated) "Simulated · " else ""}${event.severity.name} · $state"
}

@Composable
private fun AlertHistoryContent(model: AppViewModel, onExport: () -> Unit, onSelect: () -> Unit = {}) {
    val entries=model.alertHistory.filter { it.vehicle==model.profileCollection.activeId }
    var all by rememberSaveable(model.profileCollection.activeId) { mutableStateOf(false) }
    var delete by rememberSaveable(model.profileCollection.activeId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Alert history")
        if(model.alertFrameworkSupported==false)Text("Update the gauge to use alert history.")
        else if(entries.isEmpty())Text("No recorded alerts")
        model.alertHistoryError?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        entries.take(if(all)500 else 6).forEach { entry ->
            val e=entry.event
            TextButton({ onSelect(); model.selectedAlertId=entry.id }) {
                Column(Modifier.fillMaxWidth()) {
                    Text(e.label)
                    Text(alertEntryStatus(e, model.isAlertCurrent(entry)), style=MaterialTheme.typography.bodySmall)

                }
            }
        }
        if(entries.size>6)TextButton({ all=!all }) { Text(if(all)"Show recent" else "Show all history") }
        model.clearMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        if(model.clearStatus?.supported==true)TextButton({ onSelect(); model.prepareCodeClear() }) { Text("Clear engine codes") }
        SettingsRow("Save report", onClick = onExport)
        SettingsRow("Delete history", enabled = entries.any { !it.current && !it.pinned }, onClick = { delete = true })
    }
    if(delete)AlertDialog(onDismissRequest={ delete=false },title={ Text("Delete local history?") },text={ Text("Unpinned inactive reports for this vehicle will be removed from this phone. Active alerts and vehicle fault codes are retained.") },confirmButton={ TextButton({ delete=false;model.clearAlertHistory() }) { Text("Delete history") } },dismissButton={ TextButton({ delete=false }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertContextSheet(model: AppViewModel) {
    val entry=model.alertHistory.firstOrNull { it.id==model.selectedAlertId }?:model.notificationAlert?.takeIf { it.id==model.selectedAlertId }?:return
    ModalBottomSheet(onDismissRequest={ model.selectedAlertId=null }, modifier = Modifier.testTag("alert-context")) {
        DialogContent {
            val e=entry.event
            SectionTitle(e.label)
            Text("${e.severity.name} · ${e.producer}")
            Text("Recorded ${Date(entry.recordedAt)}",style=MaterialTheme.typography.bodySmall)
            Text(when {
                e.simulated -> "Simulated alert. This does not establish a vehicle fault."
                e.lifecycle==AlertLifecycle.Resolved -> "Resolved by a fresh observation"
                e.lifecycle==AlertLifecycle.Expired -> "Notice expired"
                !model.isAlertCurrent(entry) -> "Last checked. Current status has not been established."
                e.unavailable -> "Data is unavailable. This alert has not been confirmed resolved."
                else -> "Active${if(e.acknowledged)" · acknowledged" else ""}"
            })
            val observation=entry.context.optJSONObject("entryEvent")?.let(::alertFromJson)?:e
            if(observation.key<36 && observation.unit!="codes" && observation.value.isFinite()) {
                Text("At observation: ${MeasurementUnits.displayValue(observation.value.toString(),observation.unit,model.presentationPreferences.measurementSystem)} ${MeasurementUnits.label(observation.unit,model.presentationPreferences.measurementSystem)}")
                if(observation.key<32 && observation.limit.isFinite())Text("Configured boundary: ${MeasurementUnits.displayValue(observation.limit.toString(),observation.unit,model.presentationPreferences.measurementSystem)} ${MeasurementUnits.label(observation.unit,model.presentationPreferences.measurementSystem)}")
            }
            if(e.key>=36 && e.unit.isNotBlank())Text(e.unit)
            if(entry.context.optBoolean("coverageGap"))Text("Some events were unavailable during disconnection or restart.")
            val diagnostics=entry.context.optJSONObject("diagnostics")
            diagnostics?.keys()?.forEach { source ->
                val data=diagnostics.getJSONObject(source)
                Text(if(source=="TCM")"Transmission" else "Engine",style=MaterialTheme.typography.titleSmall)
                val categories=data.optJSONArray("categories")
                if(categories!=null)for(i in 0 until categories.length()) {
                    val category=categories.getJSONObject(i)
                    Text("${category.getString("name")}: ${category.getJSONArray("codes").let { if(it.length()==0)category.getString("availability") else it.toString() }}")
                }
            }
            val capture=entry.context.optJSONObject("capture")
            Text(when { capture==null -> "Sensor window is pending or unavailable."
                !capture.optBoolean("available") -> "Sensor window unavailable on the gauge."
                !capture.optBoolean("complete") || capture.optBoolean("partial") -> "Partial sensor window captured"
                else -> "Sensor window captured" },style=MaterialTheme.typography.bodySmall)
            capture?.optJSONArray("samples")?.let { samples ->
                val groups=(0 until samples.length()).map { samples.getJSONObject(it) }.filter { it.optBoolean("valid") }.groupBy { it.getInt("pidIndex") }
                val definitions=entry.context.optJSONArray("definitions")
                groups.forEach { (pid,values) ->
                    val definition=definitions?.optJSONObject(pid)
                    Text("${definition?.optString("name")?:"Reading ${pid+1}"}: ${values.first().optDouble("value")} → ${values.last().optDouble("value")} ${definition?.optString("unit").orEmpty()}",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(model.isAlertCurrent(entry) && entry.vehicle==model.profileCollection.activeId && e.lifecycle==AlertLifecycle.Active) {
                Row {
                    TextButton({ model.acknowledgeAlert(entry) }) { Text("Acknowledge") }
                    if(e.severity<AlertSeverity.Critical)TextButton({ model.acknowledgeAlert(entry,true) }) { Text("Snooze 5 min") }
                }
                Text("Acknowledgment changes attention only. It does not clear fault codes.",style=MaterialTheme.typography.bodySmall)
            }
            TextButton({ model.pinAlert(entry,!entry.pinned) }) { Text(if(entry.pinned)"Unpin report" else "Keep report") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun GlobalAlertBanner(model: AppViewModel) {
    val entry=model.alertHistory.filter { it.vehicle==model.profileCollection.activeId && model.isAlertCurrent(it) &&
        it.event.lifecycle==AlertLifecycle.Active }.maxByOrNull { it.event.severity }?:return
    Surface(color=if(entry.event.severity==AlertSeverity.Critical)MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
        TextButton({ model.selectedAlertId=entry.id }) { Text("${entry.event.severity.name} · ${entry.event.label}${if(entry.event.unavailable)" · unavailable" else ""}") }
    }
}

@Composable
fun ClearCodeConfirmation(model: AppViewModel) {
    if(!model.clearConfirmationOpen)return
    var parked by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest={ model.clearConfirmationOpen=false },title={ Text("Clear engine codes?") },text={
        DialogContent {
            Text("A diagnostic report is saved first. Clearing removes engine emissions codes and may erase freeze-frame data and reset readiness. It does not repair the cause. Permanent codes may remain.")
            Row { Checkbox(parked,{ parked=it });Text("Parked, ignition on, engine off") }
        }
    },confirmButton={ TextButton(model::confirmCodeClear,enabled=parked) { Text("Clear engine codes") } },
        dismissButton={ TextButton({ model.clearConfirmationOpen=false }) { Text("Cancel") } })
}
