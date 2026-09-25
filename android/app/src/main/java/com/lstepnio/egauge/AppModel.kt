package com.lstepnio.egauge

import android.app.Application
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
    val exampleKind: String,
    val category: String,
    val gaugeLabel: String,
)

/** Populated only from an adapter session with a known vehicle and ECU source. */
enum class VehiclePidStatus { Responding, NoResponse }

data class VehiclePidObservation(
    val profileId: String,
    val sessionId: String,
    val adapterId: String,
    val ecuId: String,
    val pidId: String,
    val source: String,
    val status: VehiclePidStatus,
    val observedAtMillis: Long,
)

val demoCatalog = listOf(
    PidExample("rpm", "Engine RPM", "ECM", "01 0C", "rpm", "2,840", "Example response", "Engine", "ENGINE RPM"),
    PidExample("coolant", "Coolant temperature", "ECM", "01 05", "°C", "92", "Example response", "Thermal", "COOLANT"),
    PidExample("speed", "Vehicle speed", "ECM", "01 0D", "km/h", "64", "Example response", "Driving", "SPEED"),
    PidExample("load", "Calculated load", "ECM", "01 04", "%", "38", "Example response", "Engine", "ENGINE LOAD"),
    PidExample("fuel", "Fuel level", "ECM", "01 2F", "%", "73", "Example response", "Fuel", "FUEL LEVEL"),
    PidExample("tcm", "Transmission input speed", "TCM", "Vehicle specific", "rpm", "2,120", "Synthetic only", "Transmission", "INPUT SPEED"),
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
    val hysteresis: Int = 3,
    val triggerDwellMs: Int = 1000,
    val clearDwellMs: Int = 2000,
    val source: String = "ECM",
)

data class CapabilitySnapshot(
    val board: String,
    val protocolMajor: Int,
    val maxAdapterLinks: Int,
    val simultaneousVerified: Boolean,
    val configWrite: Boolean,
    val savedStateRead: Boolean,
    val quickSelect: Boolean,
    val displayRotationWrite: Boolean,
    val ota: Boolean,
)

data class GaugeSavedSnapshot(val readingIndex: Int, val rotation: Int, val revision: Long)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val bleClient = BleCapabilityClient(application)
    private val profileStore = ProfileStore(application)
    private val loadedProfiles = profileStore.load()

    var destination by mutableStateOf(Destination.Garage)
        private set
    var profileCollection by mutableStateOf(loadedProfiles.collection)
        private set
    var profileError by mutableStateOf(loadedProfiles.error)
        private set
    var profileNameInput by mutableStateOf("")
        private set
    var draft by mutableStateOf(profileCollection.active.draft)
        private set
    var query by mutableStateOf("")
        private set
    var sourceFilter by mutableStateOf("All")
        private set
    // Capability reads from the gauge do not populate vehicle PID observations.
    var vehicleObservations by mutableStateOf<List<VehiclePidObservation>>(emptyList())
        private set
    var activeVehicleSessionId by mutableStateOf<String?>(null)
        private set
    var deviceMessage by mutableStateOf("No gauge connected")
        private set
    var capabilities by mutableStateOf<CapabilitySnapshot?>(null)
        private set
    var savedGauge by mutableStateOf<GaugeSavedSnapshot?>(null)
        private set
    val configurationBlockers: List<String>
        get() = buildList {
            if (profileError != null) add("Local profile data needs recovery before applying")
            if (draft.warning >= draft.critical) add("Coolant critical limit must exceed warning")
            if (draft.warning !in -40..215 || draft.critical !in -40..215)
                add("Coolant limits must be within -40 to 215 °C")
            if (draft.warning - draft.hysteresis < -40 ||
                draft.hysteresis >= draft.critical - draft.warning)
                add("Coolant hysteresis does not fit the selected limits")
            if (capabilities == null) add("Read gauge capabilities first")
            else if (capabilities?.configWrite != true) add("Gauge does not offer full configuration writes")
            val observed = activeVehicleSessionId != null && vehicleObservations.any { observation ->
                observation.profileId == profileCollection.activeId &&
                    observation.sessionId == activeVehicleSessionId &&
                    observation.adapterId.isNotBlank() && observation.ecuId.isNotBlank() &&
                    observation.pidId == draft.pidId && observation.source == draft.source &&
                    observation.status == VehiclePidStatus.Responding
            }
            if (activeVehicleSessionId == null) add("No vehicle discovery session is active")
            else if (!observed) add("Selected PID has no responding evidence for this profile and ECU")
            add("Versioned configuration transfer is not implemented")
        }
    var scanning by mutableStateOf(false)
        private set
    var discoveryPreview by mutableStateOf(false)
        private set
    var labOpen by mutableStateOf(false)
        private set
    var labInput by mutableStateOf("41 0C 2C 60")
        private set
    var customLabOpen by mutableStateOf(false)
        private set
    var customRequestInput by mutableStateOf("22 F1 90")
        private set
    var customSource by mutableStateOf("ECM")
        private set

    fun navigate(value: Destination) { destination = value }
    fun search(value: String) { query = value }
    fun filter(value: String) { sourceFilter = value }
    fun showDiscoveryPreview(value: Boolean) { discoveryPreview = value }
    fun showLab(value: Boolean) { labOpen = value }
    fun editLabInput(value: String) { labInput = value.take(128) }
    fun showCustomLab(value: Boolean) { customLabOpen = value }
    fun editCustomRequest(value: String) { customRequestInput = value.take(32) }
    fun selectCustomSource(value: String) { if (value == "ECM" || value == "TCM") customSource = value }
    fun editProfileName(value: String) { profileNameInput = value.take(32) }
    fun selectProfile(id: String) {
        if (profileError != null || profileCollection.profiles.none { it.id == id }) return
        profileCollection = profileCollection.copy(activeId = id)
        draft = profileCollection.active.draft
        profileStore.save(profileCollection)
    }
    fun createProfile() {
        val name = profileNameInput.trim()
        if (profileError != null || name.isEmpty() || profileCollection.profiles.size >= 8 ||
            profileCollection.profiles.any { it.name.equals(name, ignoreCase = true) }) return
        val profile = VehicleProfile(ProfileStore.newId(), name, Draft())
        profileCollection = profileCollection.copy(
            activeId = profile.id,
            profiles = profileCollection.profiles + profile,
        )
        draft = profile.draft
        profileNameInput = ""
        profileStore.save(profileCollection)
    }
    fun markScanning(value: Boolean) { scanning = value }
    fun connectionError(message: String) {
        scanning = false
        deviceMessage = message
        capabilities = null
        savedGauge = null
    }
    fun connected(value: CapabilitySnapshot) {
        scanning = false
        capabilities = value
        savedGauge = null
        deviceMessage = if (value.quickSelect)
            "Gauge identified. Built-in reading selection is available after pairing."
        else "Gauge identified. Discovery link closed; protocol ${value.protocolMajor} is read only."
    }
    fun selectionApplied(index: Int) {
        scanning = false
        savedGauge = null
        deviceMessage = "Gauge confirmed built-in reading ${index + 1} of 5."
    }
    fun snapshotRead(value: GaugeSavedSnapshot) {
        scanning = false
        savedGauge = value
        deviceMessage = "Gauge saved state read at revision ${value.revision}."
    }
    fun selectionError(message: String) {
        scanning = false
        deviceMessage = message
    }
    fun snapshotError(message: String) {
        scanning = false
        savedGauge = null
        deviceMessage = message
    }
    fun selectPid(pid: PidExample) = save(draft.copy(pidId = pid.id, source = pid.source))
    fun selectLayout(layout: GaugeLayout) = save(draft.copy(layout = layout))
    fun setWarning(value: Int) = save(draft.copy(warning = value))
    fun setCritical(value: Int) = save(draft.copy(critical = value))
    fun setHysteresis(value: Int) = save(draft.copy(hysteresis = value.coerceIn(0, 20)))
    fun setTriggerDwell(value: Int) = save(draft.copy(triggerDwellMs = value.coerceIn(0, 60000)))
    fun setClearDwell(value: Int) = save(draft.copy(clearDwellMs = value.coerceIn(0, 60000)))
    fun setSource(value: String) = save(draft.copy(source = value))

    private fun save(value: Draft) {
        if (profileError != null) return
        draft = value
        profileCollection = profileCollection.copy(profiles = profileCollection.profiles.map { profile ->
            if (profile.id == profileCollection.activeId) profile.copy(draft = value) else profile
        })
        profileStore.save(profileCollection)
    }
}
