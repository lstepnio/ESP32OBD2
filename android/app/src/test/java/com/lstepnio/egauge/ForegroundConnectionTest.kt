package com.lstepnio.egauge

import com.lstepnio.egauge.connection.*
import com.lstepnio.egauge.ui.state.connectionLabel
import com.lstepnio.egauge.ui.state.connectionStatus
import com.lstepnio.egauge.core.designsystem.StatusTone
import com.lstepnio.egauge.ui.state.matchesRunningPayload
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ForegroundConnectionTest {
    @Test fun rememberedTargetIsNeverReplacedByAnotherAdvertiser() {
        assertEquals("owner", automaticCandidateId(listOf("other", "owner"), "owner"))
        assertNull(automaticCandidateId(listOf("other"), "owner"))
        assertNull(automaticCandidateId(listOf("one", "two"), null))
        assertEquals("one", automaticCandidateId(listOf("one"), null))
        assertNull(automaticCandidateId(emptyList(), null))
    }

    @Test fun retryBackoffIsBoundedAndReadyExpires() {
        assertEquals(listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L, 30_000L), (1..6).map(::reconnectDelayMs))
        val ready = ConnectionState(ConnectionPhase.Ready, 1_000)
        assertTrue(ready.fresh(31_000)); assertFalse(ready.fresh(31_001)); assertFalse(ready.fresh(999))
        assertFalse(ready.copy(phase = ConnectionPhase.Retrying).fresh(1_001))
        assertEquals("Not connected", connectionLabel(ready, 31_001))
        assertEquals(StatusTone.Success, connectionStatus(ready, 31_000).tone)
        assertEquals(StatusTone.Stale, connectionStatus(ready, 31_001).tone)
        assertEquals(StatusTone.Stale, connectionStatus(ready, 999).tone)
    }

    @Test fun reopeningOnlyRecognizesTheExactHealthyRunningPayload() {
        val bytes = "exact reviewed payload".toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val runtime = GaugeConfigTransferClient.RuntimeIdentity(true, false, false, 8, 8, digest)
        assertTrue(matchesRunningPayload(bytes, 8, digest, runtime))
        assertFalse(matchesRunningPayload("changed payload".toByteArray(), 8, digest, runtime))
        assertFalse(matchesRunningPayload(bytes, 8, digest, runtime.copy(trial = true)))
        assertFalse(matchesRunningPayload(bytes, 8, digest, runtime.copy(usedPreviousGeneration = true)))
        assertFalse(matchesRunningPayload(bytes, 9, digest, runtime))
        assertFalse(matchesRunningPayload(null, 8, digest, runtime))
    }

    @Test fun foregroundStartsOnceBackgroundClosesAndResumeRetries() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var starts = 0
        var closes = 0
        val controller = ForegroundConnectionController(scope, OperationCoordinator()) {
            starts++
            try { awaitCancellation() } finally { closes++ }
        }
        try {
            delay(20); assertEquals(0, starts)
            controller.setForeground(true); await { starts == 1 }
            controller.setForeground(true); delay(20); assertEquals(1, starts)
            controller.setForeground(false); await { closes == 1 }
            delay(20); assertEquals(1, starts)
            controller.setForeground(true); await { starts == 2 }
        } finally { scope.cancel() }
    }

    @Test fun userTransferWaitsForAutomaticCleanupAndIsNeverCancelledByBackground() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var starts = 0
        var cleaned = false
        val controller = ForegroundConnectionController(scope, OperationCoordinator()) {
            starts++
            try { awaitCancellation() } finally { withContext(NonCancellable) { delay(25); cleaned = true } }
        }
        try {
            controller.setForeground(true); await { starts == 1 }
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val user = async {
                controller.runUserOperation(OperationKind.CONFIGURATION) {
                    assertTrue(cleaned); entered.complete(Unit); finish.await(); "confirmed"
                }
            }
            entered.await()
            controller.setForeground(false)
            delay(30)
            assertTrue(user.isActive); assertEquals(1, starts)
            finish.complete(Unit)
            assertEquals("confirmed", user.await())
            controller.setForeground(true); await { starts == 2 }
        } finally { scope.cancel() }
    }

    @Test fun failedReadCanRetryWithoutAUserTap() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var tries = 0
        val controller = ForegroundConnectionController(scope, OperationCoordinator()) { tries++; 10L }
        try {
            controller.setForeground(true); await { tries >= 3 }
            controller.setForeground(false)
            delay(20); val stopped = tries
            delay(30); assertEquals(stopped, tries)
        } finally { scope.cancel() }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(2_000) { while (!predicate()) delay(2) }
}
