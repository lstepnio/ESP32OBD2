package com.lstepnio.egauge.ui.car

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.alertContextReading

@Composable
internal fun IdleCoolantNotice(limitC: Double?, system: MeasurementSystem = MeasurementSystem.Metric) {
    if (limitC == null) {
        StatusCard(StatusUi("Set a coolant limit", "Add a high-temperature Coolant alert, then send your setup. Raised idle cannot start without it.", StatusTone.Warning))
    } else {
        Text("Raised idle stops at ${alertContextReading(limitC, "°C", system)}; missing coolant limits the session to one minute, started on the gauge.", style = MaterialTheme.typography.bodySmall)
    }
}
