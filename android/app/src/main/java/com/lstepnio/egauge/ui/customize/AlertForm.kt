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
    fun errors(range: ClosedFloatingPointRange<Double>, maxReset: Double = range.endInclusive - range.start): Map<String, String> = buildMap {
        val warn = warning.toDoubleOrNull()?.takeIf { it.isFinite() }; val urgent = critical.toDoubleOrNull()?.takeIf { it.isFinite() }; val margin = reset.toDoubleOrNull()?.takeIf { it.isFinite() }
        if (warn == null || warn !in range) put("warning", "Enter a number from ${com.lstepnio.egauge.MeasurementUnits.format(range.start)} to ${com.lstepnio.egauge.MeasurementUnits.format(range.endInclusive)}.")
        if (urgent == null || urgent !in range) put("critical", "Enter a number from ${com.lstepnio.egauge.MeasurementUnits.format(range.start)} to ${com.lstepnio.egauge.MeasurementUnits.format(range.endInclusive)}.")
        if (warn != null && urgent != null &&
            direction != AlertDirection.Equals && (if (direction == AlertDirection.Above) urgent <= warn else urgent >= warn)) {
            put("critical", "Set critical ${if (direction == AlertDirection.Above) "higher" else "lower"} than warning.")
        }
        if (margin == null || margin < 0 || margin > maxReset) put("reset", "Enter a reset distance from 0 to $maxReset.")
        else if (direction != AlertDirection.Equals && warn != null && urgent != null && margin >= kotlin.math.abs(urgent - warn))
            put("reset", "Use a reset distance smaller than the gap between your limits.")
        if (direction != AlertDirection.Equals && warn != null && urgent != null && margin != null) {
            val release = if (direction == AlertDirection.Above) warn - margin else warn + margin
            if (release !in range) put("reset", "Keep the reset distance within this reading's range.")
        }
        if (direction == AlertDirection.Equals) {
            if (warn !in com.lstepnio.egauge.gearPositions) put("warning", "Choose a gear position.")
            if (urgent !in com.lstepnio.egauge.gearPositions) put("critical", "Choose a gear position.")
            if (margin != 0.0) put("reset", "Gear alerts have no reset distance.")
        }
        if (secondsToMillis(trigger) == null) put("trigger", "Enter a delay from 0 to 60 seconds, with up to 3 decimal places.")
        if (secondsToMillis(clear) == null) put("clear", "Enter a delay from 0 to 60 seconds, with up to 3 decimal places.")
    }

    fun saved(readingId: String, range: ClosedFloatingPointRange<Double>, original: AlertUi?, maxReset: Double = range.endInclusive - range.start): GaugeAlertDraft? {
        if (errors(range, maxReset).isNotEmpty()) return null
        return GaugeAlertDraft(original?.id ?: "alert.$readingId", readingId, direction,
            warning.toDouble(), critical.toDouble(), reset.toDouble(), requireNotNull(secondsToMillis(trigger)),
            requireNotNull(secondsToMillis(clear)), original?.priority ?: 8)
    }

    fun errors(range: IntRange, maxReset: Int = 20): Map<String, String> =
        errors(range.first.toDouble()..range.last.toDouble(), maxReset.toDouble()) + buildMap {
            listOf("warning" to warning, "critical" to critical, "reset" to reset).forEach { (key, text) ->
                if (text.toIntOrNull() == null) put(key, "Enter a whole number.")
            }
        }
    fun saved(readingId: String, range: IntRange, original: AlertUi?, maxReset: Int = 20): GaugeAlertDraft? =
        if (errors(range, maxReset).isNotEmpty()) null else
            saved(readingId, range.first.toDouble()..range.last.toDouble(), original, maxReset.toDouble())

    companion object {
        fun from(alert: AlertUi?) = alert?.let {
            AlertForm(AlertDirection.entries.single { direction -> direction.name.equals(it.direction, true) },
                com.lstepnio.egauge.alertNumber(it.warning), com.lstepnio.egauge.alertNumber(it.critical), com.lstepnio.egauge.alertNumber(it.resetMargin),
                it.triggerSeconds.toString(), it.clearSeconds.toString())
        } ?: AlertForm()

        fun secondsToMillis(value: String): Int? = try {
            value.toBigDecimal().movePointRight(3).intValueExact().takeIf { it in 0..60000 }
        } catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }
    }
}
