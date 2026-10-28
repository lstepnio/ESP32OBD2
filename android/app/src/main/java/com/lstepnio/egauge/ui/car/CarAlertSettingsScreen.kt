package com.lstepnio.egauge.ui.car

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.customize.*
import com.lstepnio.egauge.ui.state.*

/** Car and Gauge use the same alert editor, units, validation and durable setup owner. */
@Composable
fun CarAlertSettingsScreen(
    model: AppViewModel,
    state: CustomizeUiState,
    onBack: () -> Unit,
    onPhoneAlerts: (Boolean) -> Unit,
    onReview: () -> Unit
) {
    var readingId by rememberSaveable(model.profileCollection.activeId) { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable(model.profileCollection.activeId) { mutableStateOf(false) }
    val actions = CustomizeActions(
        {}, {}, {}, {}, {}, { _, _ -> }, {}, { alert -> model.saveCarAlert(alert) { readingId = null } },
        model::removeAlert, model::checkGaugeForReview, model::sendNumericConfiguration, {}
    )
    val reading = state.readings.firstOrNull { it.id == readingId }
    BackHandler(readingId != null || choosing) {
        readingId = null
        choosing = false
    }
    if (reading != null) {
        AlertEditor(state, reading, state.alerts.firstOrNull { it.readingId == reading.id }, { readingId = null }, actions, saveLabel = if (model.canApplyCarEdit) "Save to gauge" else "Save on phone", closeOnSave = false)
        return
    }
    ScreenContent(scrollKey = model.profileCollection.activeId) {
        ScreenTitle("Alert settings", onBack)
        Panel {
            if (state.alerts.isEmpty()) Text("No reading alerts yet", style = MaterialTheme.typography.titleMedium)
            state.alerts.forEach { alert ->
                SettingsRow(
                    alert.readingName,
                    if (!alert.enabled) {
                        "Off"
                    } else {
                        "${if (alert.direction == "below") {
                            "Below"
                        } else if (alert.direction == "equals") {
                            "Matches"
                        } else {
                            "Above"
                        }} ${MeasurementUnits.format(alert.warning)} ${alert.unit}"
                    },
                    state.editingEnabled
                ) { readingId = alert.readingId }
            }
            PrimaryAction("Add alert", { choosing = true }, enabled = state.editingEnabled && state.alerts.size < 32)
        }
        Panel {
            PreferenceToggle("Phone notifications", model.phoneAlertsEnabled, onPhoneAlerts)
            if (model.phoneAlertsEnabled && !model.alertMonitoring) Text("Notifications are enabled. Monitoring starts when a gauge is available.", style = MaterialTheme.typography.bodyMedium)
        }
        if (!sameSettings(model.sentDraft, model.transmittedDraft)) {
            Panel {
                Text("Changes saved on phone", style = MaterialTheme.typography.titleMedium)
                PrimaryAction("Review and send", onReview, enabled = !state.busy && state.found)
            }
        }
    }
    if (choosing) {
        ReadingPicker(
            state.readings,
            "Choose a reading",
            null,
            state.editingEnabled,
            { choosing = false },
            {
                readingId = it
                choosing = false
            }
        )
    }
}
