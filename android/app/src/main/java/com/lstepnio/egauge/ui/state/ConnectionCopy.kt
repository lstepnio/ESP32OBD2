package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.connection.ConnectionState
import com.lstepnio.egauge.core.designsystem.StatusTone
import com.lstepnio.egauge.core.designsystem.StatusUi

fun connectionStatus(value: ConnectionState, nowElapsedMs: Long): StatusUi = when (value.phase) {
    ConnectionPhase.PermissionRequired -> StatusUi("Allow nearby devices", "Allow access so the app can find your gauge.", StatusTone.Disabled)
    ConnectionPhase.BluetoothOff -> StatusUi("Bluetooth is off", "Turn on Bluetooth to reconnect automatically.", StatusTone.Offline)
    ConnectionPhase.Searching -> StatusUi("Looking for your gauge", "Keep it powered and nearby. We'll connect when it's found.", StatusTone.Loading)
    ConnectionPhase.Checking -> StatusUi("Connecting to your gauge", "Checking its current settings.", StatusTone.Loading)
    ConnectionPhase.ChooseGauge -> StatusUi("Choose your gauge", "Select the gauge you want to pair.")
    ConnectionPhase.PairRequired -> StatusUi("Your gauge is ready to pair", "Confirm the code on your gauge to continue.")
    ConnectionPhase.Retrying -> StatusUi("Reconnecting to your gauge", "We'll keep trying while the app is open. Keep the gauge powered and nearby.", StatusTone.Offline)
    ConnectionPhase.Ready -> if (value.fresh(nowElapsedMs))
        StatusUi("Your gauge is ready", "Its current settings have been checked.", StatusTone.Success)
    else StatusUi("Gauge check is out of date", "Keep your gauge powered and nearby. We'll check again automatically.", StatusTone.Stale)
    ConnectionPhase.Unavailable -> StatusUi("Gauge found", "Automatic checks are not available on this gauge. You can still explore the preview.", StatusTone.Disabled)
    ConnectionPhase.Idle -> StatusUi("Your gauge is not connected", "Connect to check its current settings.")
}

fun connectionLabel(value: ConnectionState, now: Long): String = when {
    value.fresh(now) -> "Gauge ready"
    value.phase == ConnectionPhase.PermissionRequired -> "Allow access"
    value.phase == ConnectionPhase.BluetoothOff -> "Bluetooth off"
    value.phase == ConnectionPhase.Searching -> "Searching"
    value.phase == ConnectionPhase.Checking -> "Connecting"
    value.phase == ConnectionPhase.Retrying -> "Reconnecting"
    value.phase == ConnectionPhase.PairRequired -> "Pair gauge"
    value.phase == ConnectionPhase.ChooseGauge -> "Choose gauge"
    value.phase == ConnectionPhase.Unavailable -> "Gauge found"
    else -> "Not connected"
}

/** Every projected byte, plus the existing running/trial proof, must match. No field-only inference. */
fun matchesRunningPayload(bytes: ByteArray?, revision: Long?, digest: String?,
    runtime: com.lstepnio.egauge.GaugeConfigTransferClient.RuntimeIdentity?): Boolean {
    if (bytes == null || !isConfirmedSetup(revision, digest, runtime)) return false
    val expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    return expected == digest
}
