package com.lstepnio.egauge.ui.state

import android.content.Context
import androidx.compose.runtime.Immutable
import com.lstepnio.egauge.MeasurementSystem

@Immutable
data class PresentationPreferences(val advanced: Boolean = false, val dynamicColor: Boolean = false,
                                   val gaugeName: String = "eGauge",
                                   val measurementSystem: MeasurementSystem = MeasurementSystem.Metric)

/** Appearance only. No owner keys, vehicle identity or protocol state. */
class PresentationPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("presentation", Context.MODE_PRIVATE)
    fun read() = PresentationPreferences(preferences.getBoolean("advanced", false),
        preferences.getBoolean("dynamic-colour", false), preferences.getString("gauge-name", "eGauge") ?: "eGauge",
        MeasurementSystem.entries.firstOrNull {
            it.name == preferences.getString("measurement-system", MeasurementSystem.Metric.name)
        } ?: MeasurementSystem.Metric)
    fun write(value: PresentationPreferences): Boolean = preferences.edit()
        .putBoolean("advanced", value.advanced).putBoolean("dynamic-colour", value.dynamicColor)
        .putString("gauge-name", value.gaugeName)
        .putString("measurement-system", value.measurementSystem.name).commit()
}
