package com.lstepnio.egauge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StagedTransferCleanupTest {
    private val events = mutableListOf<String>()
    private val writes = mutableListOf<Int>()
    private var opened = 0
    private suspend fun <T> owner(pressure: Boolean = false, action: suspend GaugeOwnerTransaction.() -> T): T {
        val index = ++opened
        events += "open$index"
        var reads = 0
        val channel = object : GaugeOwnerChannel {
            override val mtu = 173
            override suspend fun write(bytes: ByteArray) {
                writes += bytes[0].toInt() and 255
            }
            override suspend fun read(): OwnerStateRead {
                if (reads++ == 0) return OwnerStateRead(0, ByteArray(64).apply { this[0] = 3 })
                if (pressure) return OwnerStateRead(0x11, byteArrayOf())
                return OwnerStateRead(
                    0,
                    ByteArray(64).apply {
                        this[0] = 3
                        this[1] = 2
                        this[8] = 42
                    }
                )
            }
        }
        return try {
            useGaugeOwnerSession(channel) { it.transaction(action) }
        } finally {
            events += "close$index"
        }
    }

    @Test fun exhaustedReadPressureClosesRetiredOwnerThenAbortsMatchingConfigOrOtaOnce() = runBlocking {
        for ((chunk, query, abort) in listOf(Triple(0x13, 0x17, 0x16), Triple(0x23, 0x27, 0x26))) {
            events.clear()
            writes.clear()
            opened = 0
            val failure = runCatching {
                withStagedTransferCleanup(
                    operation = { state ->
                        owner(pressure = true) {
                            state.preparing()
                            writeRaw(byteArrayOf(chunk.toByte()))
                            readRaw()
                        }
                    },
                    cleanup = {
                        owner {
                            abortStagedTransfer(42, read = {
                                writeRaw(byteArrayOf(query.toByte()))
                                val value = readRaw()
                                TransferAbortState(value[1].toInt(), value[8].toLong())
                            }, abort = { writeRaw(byteArrayOf(abort.toByte(), 42, 0, 0, 0)) })
                        }
                    }
                )
            }.exceptionOrNull()
            assertTrue(failure is GaugeLinkException)
            assertEquals(listOf(chunk, query, abort), writes)
            assertEquals(listOf("open1", "close1", "open2", "close2"), events)
        }
    }

    @Test fun preflightAndUncertainCommitOrActivationNeverAbort() = runBlocking {
        for (commit in listOf(false, true)) {
            var attempted = false
            runCatching {
                withStagedTransferCleanup(
                    operation = { state ->
                        if (commit) {
                            state.preparing()
                            state.committing()
                        }
                        error("preflight or uncertain commit")
                    },
                    cleanup = { attempted = true }
                )
            }
            assertFalse(attempted)
        }
    }

    @Test fun ownershipAndPhaseMustBothPermitAbort() = runBlocking {
        var aborted = 0
        for (phase in 0..5) {
            for (id in listOf(0L, 41L, 42L, 43L)) {
                val before = aborted
                abortStagedTransfer(42, { TransferAbortState(phase, id) }, { aborted++ })
                assertEquals(if (phase in 1..3 && id == 42L) 1 else 0, aborted - before)
            }
        }
        assertEquals(3, aborted)
    }

    @Test fun missingStatusAndCleanupTimeoutPreservePrimaryFailure() = runBlocking {
        for (timeout in listOf(false, true)) {
            val error = runCatching {
                withStagedTransferCleanup(
                    operation = { state ->
                        state.preparing()
                        error("staging failed")
                    },
                    cleanup = {
                        if (timeout) {
                            withTimeout(1) { awaitCancellation() }
                        } else {
                            abortStagedTransfer(42, { error("status unavailable") }, { fail("Unknown transfer must not abort") })
                        }
                    }
                )
            }.exceptionOrNull()
            assertEquals("staging failed", error?.message)
        }
    }

    @Test fun cancellationKeepsOperationLeaseUntilFreshCleanupCompletes() = runBlocking {
        val coordinator = OperationCoordinator()
        val started = CompletableDeferred<Unit>()
        val cleaning = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val job = launch {
            coordinator.run(OperationKind.CONFIGURATION) {
                withStagedTransferCleanup(
                    operation = { state ->
                        owner {
                            state.preparing()
                            started.complete(Unit)
                            awaitCancellation()
                        }
                    },
                    cleanup = {
                        owner {
                            cleaning.complete(Unit)
                            finish.await()
                        }
                    }
                )
            }
        }
        started.await()
        job.cancel()
        cleaning.await()
        assertEquals(listOf("open1", "close1", "open2"), events)
        try {
            coordinator.run(OperationKind.READ) { fail("Cleanup must retain lease") }
            fail("Must reject overlap")
        } catch (_: OperationBusyException) { }
        finish.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf("open1", "close1", "open2", "close2"), events)
        assertEquals("next", coordinator.run(OperationKind.READ) { "next" })
    }

    @Test fun successfulStageDoesNotRunFailureCleanup() = runBlocking {
        val result = withStagedTransferCleanup(operation = { state ->
            state.preparing()
            "verified"
        }, cleanup = { fail("Success must not abort") })
        assertEquals("verified", result)
    }
}
