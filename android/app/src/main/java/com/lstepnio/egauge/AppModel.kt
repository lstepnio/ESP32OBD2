package com.lstepnio.egauge

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/** All catalog rows below are examples, never vehicle capability evidence. */
data class PidExample(
    val id: String,
    val name: String,
    val source: String,
    val request: String,
    val unit: String,
    val demoValue: String,
    val evidence: String,
    val category: String,
)

val demoCatalog = listOf(
    PidExample("rpm", "Engine RPM", "ECM", "01 0C", "rpm", "2,840", "Example response", "Engine"),
    PidExample("coolant", "Coolant temperature", "ECM", "01 05", "°C", "92", "Example response", "Thermal"),
    PidExample("speed", "Vehicle speed", "ECM", "01 0D", "km/h", "64", "Example response", "Driving"),
    PidExample("load", "Calculated load", "ECM", "01 04", "%", "38", "Example response", "Engine"),
    PidExample("fuel", "Fuel level", "ECM", "01 2F", "%", "73", "Example response", "Fuel"),
    PidExample("tcm", "Transmission input speed", "TCM", "Vehicle specific", "rpm", "2,120", "Synthetic only", "Transmission"),
)

enum class Destination(val label: String, val glyph: String) {
    Garage("Garage", "⌂"), Design("Design", "◉"), Pids("PIDs", "≡"), Device("Device", "▣")
}

enum class GaugeLayout(val label: String) {
    Numeric("Numeric"), Arc("Arc"), Bar("Bar"), Trend("Trend"), Dual("Dual")
}

data class Draft(
    val pidId: String = "rpm",
    val layout: GaugeLayout = GaugeLayout.Arc,
    val warning: Int = 105,
    val critical: Int = 115,
    val source: String = "ECM",
)

data class CapabilitySnapshot(
    val board: String,
    val protocolMajor: Int,
    val maxAdapterLinks: Int,
    val simultaneousVerified: Boolean,
    val configWrite: Boolean,
    val ota: Boolean,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("draft-v1", Context.MODE_PRIVATE)

    var destination by mutableStateOf(Destination.Garage)
        private set
    var draft by mutableStateOf(loadDraft())
        private set
    var query by mutableStateOf("")
        private set
    var sourceFilter by mutableStateOf("All")
        private set
    var deviceMessage by mutableStateOf("No gauge connected")
        private set
    var capabilities by mutableStateOf<CapabilitySnapshot?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var discoveryPreview by mutableStateOf(false)
        private set
    var labOpen by mutableStateOf(false)
        private set
    var labInput by mutableStateOf("41 0C 2C 60")
        private set

    fun navigate(value: Destination) { destination = value }
    fun search(value: String) { query = value }
    fun filter(value: String) { sourceFilter = value }
    fun showDiscoveryPreview(value: Boolean) { discoveryPreview = value }
    fun showLab(value: Boolean) { labOpen = value }
    fun editLabInput(value: String) { labInput = value.take(128) }
    fun markScanning(value: Boolean) { scanning = value }
    fun connectionError(message: String) {
        scanning = false
        deviceMessage = message
        capabilities = null
    }
    fun connected(value: CapabilitySnapshot) {
        scanning = false
        capabilities = value
        deviceMessage = "Gauge identified. Discovery link closed; protocol ${value.protocolMajor} is read only."
    }
    fun selectPid(pid: PidExample) = save(draft.copy(pidId = pid.id, source = pid.source))
    fun selectLayout(layout: GaugeLayout) = save(draft.copy(layout = layout))
    fun setWarning(value: Int) = save(draft.copy(warning = value))
    fun setCritical(value: Int) = save(draft.copy(critical = value))
    fun setSource(value: String) = save(draft.copy(source = value))

    private fun save(value: Draft) {
        draft = value
        preferences.edit().putString("pid", value.pidId).putString("layout", value.layout.name)
            .putInt("warning", value.warning).putInt("critical", value.critical)
            .putString("source", value.source).apply()
    }

    private fun loadDraft(): Draft {
        val pid = preferences.getString("pid", "rpm")!!.takeIf { id -> demoCatalog.any { it.id == id } } ?: "rpm"
        val layout = runCatching { GaugeLayout.valueOf(preferences.getString("layout", "Arc")!!) }
            .getOrDefault(GaugeLayout.Arc)
        return Draft(
            pidId = pid,
            layout = layout,
            warning = preferences.getInt("warning", 105).coerceIn(-40, 250),
            critical = preferences.getInt("critical", 115).coerceIn(-40, 250),
            source = preferences.getString("source", "ECM")!!.takeIf { it == "ECM" || it == "TCM" } ?: "ECM",
        )
    }
}
