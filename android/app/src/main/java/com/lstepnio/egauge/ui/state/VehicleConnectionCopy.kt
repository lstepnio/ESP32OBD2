package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.AdapterSourceStatus
import com.lstepnio.egauge.core.designsystem.*

fun vehicleConnectionStatus(status: AdapterSourceStatus?, ageMs: Long?, profileId: String,
    sourceId: String, selected: Boolean, setupConfirmed: Boolean, failed: Boolean): StatusUi = when {
    !setupConfirmed -> StatusUi("Car setup needed", "Choose an adapter and send this car's setup to the gauge.")
    !selected -> StatusUi("Choose an adapter", "Plug your adapter into the car to get started.")
    failed || status == null || ageMs == null || ageMs !in 0..30_000 ->
        StatusUi("Checking car connection", "We'll retry automatically while the app is open.", StatusTone.Stale)
    status.vehicleId != profileId || !status.sourceId.equals(sourceId, ignoreCase = true) ->
        StatusUi("Car setup needed", "Send this car's setup to use its selected adapter.")
    status.simulated -> StatusUi("Example adapter", "These readings are simulated. No vehicle connection is verified.", StatusTone.Disabled)
    status.phase == 4 && status.bound -> StatusUi("Car connected", "The gauge is connected to your vehicle adapter.", StatusTone.Success)
    status.phase == 0 -> StatusUi("Choose an adapter", "Plug your adapter into the car to get started.")
    status.phase == 6 -> StatusUi("Car connection paused", "It will resume after the gauge update.", StatusTone.Loading)
    else -> StatusUi("Connecting to car", "Keep the adapter plugged in and ignition on. We'll retry automatically.", StatusTone.Loading)
}

/** Stable link identity keeps engine and transmission status independent for multiple adapters. */
data class ConnectionLinkUi(val id: String, val title: String, val status: StatusUi)

fun appConnectionLabel(gaugeLabel: String, gaugeReady: Boolean, links: List<ConnectionLinkUi>): String = when {
    !gaugeReady -> gaugeLabel
    links.isNotEmpty() && links.all { it.status.tone == StatusTone.Success } -> "Connected"
    links.any { it.status.title == "Example adapter" } -> "Example mode"
    links.any { it.status.title == "Connecting to car" } -> "Car reconnecting"
    links.any { it.status.title == "Checking car connection" } -> "Checking car"
    links.any { it.status.title == "Car connection paused" } -> "Car paused"
    else -> "Car setup needed"
}

/** Compare only connection setup, so an unrelated unsent page edit does not disconnect a car. */
fun vehicleSetupMatches(document: com.lstepnio.egauge.GaugeConfigTransferClient.ActiveDocument?,
    profileId: String, source: String, adapter: com.lstepnio.egauge.AdapterBinding?): Boolean = runCatching {
    if (document == null || document.vehicleProfileId != profileId) return false
    val sources = org.json.JSONObject(document.json).getJSONArray("sources")
    val entry = (0 until sources.length()).map { sources.getJSONObject(it) }
        .singleOrNull { it.getString("role").equals(source, true) } ?: return false
    val saved = entry.optJSONObject("adapter")
    if (saved == null) adapter == null else com.lstepnio.egauge.AdapterBinding.decode(saved) == adapter
}.getOrDefault(false)

fun configuredSourceId(document: com.lstepnio.egauge.GaugeConfigTransferClient.ActiveDocument?,
    source: String): String? = runCatching {
    val entries = org.json.JSONObject(document?.json ?: return null).getJSONArray("sources")
    (0 until entries.length()).map { entries.getJSONObject(it) }
        .singleOrNull { it.getString("role").equals(source, true) }?.getString("id")
}.getOrNull()

fun settingsReadCurrent(checkedAt: Long?, now: Long, failed: Boolean): Boolean =
    !failed && checkedAt != null && now >= checkedAt && now - checkedAt <= 60_000
