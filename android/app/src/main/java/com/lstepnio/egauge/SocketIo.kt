package com.lstepnio.egauge

import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One deadline for an entire exchange, including writes and fragmented reads.
 * Socket streams do not reliably respond to thread interruption. Closing the socket
 * on cancellation unblocks the IO child; structured concurrency waits for its exit.
 * Failed exchanges invalidate the transport. Callers must reconcile uncertain writes.
 */
internal suspend fun <T> socketIo(socket: Socket, timeoutMs: Long, block: () -> T): T =
    resourceIo(timeoutMs, { socket.close() }, block)

/** Cancellation retires the resource and waits for its IO child before returning. */
internal suspend fun <T> resourceIo(timeoutMs: Long, close: () -> Unit, block: () -> T): T =
    withTimeout(timeoutMs) {
        coroutineScope {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { runCatching { close() } }
                launch(Dispatchers.IO) {
                    try {
                        ensureActive()
                        continuation.resume(block())
                    } catch (error: Exception) {
                        runCatching { close() }
                        continuation.resumeWithException(error)
                    }
                }
            }
        }
    }
