package com.lstepnio.egauge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WifiUpdateBootstrapTest {
    private class Owner : WifiUpdateBootstrapOwner {
        val events = mutableListOf<String>()
        val boot = GaugeConfigTransferClient.BootIdentity(2, 16, 1, "a".repeat(64), "fixture", 0x60000)
        val wifi = WifiBulkSession(byteArrayOf(192.toByte(), 168.toByte(), 4, 1), 1234, 1, ByteArray(32), 60, "fixture", "password")
        var read: suspend () -> GaugeConfigTransferClient.BootIdentity = { boot }
        var open: suspend () -> WifiBulkSession = { wifi }
        var cleanup: suspend () -> Unit = {}
        override suspend fun <T> withSession(action: suspend WifiUpdateBootstrapSession.() -> T): T {
            events += "owner-open"
            try {
                return action(object : WifiUpdateBootstrapSession {
                    override suspend fun identity(): GaugeConfigTransferClient.BootIdentity {
                        events += "identity"
                        return read()
                    }
                    override suspend fun openWifi(): WifiBulkSession {
                        events += "wifi-start"
                        return open()
                    }
                })
            } finally {
                events += "owner-closed"
            }
        }
        override suspend fun closeWifi() {
            events += "wifi-close"
            cleanup()
        }
    }

    @Test fun oneOwnerSessionReadsIdentityBeforeStartingWifiAndReturnsAuthenticatedValues() = runBlocking {
        val owner = Owner()
        val result = bootstrapWifiUpdate("b".repeat(64), owner)
        assertSame(owner.boot, result.before)
        assertSame(owner.wifi, result.wifi)
        assertEquals(listOf("owner-open", "identity", "wifi-start", "owner-closed"), owner.events)
    }

    @Test fun alreadyRunningImageNeverStartsOrClosesWifi() = runBlocking {
        val owner = Owner()
        assertTrue(runCatching { bootstrapWifiUpdate(owner.boot.elfSha256, owner) }.exceptionOrNull() is IllegalStateException)
        assertEquals(listOf("owner-open", "identity", "owner-closed"), owner.events)
    }

    @Test fun failedProtectedIdentityNeverAttemptsWifi() = runBlocking {
        val owner = Owner()
        val failure = IllegalStateException("malformed or unavailable identity")
        owner.read = { throw failure }
        assertSame(failure, runCatching { bootstrapWifiUpdate("b".repeat(64), owner) }.exceptionOrNull())
        assertEquals(listOf("owner-open", "identity", "owner-closed"), owner.events)
    }

    @Test fun uncertainWifiStartClosesGattBeforeCleanupAndPreservesTheOriginalFailure() = runBlocking {
        val owner = Owner()
        val failure = IllegalStateException("AP reply lost")
        owner.open = { throw failure }
        owner.cleanup = { error("cleanup unavailable") }
        assertSame(failure, runCatching { bootstrapWifiUpdate("b".repeat(64), owner) }.exceptionOrNull())
        assertEquals(listOf("owner-open", "identity", "wifi-start", "owner-closed", "wifi-close"), owner.events)
    }

    @Test fun cleanupSessionTimeoutCannotReplaceTheUncertainStartupFailure() = runBlocking {
        val owner = Owner()
        val failure = IllegalStateException("AP response unavailable")
        owner.open = { throw failure }
        owner.cleanup = { withTimeout(1) { awaitCancellation() } }
        assertSame(failure, runCatching { bootstrapWifiUpdate("b".repeat(64), owner) }.exceptionOrNull())
        assertEquals("wifi-close", owner.events.last())
    }

    @Test fun cancellationAfterWifiRequestFinishesCleanupWithoutRetryOrSuccess() = runBlocking {
        val owner = Owner()
        val started = CompletableDeferred<Unit>()
        owner.open = {
            started.complete(Unit)
            awaitCancellation()
        }
        owner.cleanup = {
            delay(1)
            owner.events += "cleanup-finished"
        }
        val job = launch {
            bootstrapWifiUpdate("b".repeat(64), owner)
            fail("cancelled bootstrap succeeded")
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(listOf("owner-open", "identity", "wifi-start", "owner-closed", "wifi-close", "cleanup-finished"), owner.events)
    }

    @Test fun cancellationDuringIdentityDoesNotMutateWifi() = runBlocking {
        val owner = Owner()
        val started = CompletableDeferred<Unit>()
        owner.read = {
            started.complete(Unit)
            awaitCancellation()
        }
        val job = launch { bootstrapWifiUpdate("b".repeat(64), owner) }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf("owner-open", "identity", "owner-closed"), owner.events)
    }
}
