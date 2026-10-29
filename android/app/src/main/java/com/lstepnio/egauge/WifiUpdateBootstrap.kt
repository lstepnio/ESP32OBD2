package com.lstepnio.egauge

internal interface WifiUpdateBootstrapSession {
    suspend fun identity(): GaugeConfigTransferClient.BootIdentity
    suspend fun openWifi(): WifiBulkSession
}

internal interface WifiUpdateBootstrapOwner {
    suspend fun <T> withSession(action: suspend WifiUpdateBootstrapSession.() -> T): T
    suspend fun closeWifi()
}

internal data class WifiUpdateBootstrap(
    val before: GaugeConfigTransferClient.BootIdentity,
    val wifi: WifiBulkSession
)

/** One protected session; uncertain AP startup is retired only after its GATT session closes. */
internal suspend fun bootstrapWifiUpdate(expectedElf: String, owner: WifiUpdateBootstrapOwner): WifiUpdateBootstrap {
    var requestedWifi = false
    try {
        return owner.withSession {
            val before = identity()
            check(before.elfSha256 != expectedElf) { "This signed firmware image is already running on the gauge" }
            requestedWifi = true
            WifiUpdateBootstrap(before, openWifi())
        }
    } catch (error: Exception) {
        if (requestedWifi) {
            try {
                boundedCleanup(5_000) { owner.closeWifi() }
            } catch (_: Exception) {
                // A cleanup session's own timeout/cancellation must not replace the startup outcome.
            }
        }
        throw error
    }
}
