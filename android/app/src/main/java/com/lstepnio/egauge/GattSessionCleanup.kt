package com.lstepnio.egauge

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Keep the lease until Android reports disconnect, with bounded cancellation-safe
 * cleanup. A missing platform callback still releases the registered GATT client.
 */
internal suspend fun closeGattSession(
    disconnect: () -> Unit,
    awaitDisconnected: suspend () -> Unit,
    close: () -> Unit,
    timeoutMs: Long = 1_500
): Boolean = withContext(NonCancellable) {
    try {
        disconnect()
        withTimeoutOrNull(timeoutMs) {
            awaitDisconnected()
            true
        } ?: false
    } finally {
        close()
    }
}
