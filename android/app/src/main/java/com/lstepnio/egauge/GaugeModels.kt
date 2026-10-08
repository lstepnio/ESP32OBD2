package com.lstepnio.egauge

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
    val definitionId: String = "",
    val minimum: Double = 0.0,
    val maximum: Double = 100.0,
    val alertKind: String = "numeric",
    val configurationVersion: Int = 5,
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

// Preserve legacy local profiles; synthetic input speed cannot be sent.
val demoCatalog = readingCatalog + PidExample("tcm", "Transmission input speed", "TCM", "Vehicle specific", "rpm", "2,120", "Synthetic only", "Transmission", "INPUT SPEED")

enum class Destination(val label: String, val glyph: String) {
    Gauge("Gauge", "◉"), Readings("Readings", "≡"), Vehicle("Vehicle", "⌂"), Settings("Settings", "⚙")
}

enum class GaugeLayout(val label: String) {
    Numeric("Numeric"), Arc("Arc"), Bar("Bar"), Trend("Trend"), Dual("Dual")
}

data class GaugePageDraft(
    val id: String,
    val name: String,
    val layout: GaugeLayout,
    val pidIds: List<String>,
)

enum class AlertDirection { Above, Below, Equals }

data class GaugeAlertDraft(
    val id: String,
    val pidId: String,
    val direction: AlertDirection = AlertDirection.Above,
    val warning: Double,
    val critical: Double,
    val hysteresis: Double = 3.0,
    val triggerDwellMs: Int = 1000,
    val clearDwellMs: Int = 2000,
    val priority: Int = 8,
) {
    constructor(id: String, pidId: String, direction: AlertDirection = AlertDirection.Above,
                warning: Int, critical: Int, hysteresis: Int = 3, triggerDwellMs: Int = 1000,
                clearDwellMs: Int = 2000, priority: Int = 8) : this(id, pidId, direction,
        warning.toDouble(), critical.toDouble(), hysteresis.toDouble(), triggerDwellMs, clearDwellMs, priority)
}

fun readingRange(id: String): IntRange = readingBounds(id).let {
    kotlin.math.ceil(it.start).toInt()..kotlin.math.floor(it.endInclusive).toInt()
}

fun defaultAlert(pidId: String = "coolant"): GaugeAlertDraft {
    val reading = readingCatalog.first { it.id == pidId }
    if (reading.alertKind == "gear") return GaugeAlertDraft("alert.$pidId", pidId,
        AlertDirection.Equals, warning = 11.0, critical = 13.0, hysteresis = 0.0)
    val span = reading.maximum - reading.minimum
    val warning = when (pidId) {
        "coolant" -> 105.0
        "rpm" -> 4000.0
        "speed" -> 120.0
        "load", "fuel" -> 80.0
        else -> reading.minimum + span * .75
    }
    val critical = when (pidId) {
        "coolant" -> 115.0
        "rpm" -> 5000.0
        "speed" -> 140.0
        "load", "fuel" -> 90.0
        else -> reading.minimum + span * .9
    }
    val margin = if (reading.configurationVersion == 1) (span.toInt() / 50).coerceIn(1, 20).toDouble()
        else minOf(span * .02, (critical - warning) / 4)
    return GaugeAlertDraft("alert.$pidId", pidId, AlertDirection.Above, warning, critical, hysteresis = margin)
}

fun defaultGaugePages(primary: String = "rpm", layout: GaugeLayout = GaugeLayout.Numeric): List<GaugePageDraft> {
    val ordered = listOf(primary, "rpm", "coolant", "speed").distinct().take(3)
    return ordered.mapIndexed { index, pidId ->
        val definition = demoCatalog.first { it.id == pidId }
        GaugePageDraft("page.${index + 1}.${pidId}", definition.gaugeLabel, if (index == 0) layout else GaugeLayout.Numeric,
            listOf(pidId))
    }
}

enum class OwnerAccess { UNKNOWN, DISCOVERED, AUTHENTICATED }
enum class PairingWindowState { CLOSED, READY, CODE_DISPLAYED, OWNER_PRESENT }
enum class PairingProgress { WAITING_FOR_ANDROID, CHECKING_GAUGE_ACCESS }
data class GaugePairingWindow(val state: PairingWindowState, val secondsRemaining: Int)

data class Draft(
    val pidId: String = "rpm",
    val layout: GaugeLayout = GaugeLayout.Numeric,
    val source: String = "ECM",
    val pages: List<GaugePageDraft> = defaultGaugePages(pidId, layout),
    val alerts: List<GaugeAlertDraft> = listOf(defaultAlert()),
    val actions: List<PageAction> = emptyList(),
)

data class CapabilitySnapshot(
    val board: String,
    val protocolMajor: Int,
    val maxAdapterLinks: Int,
    val simultaneousVerified: Boolean,
    val configWrite: Boolean,
    val experimentalNumericConfig: Boolean,
    val savedStateRead: Boolean,
    val quickSelect: Boolean,
    val displayRotationWrite: Boolean,
    val displaySettingsVersion: Int = 0,
    val ota: Boolean,
    val wifiBulk: String?,
    val hardwareCapacityVersion: Int?,
    val configurationVersion: Int = 0,
    val adapterRegistryVersion: Int = 0,
    val dualAdapterVersion: Int = 0,
    val vehicleDashboardVersion: Int = 0,
    val pidCatalogVersion: Int = 0,
    val pageActionsVersion: Int = 0,
    val maxPages: Int = 0,
    val supportedRenderers: Set<GaugeLayout> = emptySet(),
)

data class GaugeSavedSnapshot(val readingIndex: Int, val rotation: Int, val revision: Long)

