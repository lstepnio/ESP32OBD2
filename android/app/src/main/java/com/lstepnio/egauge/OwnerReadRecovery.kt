package com.lstepnio.egauge

import kotlinx.coroutines.delay

/** One authenticated, read-only status reset repairs a cached long response on
 * older firmware. It never changes configuration, pairing, faults or firmware. */
internal fun ownerReadResetCommand(gattStatus: Int, alreadyReset: Boolean): ByteArray? =
    if (gattStatus == 7 && !alreadyReset) byteArrayOf(0x17, 1, 0, 0, 0) else null

internal data class OwnerStateRead(val status: Int, val bytes: ByteArray)

/** Before any transaction command, require a successful protected status frame.
 * Android's internal GATT_ERROR (133) can interrupt this initial read. Bounded
 * read retries never replay a transaction or substitute for authentication.
 */
internal suspend fun establishProtectedOwner(
    read: suspend () -> OwnerStateRead,
    writeStatusReset: suspend (ByteArray) -> Unit,
    pause: suspend () -> Unit = { delay(250) },
) {
    var resetReadMode = false
    repeat(4) { attempt ->
        val result = read()
        if (result.status == 0 && GaugeProtocolCodec.isProtectedStatusFrame(result.bytes)) return
        val reset = ownerReadResetCommand(result.status, resetReadMode)
        if (reset != null) {
            resetReadMode = true
            writeStatusReset(reset)
        } else {
            if (result.status !in setOf(5, 15, 17, 133))
                error("Gauge owner state read failed (GATT ${result.status}, version ${result.bytes.firstOrNull()?.toInt()?.and(255)}, length ${result.bytes.size})")
            if (attempt < 3) pause()
        }
    }
    error("Gauge owner link did not become authenticated")
}
