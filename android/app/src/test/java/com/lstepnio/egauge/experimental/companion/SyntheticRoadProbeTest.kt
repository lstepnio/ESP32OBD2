package com.lstepnio.egauge.experimental.companion

// BEGIN EXPERIMENT: companion-content
// Removal: delete this package and its tests; see docs/development/companion-session-experiment.md.

import com.lstepnio.egauge.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Scripted transport replies, not a simulated implementation of firmware application/expiry. */
class SyntheticRoadProbeTest {
    private val scope = SyntheticProbeScope("fixture-car", "fixture-gauge", 7, 50, "00".repeat(32))
    private class ScriptedChannel(vararg replies: ByteArray, override val mtu: Int = 173) : GaugeOwnerChannel {
        val pending = ArrayDeque(replies.toList())
        val writes = mutableListOf<ByteArray>()
        override suspend fun read() = OwnerStateRead(0, pending.removeFirst())
        override suspend fun write(bytes: ByteArray) {
            writes += bytes.copyOf()
        }
    }

    @Test fun showReadbackAndWithdrawalReuseOneSessionAndNeverSendLiveContent() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), empty(), notice("T1"), runtime(), notice("T1"), empty())
        var sequence = 0L
        useGaugeOwnerSession(channel) { session ->
            val probe = SyntheticRoadProbe(session, scope, { scope }, { ++sequence }, { 1_000 })
            assertTrue(probe.show().events.single().simulated)
            assertTrue(probe.withdraw().events.isEmpty())
        }
        assertTrue(channel.pending.isEmpty())
        val content = channel.writes.filter { it[0] == 0x3e.toByte() }
        assertEquals(2, content.size)
        assertArrayEquals(
            AlertCodec.external(
                0,
                7,
                1,
                AlertSeverity.Advisory,
                3_000,
                "Phone alert test",
                "T1",
                true
            ),
            content[0]
        )
        assertArrayEquals(
            AlertCodec.external(
                0,
                7,
                2,
                AlertSeverity.Advisory,
                0,
                "Phone alert test",
                "T2",
                true
            ),
            content[1]
        )
        assertTrue(channel.writes.all { it[0].toInt() and 255 in setOf(0x33, 0x3c, 0x3e) })
    }

    @Test fun queuedWriteIsNotReportedAsAppliedAndIsNeverRetried() = runBlocking {
        val replies = arrayOf(empty(), runtime(), empty(), *Array(10) { empty() })
        val channel = ScriptedChannel(*replies)
        useGaugeOwnerSession(channel) { session ->
            val probe = SyntheticRoadProbe(session, scope, { scope }, { 1 }, { 0 }, pause = {})
            expectFailure { probe.show() }
            expectFailure { session.transaction { readRaw() } }
        }
        assertEquals(1, channel.writes.count { it[0] == 0x3e.toByte() })
    }

    @Test fun anotherProducersSlotIsNeverOverwrittenOrWithdrawn() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), notice("other", simulated = false))
        useGaugeOwnerSession(channel) { session ->
            expectFailure { SyntheticRoadProbe(session, scope, { scope }, { 1 }, { 0 }).show() }
        }
        assertFalse(channel.writes.any { it[0] == 0x3e.toByte() })
    }

    @Test fun evenAnOldSyntheticSlotIsNotAssumedToBelongToThisProbe() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), notice("T1"))
        useGaugeOwnerSession(channel) { session ->
            expectFailure { SyntheticRoadProbe(session, scope, { scope }, { 2 }, { 0 }).withdraw() }
        }
        assertFalse(channel.writes.any { it[0] == 0x3e.toByte() })
    }

    @Test fun scopeSwitchWhileReservingSequenceCannotRetargetContent() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), empty())
        var current = scope
        useGaugeOwnerSession(channel) { session ->
            val probe = SyntheticRoadProbe(session, scope, { current }, {
                current = scope.copy(vehicle = "other-car")
                1
            }, { 0 })
            expectFailure { probe.show() }
        }
        assertFalse(channel.writes.any { it[0] == 0x3e.toByte() })
    }

    @Test fun changedRevisionDigestOrBootCannotSendContent() = runBlocking {
        for ((identity, snapshot) in listOf(
            runtime(revision = 51) to empty(),
            runtime(digest = 1) to empty(),
            runtime() to empty(boot = 8)
        )) {
            val channel = ScriptedChannel(empty(), identity, snapshot)
            useGaugeOwnerSession(channel) { session ->
                expectFailure { SyntheticRoadProbe(session, scope, { scope }, { 1 }, { 0 }).show() }
            }
            assertFalse(channel.writes.any { it[0] == 0x3e.toByte() })
        }
    }

    @Test fun reservationDelayConsumesLifetimeInsteadOfRejuvenatingTheFixture() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), empty())
        var now = 100L
        useGaugeOwnerSession(channel) { session ->
            expectFailure {
                SyntheticRoadProbe(session, scope, { scope }, {
                    now += 3_000
                    1
                }, { now }).show()
            }
        }
        assertFalse(channel.writes.any { it[0] == 0x3e.toByte() })
    }

    @Test fun duplicateReservationIsRejectedBeforeAnotherWrite() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), empty(), notice("T1"), runtime(), notice("T1"))
        useGaugeOwnerSession(channel) { session ->
            val probe = SyntheticRoadProbe(session, scope, { scope }, { 1 }, { 0 })
            probe.show()
            expectFailure { probe.show() }
        }
        assertEquals(1, channel.writes.count { it[0] == 0x3e.toByte() })
    }

    @Test fun smallMtuDoesNotGuessLongWriteOrCreateANewWireProtocol() = runBlocking {
        val channel = ScriptedChannel(empty(), mtu = 23)
        useGaugeOwnerSession(channel) { session ->
            expectFailure { SyntheticRoadProbe(session, scope, { scope }, { 1 }, { 0 }).show() }
        }
        assertTrue(channel.writes.isEmpty())
    }

    @Test fun cancellationDoesNotAttemptAnUncertainWithdrawal() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), empty(), empty())
        val polling = CompletableDeferred<Unit>()
        useGaugeOwnerSession(channel) { session ->
            val probe = SyntheticRoadProbe(session, scope, { scope }, { 1 }, { 0 }, pause = {
                polling.complete(Unit)
                awaitCancellation()
            })
            val job = launch { probe.show() }
            polling.await()
            job.cancelAndJoin()
            expectFailure { probe.withdraw() }
        }
        assertEquals(1, channel.writes.count { it[0] == 0x3e.toByte() })
    }

    @Test fun malformedReadbackIsNotAcceptedAsSuccessfulPresentation() = runBlocking {
        val channel = ScriptedChannel(empty(), runtime(), empty(), byteArrayOf(99))
        useGaugeOwnerSession(channel) { session ->
            expectFailure { SyntheticRoadProbe(session, scope, { scope }, { 1 }, { 0 }).show() }
        }
        assertEquals(1, channel.writes.count { it[0] == 0x3e.toByte() })
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Invalid/unconfirmed synthetic transaction must fail")
        } catch (_: IllegalStateException) { } catch (_: IllegalArgumentException) { }
    }

    private fun runtime(revision: Long = 50, digest: Int = 0) = ByteArray(44).apply {
        this[0] = 8
        this[1] = 1
        AlertCodec.le32(revision).copyInto(this, 4)
        AlertCodec.le32(revision).copyInto(this, 8)
        this[12] = digest.toByte()
    }

    private fun empty(boot: Long = 7) = ByteArray(24).apply {
        this[0] = 16
        this[1] = 1
        this[2] = 2
        AlertCodec.le32(boot).copyInto(this, 4)
    }

    private fun notice(context: String, simulated: Boolean = true): ByteArray = // Adapt the production C parity vector only for scripted fake-transport replies.
        javaClass.getResourceAsStream("/alert-events-v16.hex")!!.bufferedReader().use { it.readText() }
            .trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray().apply {
                this[1] = 1
                this[40] = 36
                this[42] = AlertSeverity.Advisory.ordinal.toByte()
                this[47] = if (simulated) 1 else 0
                fill(0, 56, 100)
                "Phone alert test".toByteArray().copyInto(this, 56)
                context.toByteArray().copyInto(this, 88)
            }
}

// END EXPERIMENT: companion-content
