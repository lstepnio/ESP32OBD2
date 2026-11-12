package com.lstepnio.egauge

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WifiUpdateBootstrapTest {
    @Test fun transientStartupFailureRetriesOnceBeforeFlash() = runBlocking {
        var attempts = 0
        var retries = 0
        val result = retryWifiUpdateStartup(onRetry = { retries++ }) { stage ->
            attempts++
            stage(FirmwareUpdateStage.CONNECTING_WIFI)
            if (attempts == 1) throw IOException("Connection lost")
            "installed"
        }
        assertEquals("installed", result)
        assertEquals(2, attempts)
        assertEquals(1, retries)
    }

    @Test fun flashPreparationFailureDoesNotReplayTheUpdate() = runBlocking {
        val failure = IOException("Connection lost after preparation started")
        val result = runCatching {
            retryWifiUpdateStartup(onRetry = { fail("Must verify before retrying") }) { stage ->
                stage(FirmwareUpdateStage.PREPARING_FLASH)
                throw failure
            }
        }
        assertSame(failure, result.exceptionOrNull())
    }

    private val privateTag = 0xf2434755L

    private class Owner : WifiUpdateBootstrapOwner {
        val events = mutableListOf<String>()
        val boot = GaugeConfigTransferClient.BootIdentity(2, 16, 1, "a".repeat(64), "fixture", 0x60000)
        val wifi = WifiBulkSession(byteArrayOf(192.toByte(), 168.toByte(), 4, 1), 1234, 1, ByteArray(32), 60, "fixture", "password")
        var read: suspend () -> GaugeConfigTransferClient.BootIdentity = { boot }
        var target: suspend () -> ByteArray = { error("Unexpected private target read") }
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
                    override suspend fun targetIdentity(): ByteArray {
                        events += "target"
                        return target()
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

    private fun targetReply(running: Int = 0x60000, alternate: Int = 0x360000): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        .put(byteArrayOf(1, 1, 0, 0))
        .putInt(privateTag.toInt()).putInt(running).putInt(alternate)
        .putInt(0x300000).putInt(0x300000).putInt(0x10000).putInt(0x2000).array()

    @Test fun privateTargetAndBootAreReadBeforeOpeningWifiInSameSession() = runBlocking {
        assertArrayEquals(byteArrayOf(0x31, 1, 0, 0, 0, 1), privateOtaTargetRequest())
        val owner = Owner()
        owner.read = { owner.boot.copy(partitionAddress = 0x360000) }
        owner.target = { targetReply(0x360000, 0x60000) }
        val result = bootstrapWifiUpdate("b".repeat(64), owner, privateTag)
        assertSame(owner.wifi, result.wifi)
        assertEquals(listOf("owner-open", "identity", "target", "wifi-start", "owner-closed"), owner.events)
    }

    @Test fun invalidPrivateTargetNeverOpensOrClosesWifi() = runBlocking {
        val invalidReplies = listOf(
            targetReply().copyOf(31),
            targetReply().apply { this[0] = 2 },
            targetReply().apply { this[1] = 0 },
            targetReply().apply { this[2] = 1 },
            targetReply().apply { this[4] = 0 },
            targetReply(0x360000, 0x60000),
            targetReply(alternate = 0x60000),
            targetReply().apply { this[16] = 1 },
            targetReply().apply { this[20] = 1 },
            targetReply().apply { this[24] = 1 },
            targetReply().apply { this[28] = 1 }
        )
        for (reply in invalidReplies) {
            val owner = Owner()
            owner.target = { reply }
            assertTrue(runCatching { bootstrapWifiUpdate("b".repeat(64), owner, privateTag) }.isFailure)
            assertEquals(listOf("owner-open", "identity", "target", "owner-closed"), owner.events)
        }
    }

    @Test fun unconfirmedPrivateBootNeverRequestsTargetOrOpensWifi() = runBlocking {
        val owner = Owner()
        owner.read = { owner.boot.copy(otaState = 1) }
        assertTrue(runCatching { bootstrapWifiUpdate("b".repeat(64), owner, privateTag) }.isFailure)
        assertEquals(listOf("owner-open", "identity", "owner-closed"), owner.events)
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
