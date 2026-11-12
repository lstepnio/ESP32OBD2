package com.lstepnio.egauge

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal interface WifiUpdateBootstrapSession {
    suspend fun identity(): GaugeConfigTransferClient.BootIdentity
    suspend fun targetIdentity(): ByteArray
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

internal fun privateOtaTargetRequest() = byteArrayOf(0x31, 1, 0, 0, 0, 1)

private fun verifyPrivateOtaTarget(bytes: ByteArray, expectedTag: Long, boot: GaugeConfigTransferClient.BootIdentity) {
    check(
        bytes.size == 32 && bytes[0] == 1.toByte() && bytes[1] == 1.toByte() &&
            bytes[2] == 0.toByte() && bytes[3] == 0.toByte()
    ) { "Gauge did not confirm a rollback-enabled update target" }
    val fields = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val tag = Integer.toUnsignedLong(fields.getInt(4))
    val running = Integer.toUnsignedLong(fields.getInt(8))
    val alternate = Integer.toUnsignedLong(fields.getInt(12))
    check(expectedTag != 0L && tag == expectedTag) { "Signed update targets a different gauge" }
    check(
        running == boot.partitionAddress && setOf(running, alternate) == setOf(0x60000L, 0x360000L) &&
            fields.getInt(16) == 0x300000 && fields.getInt(20) == 0x300000 &&
            fields.getInt(24) == 0x10000 && fields.getInt(28) == 0x2000
    ) { "Gauge does not have the required two-slot update layout" }
}

/** One protected session; uncertain AP startup is retired only after its GATT session closes. */
internal suspend fun bootstrapWifiUpdate(
    expectedElf: String,
    owner: WifiUpdateBootstrapOwner,
    expectedTargetTag: Long? = null
): WifiUpdateBootstrap {
    var requestedWifi = false
    try {
        return owner.withSession {
            val before = identity()
            check(before.elfSha256 != expectedElf) { "This signed firmware image is already running on the gauge" }
            if (expectedTargetTag != null) {
                check(before.otaState == 2) { "The running gauge firmware has not completed its boot check" }
                verifyPrivateOtaTarget(targetIdentity(), expectedTargetTag, before)
            }
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

/** A disconnected startup can be retried after the first attempt closes its transport. */
internal suspend fun <T> retryWifiUpdateStartup(
    onRetry: () -> Unit,
    attempt: suspend ((FirmwareUpdateStage) -> Unit) -> T
): T {
    var beforeFlash = true
    try {
        return attempt { stage ->
            if (stage != FirmwareUpdateStage.PREPARING && stage != FirmwareUpdateStage.CONNECTING_WIFI) {
                beforeFlash = false
            }
        }
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        val transient = when (error) {
            is TimeoutCancellationException -> true
            is CancellationException, is GaugeCommandRejectedException -> false
            is IOException, is GaugeLinkException -> true
            else -> false
        }
        if (!beforeFlash || !transient) throw error
    }
    onRetry()
    delay(1_000)
    return attempt {}
}
