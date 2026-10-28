package com.lstepnio.egauge.core.designsystem

import androidx.compose.ui.graphics.Color

/** Project palette. The explicit position text remains the meaning of every gear value. */
fun gearPositionColor(position: String): Color = when (position) {
    "P" -> EGaugeTokens.Gauge.gearPark
    "N" -> EGaugeTokens.Gauge.gearNeutral
    "D", "1", "2", "3", "4", "5", "6", "7", "8" -> EGaugeTokens.Gauge.gearDrive
    "R" -> EGaugeTokens.Gauge.gearReverse
    else -> EGaugeTokens.Gauge.gearUnknown
}

internal fun readingColor(gear: Boolean, value: String, condition: PreviewCondition, fallback: Color): Color = when (condition) {
    PreviewCondition.Critical -> EGaugeTokens.Dark.critical
    PreviewCondition.Warning -> EGaugeTokens.Dark.warning
    PreviewCondition.Normal -> if (gear) gearPositionColor(value) else fallback
    else -> fallback
}
