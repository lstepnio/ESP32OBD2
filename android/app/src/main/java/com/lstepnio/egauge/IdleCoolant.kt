package com.lstepnio.egauge

/** Canonical Celsius from the vehicle's existing high-temperature alert, not a second setting. */
fun idleCoolantLimitC(alerts: List<GaugeAlertDraft>): Double? = alerts.filter {
    it.enabled &&
        it.pidId == "coolant" && it.direction == AlertDirection.Above &&
        it.warning.isFinite() && it.critical.isFinite() &&
        it.warning > -40 && it.warning < it.critical && it.critical <= 215
}.minOfOrNull { it.warning }
