package com.lstepnio.egauge

import kotlinx.coroutines.withTimeout

private class LegacyDiagnosticsRejected(cause: GaugeCommandRejectedException) : Exception("Snapshot command is unsupported", cause)

/** Each request owns a fresh authenticated transaction. Only a confirmed opcode rejection permits downgrade. */
internal suspend fun readDiagnosticsSnapshot(
    sourceIndex: Int?,
    withSession: suspend (suspend GaugeOwnerTransaction.() -> ByteArray) -> ByteArray
): ByteArray {
    require(sourceIndex == null || sourceIndex in 0..1)
    suspend fun request(command: ByteArray): ByteArray = withSession {
        try {
            writeRaw(command)
        } catch (error: GaugeCommandRejectedException) {
            // Authentication occurs before this block. Do not downgrade owner/auth failures.
            if (command[0] == 0x39.toByte() && error.status in setOf(6, 13)) throw LegacyDiagnosticsRejected(error)
            throw error
        }
        readRaw()
    }
    // Reconnection/fallback shares one deadline rather than renewing the operation budget.
    return withTimeout(240_000) {
        try {
            request(if (sourceIndex == null) byteArrayOf(0x39, 1, 0, 0, 0) else byteArrayOf(0x3a, 1, 0, 0, 0, sourceIndex.toByte()))
        } catch (_: LegacyDiagnosticsRejected) {
            // The first session has completed cleanup; never send fallback on retired IO.
            request(byteArrayOf(0x30, 1, 0, 0, 0))
        }
    }
}
