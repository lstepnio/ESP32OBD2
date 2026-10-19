package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.MeasurementUnits
import com.lstepnio.egauge.gearPositions

/** Context keeps canonical storage intact and uses the same units as the dashboard. */
fun alertContextReading(value: Double, unit: String, system: MeasurementSystem): String {
    if (!value.isFinite()) return "Unavailable"
    if (unit == "gear") return gearPositions[value] ?: "Unavailable"
    return listOf(MeasurementUnits.displayValue(value.toString(), unit, system),
        MeasurementUnits.label(unit, system)).filter { it.isNotBlank() }.joinToString(" ")
}
