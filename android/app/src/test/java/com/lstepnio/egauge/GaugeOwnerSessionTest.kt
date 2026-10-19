package com.lstepnio.egauge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GaugeOwnerSessionTest {
    private class Channel : GaugeOwnerChannel {
        override val mtu = 173
        var reads = 0
        val writes = mutableListOf<ByteArray>()
        var reader: suspend () -> OwnerStateRead = { OwnerStateRead(0, status()) }
        var writer: suspend (ByteArray) -> Unit = { writes += it }
        override suspend fun read(): OwnerStateRead { reads++; return reader() }
        override suspend fun write(bytes: ByteArray) = writer(bytes)
    }

    @Test fun severalTransactionsUseOneAuthenticatedChannel() = runBlocking {
        val channel = Channel()
        useGaugeOwnerSession(channel) { session ->
            repeat(3) { sequence ->
                session.transaction {
                    assertEquals(173, mtu)
                    writeRaw(byteArrayOf(0x35, sequence.toByte(), 0, 0, 0))
                    assertArrayEquals(status(), readRaw())
                }
            }
        }
        assertEquals(4, channel.reads) // One owner read, then three transaction reads.
        assertEquals(3, channel.writes.size)
    }

    @Test fun failedAuthenticationNeverExposesATransaction() = runBlocking {
        val channel = Channel().apply { reader = { OwnerStateRead(0, byteArrayOf(99)) } }
        var exposed = false
        try { useGaugeOwnerSession(channel) { exposed = true }; fail("Must reject malformed owner state") }
        catch (_: IllegalStateException) { }
        assertFalse(exposed)
        assertTrue(channel.writes.isEmpty())
    }

    @Test fun authenticationStillPerformsTheExistingReadOnlyReset() = runBlocking {
        val channel = Channel()
        channel.reader = { if (channel.reads == 1) OwnerStateRead(7, byteArrayOf()) else OwnerStateRead(0, status()) }
        useGaugeOwnerSession(channel) { it.transaction { readRaw() } }
        assertEquals(3, channel.reads)
        assertArrayEquals(byteArrayOf(0x17, 1, 0, 0, 0), channel.writes.single())
    }

    @Test fun overlappingTransactionsDoNotInterleaveCommandAndStatus() = runBlocking {
        val channel = Channel()
        val firstWritten = CompletableDeferred<Unit>()
        val continueFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        useGaugeOwnerSession(channel) { session ->
            coroutineScope {
                val first = async {
                    session.transaction {
                        writeRaw(byteArrayOf(1)); order += "write1"; firstWritten.complete(Unit)
                        continueFirst.await(); readRaw(); order += "read1"
                    }
                }
                firstWritten.await()
                val second = async(start = CoroutineStart.UNDISPATCHED) {
                    session.transaction { writeRaw(byteArrayOf(2)); order += "write2"; readRaw(); order += "read2" }
                }
                assertEquals(listOf("write1"), order)
                continueFirst.complete(Unit)
                first.await(); second.await()
            }
        }
        assertEquals(listOf("write1", "read1", "write2", "read2"), order)
    }

    @Test fun cancelledWaitingTransactionDoesNotRetireTheActiveOwner() = runBlocking {
        val channel = Channel()
        useGaugeOwnerSession(channel) { session ->
            coroutineScope {
                val entered = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                val first = launch { session.transaction { entered.complete(Unit); finish.await(); readRaw() } }
                entered.await()
                val waiting = launch(start = CoroutineStart.UNDISPATCHED) { session.transaction { fail("Cancelled waiter ran") } }
                waiting.cancelAndJoin(); finish.complete(Unit); first.join()
                session.transaction { writeRaw(byteArrayOf(3)) }
            }
        }
        assertEquals(1, channel.writes.size)
    }

    @Test fun cancellationRetiresConnectionBeforeTheNextTransaction() = runBlocking {
        val channel = Channel()
        useGaugeOwnerSession(channel) { session ->
            val entered = CompletableDeferred<Unit>()
            val job = launch { session.transaction { writeRaw(byteArrayOf(1)); entered.complete(Unit); awaitCancellation() } }
            entered.await(); job.cancelAndJoin()
            expectRetired { session.transaction { writeRaw(byteArrayOf(2)) } }
        }
        assertEquals(1, channel.writes.size)
    }

    @Test fun caughtWriteFailureStillPoisonsSessionAndIsNeverReplayed() = runBlocking {
        val channel = Channel()
        var attempts = 0
        channel.writer = { attempts++; throw GaugeCommandRejectedException(5) }
        useGaugeOwnerSession(channel) { session ->
            session.transaction {
                try { writeRaw(byteArrayOf(1)) } catch (_: GaugeCommandRejectedException) { }
                expectRetired { writeRaw(byteArrayOf(2)) }
            }
            expectRetired { session.transaction { readRaw() } }
        }
        assertEquals(1, attempts)
    }

    @Test fun transientReadPressureRetriesOnlyRead() = runBlocking {
        val channel = Channel()
        channel.reader = { OwnerStateRead(if (channel.reads == 2) 17 else 0, status()) }
        useGaugeOwnerSession(channel) { it.transaction { writeRaw(byteArrayOf(1)); readRaw() } }
        assertEquals(3, channel.reads)
        assertEquals(1, channel.writes.size)
    }

    @Test fun malformedOrFailedReadIsNotReinterpretedAsAuthenticationRecovery() = runBlocking {
        val channel = Channel()
        channel.reader = { OwnerStateRead(if (channel.reads == 1) 0 else 133, status()) }
        useGaugeOwnerSession(channel) { session ->
            try { session.transaction { readRaw() }; fail("Failed read must propagate") }
            catch (_: GaugeLinkException) { }
            expectRetired { session.transaction { readRaw() } }
        }
        assertEquals(2, channel.reads)
        assertTrue(channel.writes.isEmpty())
    }

    @Test fun handlesCannotBeUsedAfterTransactionOrFactoryExit() = runBlocking {
        val channel = Channel()
        lateinit var escapedIo: GaugeOwnerTransaction
        lateinit var escapedSession: GaugeOwnerSession
        useGaugeOwnerSession(channel) { session ->
            escapedSession = session
            session.transaction { escapedIo = this }
            expectRetired { escapedIo.writeRaw(byteArrayOf(1)) }
        }
        expectRetired { escapedSession.transaction { readRaw() } }
        assertEquals(1, channel.reads)
        assertTrue(channel.writes.isEmpty())
    }

    @Test fun cleanFactoryExitAlsoRetiresTheSession() = runBlocking {
        val channel = Channel()
        lateinit var escapedSession: GaugeOwnerSession
        useGaugeOwnerSession(channel) { escapedSession = it; it.transaction { readRaw() } }
        expectRetired { escapedSession.transaction { writeRaw(byteArrayOf(1)) } }
        assertTrue(channel.writes.isEmpty())
    }

    @Test fun recursiveTransactionFailsPromptlyInsteadOfWaitingOnItsOwnMutex() = runBlocking {
        withTimeout(1_000) {
            useGaugeOwnerSession(Channel()) { session ->
                session.transaction {
                    expectRetired { session.transaction { fail("Must not enter recursively") } }
                    readRaw() // No IO failed, so catching the rejected nesting is harmless.
                    Unit
                }
            }
        }
    }

    @Test fun escapedInFlightReadCannotOverlapTheNextTransactionsAttOperation() = runBlocking {
        val channel = Channel()
        val started = CompletableDeferred<Unit>()
        val finishRead = CompletableDeferred<Unit>()
        channel.reader = {
            if (channel.reads > 1) { started.complete(Unit); finishRead.await() }
            OwnerStateRead(0, status())
        }
        // Deliberately model an incorrectly detached caller. The session must still fence its IO.
        val detached = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            useGaugeOwnerSession(channel) { session ->
                supervisorScope {
                    lateinit var staleRead: Deferred<ByteArray>
                    session.transaction {
                        val io = this
                        staleRead = detached.async(start = CoroutineStart.UNDISPATCHED) { io.readRaw() }
                        started.await()
                    }
                    val next = async(start = CoroutineStart.UNDISPATCHED) {
                        session.transaction { writeRaw(byteArrayOf(2)) }
                    }
                    assertFalse(next.isCompleted)
                    assertTrue(channel.writes.isEmpty())
                    finishRead.complete(Unit)
                    expectRetired { staleRead.await() }
                    expectRetired { next.await() }
                }
            }
        } finally { detached.cancel() }
        assertTrue(channel.writes.isEmpty())
    }

    @Test fun nextOperationWaitsForCancelledSessionCleanup() = runBlocking {
        val coordinator = OperationCoordinator()
        val entered = CompletableDeferred<Unit>()
        val cleaning = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        val operation = launch {
            coordinator.run(OperationKind.READ) {
                try { useGaugeOwnerSession(Channel()) { it.transaction { entered.complete(Unit); awaitCancellation() } } }
                finally { closeGattSession({ cleaning.complete(Unit) }, { finishCleanup.await() }, {}) }
            }
        }
        entered.await(); operation.cancel(); cleaning.await()
        try { coordinator.run(OperationKind.CONFIGURATION) { fail("Cleanup still owns lease") } }
        catch (_: OperationBusyException) { }
        finishCleanup.complete(Unit); operation.join()
        assertEquals("next", coordinator.run(OperationKind.CONFIGURATION) { "next" })
    }

    private suspend fun expectRetired(action: suspend () -> Unit) {
        try { action(); fail("Retired handle must reject IO") } catch (_: IllegalStateException) { }
    }

    companion object {
        private fun status() = ByteArray(64).apply { this[0] = 3 }
    }
}
