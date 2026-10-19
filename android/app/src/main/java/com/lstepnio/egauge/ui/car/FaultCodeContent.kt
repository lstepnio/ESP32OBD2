package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.FaultCodeCatalog
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.CarFaultUi

@Composable
fun FaultCodeRow(fault: CarFaultUi, recorded: Boolean = false, onClick: () -> Unit) {
    SettingsRow(
        FaultCodeCatalog.lookup(fault.code).title,
        "${fault.code} · ${fault.source}${if (recorded) {
            " · Saved with alert"
        } else if (fault.current) {
            ""
        } else {
            " · Last checked"
        }}",
        onClick = onClick
    )
}

/** Shared by live vehicle checks and immutable alert context; no refresh or ECU action. */
@Composable
fun FaultCodeExplanation(fault: CarFaultUi, recorded: Boolean = false) {
    val meaning = FaultCodeCatalog.lookup(fault.code)
    Column(Modifier.fillMaxWidth().testTag("fault-explanation"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(meaning.title)
        Text("${fault.code} · ${fault.source}", style = MaterialTheme.typography.titleMedium)
        if (recorded) {
            Text("Saved with this alert. This is not a live fault check.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!fault.current) {
            Text("Last checked. This code has not been confirmed cleared.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("What it means", style = MaterialTheme.typography.titleSmall)
        Text(meaning.explanation)
        if (fault.categoryNames.isNotEmpty()) {
            Text(
                fault.categoryNames.map { category ->
                    when (category) {
                        "Pending" -> "Awaiting confirmation by the car."
                        "Permanent" -> "Kept by the car until its own checks confirm the fault is gone."
                        "Stored" -> "Recorded by the car. This does not by itself establish that the fault is still happening."
                        else -> "Recorded with this check."
                    }
                }.distinct().joinToString(" "),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text("Next step", style = MaterialTheme.typography.titleSmall)
        Text(meaning.nextStep)
    }
}

@Composable
fun SavedFaultCodes(faults: List<CarFaultUi>, resetKey: String) {
    var selectedCode by rememberSaveable(resetKey) { mutableStateOf<String?>(null) }
    Text("Codes saved with this alert", style = MaterialTheme.typography.titleSmall)
    Text("This is the recorded check, not the car's current status.", style = MaterialTheme.typography.bodySmall)
    if (faults.isEmpty()) Text("Code information was not available when this alert was saved.")
    val selected = faults.firstOrNull { "${it.source}:${it.code}" == selectedCode }
    if (selected != null) {
        TextButton({ selectedCode = null }) { Text("Back to saved codes") }
        FaultCodeExplanation(selected, recorded = true)
    } else {
        faults.forEach { fault ->
            FaultCodeRow(fault, recorded = true) { selectedCode = "${fault.source}:${fault.code}" }
        }
    }
}
