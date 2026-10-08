package com.lstepnio.egauge

import kotlin.math.roundToInt
import java.util.Locale

enum class MeasurementSystem { Metric, Imperial }

/** Initial temperature convention from Unicode CLDR measurementData (2026-10-06).
 * A saved choice is authoritative; language alone is not a country preference. */
fun preferredMeasurementSystem(savedName: String?, locale: Locale): MeasurementSystem {
    MeasurementSystem.entries.firstOrNull { it.name == savedName }?.let { return it }
    when (locale.getUnicodeLocaleType("mu")) {
        "fahrenhe" -> return MeasurementSystem.Imperial
        "celsius" -> return MeasurementSystem.Metric
    }
    val region = locale.getUnicodeLocaleType("rg")?.takeIf { it.matches(Regex("[a-z]{2}zzzz")) }
        ?.take(2)?.uppercase(Locale.ROOT) ?: locale.country.uppercase(Locale.ROOT)
    return if (region in setOf("US", "BS", "BZ", "KY", "PR", "PW"))
        MeasurementSystem.Imperial else MeasurementSystem.Metric
}

/** Canonical configuration and alert values remain metric. */
object MeasurementUnits {
    fun displayValue(value: String, unit: String, system: MeasurementSystem): String {
        val number = value.replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() } ?: return value
        val precise = value(number, unit, system)
        val converted = if (unit in setOf("°C", "degC", "kph", "km/h", "rpm", "count", "s", "min", "km", "Nm")) precise.roundToInt().toDouble() else precise
        return if ("," in value && converted % 1.0 == 0.0) "%,d".format(converted.toLong()) else format(converted)
    }

    fun format(value: Double): String = alertNumber(java.math.BigDecimal.valueOf(value)
        .setScale(3, java.math.RoundingMode.HALF_UP).toDouble())

    private fun factor(unit: String) = when (unit) {
        "km/h", "kph", "km" -> 0.621371
        "kPa" -> 0.1450377377
        "Pa" -> 0.0001450377377
        "g/s" -> 0.1322773573
        "L/h" -> 0.2641720524
        "Nm" -> 0.7375621493
        "°C", "degC" -> 1.8
        else -> 1.0
    }
    fun value(canonical: Double, unit: String, system: MeasurementSystem): Double =
        if (system == MeasurementSystem.Metric) canonical else canonical * factor(unit) +
            if (unit in setOf("°C", "degC")) 32.0 else 0.0
    fun canonical(displayed: Double, unit: String, system: MeasurementSystem): Double =
        if (system == MeasurementSystem.Metric) displayed else
            (displayed - if (unit in setOf("°C", "degC")) 32.0 else 0.0) / factor(unit)
    fun distance(canonical: Double, unit: String, system: MeasurementSystem): Double =
        canonical * if (system == MeasurementSystem.Imperial) factor(unit) else 1.0
    fun canonicalDistance(displayed: Double, unit: String, system: MeasurementSystem): Double =
        displayed / if (system == MeasurementSystem.Imperial) factor(unit) else 1.0
    fun range(canonical: ClosedFloatingPointRange<Double>, unit: String, system: MeasurementSystem) =
        value(canonical.start, unit, system)..value(canonical.endInclusive, unit, system)

    fun label(unit: String, system: MeasurementSystem): String = when {
        system == MeasurementSystem.Imperial && unit == "°C" -> "°F"
        system == MeasurementSystem.Imperial && unit == "degC" -> "°F"
        system == MeasurementSystem.Imperial && unit == "km/h" -> "mph"
        system == MeasurementSystem.Imperial && unit == "kph" -> "mph"
        system == MeasurementSystem.Imperial && unit == "km" -> "mi"
        system == MeasurementSystem.Imperial && unit in setOf("kPa", "Pa") -> "psi"
        system == MeasurementSystem.Imperial && unit == "g/s" -> "lb/min"
        system == MeasurementSystem.Imperial && unit == "L/h" -> "US gal/h"
        system == MeasurementSystem.Imperial && unit == "Nm" -> "lb·ft"
        else -> if (unit == "deg") "°" else unit
    }

    fun value(canonical: Int, unit: String, system: MeasurementSystem): Int = value(canonical.toDouble(), unit, system).roundToInt()
    fun range(canonical: IntRange, unit: String, system: MeasurementSystem): IntRange =
        value(canonical.first, unit, system)..value(canonical.last, unit, system)
    fun canonical(displayed: Int, unit: String, system: MeasurementSystem): Int = canonical(displayed.toDouble(), unit, system).roundToInt()
    fun distance(canonical: Int, unit: String, system: MeasurementSystem): Int = distance(canonical.toDouble(), unit, system).roundToInt()
    fun canonicalDistance(displayed: Int, unit: String, system: MeasurementSystem): Int = canonicalDistance(displayed.toDouble(), unit, system).roundToInt()
}
