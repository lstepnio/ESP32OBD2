package com.lstepnio.egauge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import java.util.Date

@Composable
fun AlertHistoryPanel(model: AppViewModel, onExport: () -> Unit) {
    val entries=model.alertHistory.filter { it.vehicle==model.profileCollection.activeId }
    var all by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    Panel {
        SectionTitle("Alerts")
        if(model.alertFrameworkSupported==false)Text("Update the gauge to use alert history.")
        else if(entries.isEmpty())Text("No recorded alerts")
        model.alertHistoryError?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        entries.take(if(all)500 else 6).forEach { entry ->
            val e=entry.event
            TextButton({ model.selectedAlertId=entry.id }) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${e.severity.name} · ${e.label}")
                    Text(if(e.simulated)"Simulated" else if(e.lifecycle!=AlertLifecycle.Active)e.lifecycle.name else if(!model.isAlertCurrent(entry))"Last checked" else if(e.unavailable)"Data unavailable" else if(e.snoozed)"Snoozed · active" else if(e.acknowledged)"Acknowledged · active" else e.lifecycle.name,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(entries.size>6)TextButton({ all=!all }) { Text(if(all)"Show recent" else "Show all history") }
        model.clearMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        if(model.clearStatus?.supported==true)TextButton(model::prepareCodeClear) { Text("Clear engine codes") }
        Row {
            TextButton(onExport) { Text("Save report") }
            TextButton({ delete=true },enabled=entries.any { !it.current && !it.pinned }) { Text("Delete history") }
        }
    }
    if(delete)AlertDialog(onDismissRequest={ delete=false },title={ Text("Delete local history?") },text={ Text("Unpinned inactive reports for this vehicle will be removed from this phone. Active alerts and vehicle fault codes are retained.") },confirmButton={ TextButton({ delete=false;model.clearAlertHistory() }) { Text("Delete history") } },dismissButton={ TextButton({ delete=false }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertContextSheet(model: AppViewModel) {
    val entry=model.alertHistory.firstOrNull { it.id==model.selectedAlertId }?:model.notificationAlert?.takeIf { it.id==model.selectedAlertId }?:return
    ModalBottomSheet(onDismissRequest={ model.selectedAlertId=null }) {
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
