package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
class OwnerReadRecoveryTest {
    @Test fun invalidOffsetCanSelectOnlyReadStatusOnce() {
        assertArrayEquals(byteArrayOf(0x17, 1, 0, 0, 0), ownerReadResetCommand(7, false))
        assertNull(ownerReadResetCommand(7, true))
    }
    @Test fun authenticationAndOtherFailuresNeverResetOrBypassOwnerChecks() {
        for (status in listOf(0, 5, 8, 13, 15, 133)) assertNull(ownerReadResetCommand(status, false))
    }
    @Test fun transientReadStillNeedsSuccessfulProtectedProofBeforeContinuing() = runBlocking {
        val frame = ByteArray(64).also { it[0] = 3 }
        val replies = ArrayDeque(listOf(OwnerStateRead(133, frame),
            OwnerStateRead(5, byteArrayOf()), OwnerStateRead(0, frame)))
        var pauses = 0
        establishProtectedOwner({ replies.removeFirst() }, { fail("No status-reset write expected") }, { pauses++ })
        assertTrue(replies.isEmpty())
        assertEquals(2, pauses)
    }
    @Test fun persistentTransientFailureStopsAfterFourReadsWithoutSendingCommands() = runBlocking {
        var reads = 0
        var pauses = 0
        try {
            establishProtectedOwner({ reads++; OwnerStateRead(133, byteArrayOf(0)) },
                { fail("Failed ownership cannot send a command") }, { pauses++ })
            fail("Persistent read failure must stop")
        } catch (_: IllegalStateException) { }
        assertEquals(4, reads)
        assertEquals(3, pauses)
    }
    @Test fun deniedOrMalformedProofFailsImmediatelyAndLegacyResetRunsOnlyOnce() = runBlocking {
        for (status in listOf(0, 8, 13, 7)) {
            var reads = 0
            var resets = 0
            try {
                establishProtectedOwner({ reads++; OwnerStateRead(status, byteArrayOf(0)) },
                    { resets++ }, { fail("A permanent failure cannot delay/retry") })
                fail("Invalid proof must fail")
            } catch (_: IllegalStateException) { }
            assertEquals(if (status == 7) 2 else 1, reads)
            assertEquals(if (status == 7) 1 else 0, resets)
        }
    }
    @Test fun cancellationDuringAuthenticationIsPropagatedWithoutAnotherRead() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        var reads = 0
        val job = launch {
            establishProtectedOwner({ reads++; OwnerStateRead(133, byteArrayOf()) },
                { fail("No command expected") }, { waiting.complete(Unit); awaitCancellation() })
            fail("Cancelled authentication cannot succeed")
        }
        waiting.await()
        job.cancelAndJoin()
        assertEquals(1, reads)
        assertTrue(job.isCancelled)
    }
}
