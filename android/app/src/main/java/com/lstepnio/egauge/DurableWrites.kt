package com.lstepnio.egauge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One disk writer. An admitted platform commit finishes before its lease is released. */
class DurableWrites {
    private val mutex = Mutex()
    suspend fun <T> write(block: () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO + NonCancellable) { block() }
    }
}

/** Use for recoverable suspend work only. Cancellation must never become a success/null. */
suspend inline fun <T> suspendResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

/** Only cleanup is noncancellable; its own deadline remains effective. */
suspend fun boundedCleanup(timeoutMs: Long, block: suspend () -> Unit) {
    withContext(NonCancellable) {
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { suspendResult { block() } }
    }
}
