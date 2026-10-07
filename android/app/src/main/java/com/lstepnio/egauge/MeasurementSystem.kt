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
        val number = value.replace(",", "").toIntOrNull() ?: return value
        val converted = value(number, unit, system)
        return if ("," in value) "%,d".format(converted) else converted.toString()
    }

    fun label(unit: String, system: MeasurementSystem): String = when {
        system == MeasurementSystem.Imperial && unit == "°C" -> "°F"
        system == MeasurementSystem.Imperial && unit == "degC" -> "°F"
        system == MeasurementSystem.Imperial && unit == "km/h" -> "mph"
        system == MeasurementSystem.Imperial && unit == "kph" -> "mph"
        else -> unit
    }

    fun value(canonical: Int, unit: String, system: MeasurementSystem): Int = when {
        system != MeasurementSystem.Imperial -> canonical
        unit == "°C" || unit == "degC" -> (canonical * 9.0 / 5.0 + 32.0).roundToInt()
        unit == "km/h" || unit == "kph" -> (canonical * 0.621371).roundToInt()
        else -> canonical
    }

    fun range(canonical: IntRange, unit: String, system: MeasurementSystem): IntRange =
        value(canonical.first, unit, system)..value(canonical.last, unit, system)

    fun canonical(displayed: Int, unit: String, system: MeasurementSystem): Int = when {
        system != MeasurementSystem.Imperial -> displayed
        unit == "°C" || unit == "degC" -> ((displayed - 32) * 5.0 / 9.0).roundToInt()
        unit == "km/h" || unit == "kph" -> (displayed / 0.621371).roundToInt()
        else -> displayed
    }

    fun distance(canonical: Int, unit: String, system: MeasurementSystem): Int = when {
        system != MeasurementSystem.Imperial -> canonical
        unit == "°C" || unit == "degC" -> (canonical * 9.0 / 5.0).roundToInt()
        unit == "km/h" || unit == "kph" -> (canonical * 0.621371).roundToInt()
        else -> canonical
    }

    fun canonicalDistance(displayed: Int, unit: String, system: MeasurementSystem): Int = when {
        system != MeasurementSystem.Imperial -> displayed
        unit == "°C" || unit == "degC" -> (displayed * 5.0 / 9.0).roundToInt()
        unit == "km/h" || unit == "kph" -> (displayed / 0.621371).roundToInt()
        else -> displayed
    }
}
