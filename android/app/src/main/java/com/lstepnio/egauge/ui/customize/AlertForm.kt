package com.lstepnio.egauge.ui.customize

import com.lstepnio.egauge.AlertDirection
import com.lstepnio.egauge.GaugeAlertDraft
import com.lstepnio.egauge.ui.state.AlertUi

/** A local, unsaved form. Thresholds are deliberately blank for a new alert. */
data class AlertForm(
    val direction: AlertDirection = AlertDirection.Above,
    val warning: String = "", val critical: String = "", val reset: String = "0",
    val trigger: String = "1", val clear: String = "2",
) {
    fun errors(range: IntRange, maxReset: Int = 20): Map<String, String> = buildMap {
        val warn = warning.toIntOrNull(); val urgent = critical.toIntOrNull(); val margin = reset.toIntOrNull()
        if (warn == null || warn !in range) put("warning", "Enter a whole number from ${range.first} to ${range.last}.")
        if (urgent == null || urgent !in range) put("critical", "Enter a whole number from ${range.first} to ${range.last}.")
        if (warn != null && urgent != null &&
            (if (direction == AlertDirection.Above) urgent <= warn else urgent >= warn)) {
            put("critical", "Set critical ${if (direction == AlertDirection.Above) "higher" else "lower"} than warning.")
        }
        if (margin == null || margin !in 0..maxReset) put("reset", "Enter a reset distance from 0 to $maxReset.")
        else if (warn != null && urgent != null && margin >= kotlin.math.abs(urgent.toLong() - warn.toLong()))
            put("reset", "Use a reset distance smaller than the gap between your limits.")
        if (secondsToMillis(trigger) == null) put("trigger", "Enter a delay from 0 to 60 seconds, with up to 3 decimal places.")
        if (secondsToMillis(clear) == null) put("clear", "Enter a delay from 0 to 60 seconds, with up to 3 decimal places.")
    }

    fun saved(readingId: String, range: IntRange, original: AlertUi?, maxReset: Int = 20): GaugeAlertDraft? {
        if (errors(range, maxReset).isNotEmpty()) return null
        return GaugeAlertDraft(original?.id ?: "alert.$readingId", readingId, direction,
            warning.toInt(), critical.toInt(), reset.toInt(), requireNotNull(secondsToMillis(trigger)),
            requireNotNull(secondsToMillis(clear)), original?.priority ?: 8)
    }

    companion object {
        fun from(alert: AlertUi?) = alert?.let {
            AlertForm(if (it.direction == "below") AlertDirection.Below else AlertDirection.Above,
                it.warning.toString(), it.critical.toString(), it.resetMargin.toString(),
                it.triggerSeconds.toString(), it.clearSeconds.toString())
        } ?: AlertForm()

        fun secondsToMillis(value: String): Int? = try {
            value.toBigDecimal().movePointRight(3).intValueExact().takeIf { it in 0..60000 }
        } catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }
    }
}
