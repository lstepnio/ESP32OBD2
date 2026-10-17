package com.lstepnio.egauge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GattSessionCleanupTest {
    @Test fun nextOperationWaitsForPlatformDisconnectAndClientClose() = runBlocking {
        val disconnected = CompletableDeferred<Unit>()
        val steps = mutableListOf<String>()
        val cleanup = async(start = CoroutineStart.UNDISPATCHED) {
            closeGattSession({ steps += "disconnect" }, { disconnected.await() }, { steps += "close" })
                .also { steps += "next" }
        }
        assertEquals(listOf("disconnect"), steps)
        assertFalse(cleanup.isCompleted)
        disconnected.complete(Unit)
        assertTrue(cleanup.await())
        assertEquals(listOf("disconnect", "close", "next"), steps)
    }
    @Test fun missingCallbackHasABoundedWaitAndStillReleasesTheClient() = runBlocking {
        var closed = 0
        assertFalse(withTimeout(1_000) {
            closeGattSession({}, { awaitCancellation() }, { closed++ }, timeoutMs = 10)
        })
        assertEquals(1, closed)
    }
    @Test fun callerCancellationStillFinishesBoundedCleanupBeforeLeaseRelease() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val disconnected = CompletableDeferred<Unit>()
        var closed = false
        val job = launch {
            try { started.complete(Unit); awaitCancellation() }
            finally {
                closeGattSession({ cleanupStarted.complete(Unit) }, { disconnected.await() }, { closed = true })
            }
        }
        started.await()
        job.cancel()
        cleanupStarted.await()
        assertFalse(closed)
        assertFalse(job.isCompleted)
        disconnected.complete(Unit)
        job.join()
        assertTrue(closed)
        assertTrue(job.isCancelled)
    }
    @Test fun synchronousDisconnectFailureStillClosesTheClient() = runBlocking {
        var closed = false
        try {
            closeGattSession({ error("platform unavailable") }, { fail("Must not wait") }, { closed = true })
            fail("Platform failure must propagate")
        } catch (_: IllegalStateException) { }
        assertTrue(closed)
    }
}
