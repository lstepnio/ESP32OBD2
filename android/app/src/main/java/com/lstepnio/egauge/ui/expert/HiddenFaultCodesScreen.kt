package com.lstepnio.egauge.ui.expert

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.ExpertUiState

/** The only presentation surface that intentionally names hidden fault codes. */
@Composable
fun HiddenFaultCodesScreen(state: ExpertUiState, actions: ExpertActions, onBack: () -> Unit, onReview: () -> Unit) {
    var submittedRule by rememberSaveable(state.vehicleId) { mutableStateOf<String?>(null) }
    var adding by rememberSaveable(state.vehicleId) { mutableStateOf(false) }
    var code by rememberSaveable(state.vehicleId) { mutableStateOf("") }
    var controller by rememberSaveable(state.vehicleId) { mutableStateOf("ECM") }
    var showRule by remember { mutableStateOf<DiagnosticCodeFilter?>(null) }
    LaunchedEffect(submittedRule, state.diagnosticFilters) {
        if (adding && state.diagnosticFilters.any { "${it.controller}:${it.code}" == submittedRule }) {
            adding = false
            code = ""
            submittedRule = null
        }
    }
    BackHandler { if (adding) adding = false else onBack() }
    ScreenContent(scrollKey = state.vehicleId to adding) {
        ScreenTitle(if (adding) "Hide a fault code" else "Hidden fault codes", { if (adding) adding = false else onBack() })
        if (adding) {
            OutlinedTextField(code, { code = it.trim().uppercase().take(6) }, label = { Text("Code") }, singleLine = true, enabled = state.canEditVehicle)
            Row {
                listOf("ECM" to "Engine", "TCM" to "Transmission").forEach { (id, label) ->
                    FilterChip(controller == id, { controller = id }, enabled = state.canEditVehicle, label = { Text(label) })
                }
            }
            val rule = runCatching { DiagnosticCodeFilters.rule(controller, code) }.getOrNull()
            if (code.isNotEmpty() && rule == null) Text("Enter an exact code, for example P0420.")
            if (rule in state.diagnosticFilters) Text("Already hidden")
            Text("Applies to ${state.vehicleName}. Hidden from eGauge screens, notifications and reports. This does not clear your car's code.")
            PrimaryAction("Hide code", {
                submittedRule = "$controller:$code"
                actions.addCodeFilter(state.vehicleId, controller, code)
            }, enabled = state.canEditVehicle && rule != null && rule !in state.diagnosticFilters && state.diagnosticFilters.size < DiagnosticCodeFilters.LIMIT)
        } else {
            Text("Hidden from eGauge screens, notifications and reports. Your car keeps the codes.")
            Panel {
                if (state.diagnosticFilters.isEmpty()) Text("No hidden codes")
                state.diagnosticFilters.forEach { rule ->
                    SettingsRow(rule.code, if (rule.controller == "TCM") "Transmission" else "Engine", state.canEditVehicle) { showRule = rule }
                }
                PrimaryAction("Hide a code", { adding = true }, enabled = state.canEditVehicle && state.diagnosticFilters.size < DiagnosticCodeFilters.LIMIT)
            }
            if (state.filterTargets.isNotEmpty()) {
                Panel {
                    SectionTitle("Assigned gauges")
                    state.filterTargets.forEach { Text("${it.label} · ${it.value}") }
                    if (state.filterTargets.any { it.value != "Applied" }) PrimaryAction("Review and send", onReview, enabled = state.canRead)
                }
            }
        }
    }
    showRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { showRule = null },
            title = { Text("Show ${rule.code} again?") },
            text = { Text("Matching faults and their history will be visible again. Previous alerts will not send new notifications.") },
            confirmButton = {
                TextButton({
                    actions.removeCodeFilter(state.vehicleId, rule)
                    showRule = null
                }) { Text("Show code") }
            },
            dismissButton = { TextButton({ showRule = null }) { Text("Keep hidden") } }
        )
    }
}
