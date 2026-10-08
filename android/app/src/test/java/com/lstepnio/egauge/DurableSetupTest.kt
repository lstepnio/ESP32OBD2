package com.lstepnio.egauge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class DurableSetupTest {
    private fun initial() = LocalSetup(
        ProfileCollection("one", listOf(VehicleProfile("one", "One", Draft()), VehicleProfile("two", "Two", Draft()))),
        GaugeAssociations("gauge", listOf(KnownGauge("gauge", "eGauge", "one", "ECM"), KnownGauge("other", "Other", "two", "ECM"))))

    @Test fun failedSaveCannotAcknowledgeOrRetargetTheVisibleSetup() = runBlocking<Unit> {
        val before = initial()
        var stored = before
        var acknowledged = before
        val repository = LocalSetupTransactions({ stored }, { false }, DurableWrites())
        try { acknowledged = repository.update { it.select("two") }; fail("Disk failure must propagate") }
        catch (_: LocalSetupSaveFailure) { }
        assertEquals(before, acknowledged)
        assertEquals(before, stored)
    }

    @Test fun createdVehicleAndAssignmentRecoverTogetherAfterRestart() = runBlocking<Unit> {
        var stored = initial()
        val repository = LocalSetupTransactions({ stored }, { stored = it; true }, DurableWrites())
        val created = repository.update { it.create(VehicleProfile("three", "Three", Draft())) }
        assertEquals("three", created.profiles.activeId)
        assertEquals("three", created.associations.gauges.first().vehicleId)
        assertEquals("two", created.associations.gauges.last().vehicleId)
        val restarted = LocalSetupTransactions({ stored }, { stored = it; true }, DurableWrites())
        assertEquals(created, restarted.update { it })
        assertEquals(created.profiles, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(created.profiles)))
        assertEquals(created.associations, GaugeAssociationDocumentCodec.decode(GaugeAssociationDocumentCodec.encode(created.associations)))
    }

    @Test fun cancellationWaitsForAdmittedCommitBeforeAnotherWriterEnters() = runBlocking<Unit> {
        val writes = DurableWrites()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val concurrent = AtomicInteger(0)
        val commits = mutableListOf<Int>()
        val first = launch {
            writes.write {
                assertEquals(1, concurrent.incrementAndGet())
                entered.complete(Unit)
                check(release.await(2, TimeUnit.SECONDS))
                commits += 1
                concurrent.decrementAndGet()
            }
        }
        entered.await()
        first.cancel()
        val second = async {
            writes.write {
                assertEquals(1, concurrent.incrementAndGet())
                commits += 2
                concurrent.decrementAndGet()
            }
        }
        yield()
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        release.countDown()
        withTimeout(3_000) { first.join(); second.await() }
        assertEquals(listOf(1, 2), commits)
    }

    @Test fun waitingWriterCancellationDoesNotRunItsMutation() = runBlocking<Unit> {
        val writes = DurableWrites()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val first = launch { writes.write { entered.complete(Unit); check(release.await(2, TimeUnit.SECONDS)) } }
        entered.await()
        var invoked = false
        val queued = launch { writes.write { invoked = true } }
        yield(); queued.cancelAndJoin(); release.countDown(); first.join()
        assertFalse(invoked)
    }

    @Test fun seededFailuresAndVehicleSwitchesKeepProfilesAndAssignmentsConsistent() = runBlocking<Unit> {
        val random = Random(47)
        var stored = initial()
        var shouldFail = false
        val repository = LocalSetupTransactions({ stored }, { if (shouldFail) false else { stored = it; true } }, DurableWrites())
        repeat(150) {
            val before = stored
            shouldFail = random.nextInt(4) == 0
            val target = if (random.nextBoolean()) "one" else "two"
            try {
                val committed = repository.update { it.select(target) }
                assertEquals(target, committed.profiles.activeId)
                assertEquals(target, committed.associations.gauges.first().vehicleId)
            } catch (_: LocalSetupSaveFailure) { assertEquals(before, stored) }
            assertEquals("two", stored.associations.gauges.last().vehicleId)
        }
    }

    @Test fun attachingTransmissionCommitsParentAndAllAffectedGaugeAssignmentsTogether() = runBlocking<Unit> {
        val engine = VehicleProfile("engine", "Engine", Draft(), AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"))
        val legacy = VehicleProfile("legacy", "Transmission", TransmissionSetup.draft(),
            AdapterBinding("tcm", "AA:BB:CC:DD:EE:02", "public"))
        val before = LocalSetup(ProfileCollection("legacy", listOf(engine, legacy)),
            GaugeAssociations("gauge", listOf(KnownGauge("gauge", "Gauge", "legacy", "TCM"),
                KnownGauge("other", "Other", "engine", "ECM"))))
        var stored = before
        var failCommit = true
        val repository = LocalSetupTransactions({ stored }, { if (failCommit) false else { stored = it; true } }, DurableWrites())
        try { repository.update { it.attachTransmission("engine") }; fail() } catch (_: LocalSetupSaveFailure) { }
        assertEquals(before, stored)
        failCommit = false
        val saved = repository.update { it.attachTransmission("engine") }
        assertEquals("engine", saved.profiles.activeId)
        assertEquals(1, saved.profiles.profiles.size)
        assertEquals(legacy.primaryAdapter, saved.profiles.active.transmission?.adapter)
        assertEquals("engine", saved.associations.gauges.first().vehicleId)
        assertEquals("TCM", saved.associations.gauges.first().source)
        assertEquals(before.associations.gauges.last(), saved.associations.gauges.last())
    }

    @Test fun unknownCandidateCannotClearRecoveryBeforeRunningHealthConfirmation() {
        val pending = PendingUpdateRecovery("unknown", "unknown", null, "unreadable", 0)
        for (state in listOf(-1, 0, 1, 3)) {
            assertFalse(reconcilePendingUpdate(pending, "gauge", "ab".repeat(32), state).terminal)
        }
        assertTrue(reconcilePendingUpdate(pending, "gauge", "ab".repeat(32), 2).terminal)
        assertFalse(reconcilePendingUpdate(pending.copy(expectedElfSha256 = "cd".repeat(32)),
            "gauge", "ab".repeat(32), 1).terminal)
    }

    @Test fun suspendResultPreservesCancellationAndDoesNotCatchFatalErrors() = runBlocking<Unit> {
        try { suspendResult<Unit> { throw CancellationException("cancel") }; fail() } catch (_: CancellationException) { }
        try { suspendResult<Unit> { throw AssertionError("fatal") }; fail() } catch (_: AssertionError) { }
        assertTrue(suspendResult<Unit> { throw IOException("offline") }.exceptionOrNull() is IOException)
    }

    @Test fun cleanupHasItsOwnDeadlineEvenWhenCallerIsCancelled() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        var closed = false
        val job = launch {
            try { entered.complete(Unit); awaitCancellation() }
            finally { boundedCleanup(30) { try { awaitCancellation() } finally { closed = true } } }
        }
        entered.await(); withTimeout(1_000) { job.cancelAndJoin() }
        assertTrue(closed)
    }

    @Test fun pairingRecoveryUsesReasonsRatherThanExceptionText() {
        assertTrue(PairingFailureReason.Failed.refreshWindow)
        assertTrue(PairingFailureReason.TimedOut.refreshWindow)
        assertTrue(PairingFailureReason.Cancelled.refreshWindow)
        assertFalse(PairingFailureReason.OwnerVerificationPending.refreshWindow)
        assertFalse(PairingFailureReason.AlreadyOwned.refreshWindow)
        assertEquals(PairingFailureReason.OwnerVerificationPending,
            PairingFailure(PairingFailureReason.OwnerVerificationPending).reason)
        assertFalse(IllegalStateException("pairing_failed") is PairingFailure)
    }
}
