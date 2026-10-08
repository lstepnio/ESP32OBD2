package com.lstepnio.egauge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Only protected reads retry after restart. A session timeout is not caller cancellation. */
internal suspend fun <T> restartRead(timeoutMs: Long = 20_000, read: suspend () -> T): T? {
    if (timeoutMs <= 0) return null
    return try {
        withTimeoutOrNull(timeoutMs) { read() }
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
