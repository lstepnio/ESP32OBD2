package com.lstepnio.egauge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RestartReadTest {
    @Test fun sessionTimeoutLeavesTheCallerActiveForTheNextProtectedRead() = runBlocking {
        assertNull(restartRead { withTimeout(1) { delay(30); "late" } })
        currentCoroutineContext().ensureActive()
        assertEquals("confirmed", restartRead { "confirmed" })
    }
    @Test fun attemptDeadlineAndUnavailableLinkAreRetryable() = runBlocking {
        assertNull(restartRead(1) { delay(30); "late" })
        assertNull(restartRead<String> { error("link unavailable") })
        var invoked = false
        assertNull(restartRead(0) { invoked = true; "unexpected" })
        assertFalse(invoked)
    }
    @Test fun realCallerCancellationStillClosesTheReadAndIsPropagated() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var closed = false
        val job = launch {
            restartRead {
                try { started.complete(Unit); awaitCancellation() }
                finally { closed = true }
            }
            fail("Cancelled confirmation must not complete")
        }
        started.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(closed)
    }
}
