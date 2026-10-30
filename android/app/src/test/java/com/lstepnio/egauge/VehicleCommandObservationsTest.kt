package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class VehicleCommandObservationsTest {
    private val scope = VehicleCommandScope("gauge-a", "jeep", 7, "hash-a")
    private fun status(kind: VehicleCommandKind, boot: Long = 17) = VehicleCommandStatus(kind, VehicleCommandPhase.Ready, revision = 7, boot = boot, link = 9, qualified = true)
    private fun snapshot(at: Long = 0): VehicleCommandObservations {
        val initial = VehicleCommandObservations().inScope(scope)
        return initial.snapshot(scope, initial.epoch, VehicleCommandKind.entries.associateWith { status(it) }, at)
    }

    @Test fun completingOneActionDoesNotRefreshItsStaleSiblings() {
        var now = 0L
        var observed = snapshot(now)
        now = 30_001
        assertTrue(observed.freshKinds(scope, now).isEmpty())
        observed = observed.observed(scope, observed.epoch, VehicleCommandKind.Idle, status(VehicleCommandKind.Idle), now)
        assertEquals(setOf(VehicleCommandKind.Idle), observed.freshKinds(scope, now))
        assertEquals(0L, observed.entries.getValue(VehicleCommandKind.AbsTcs).observedAt)
        now += 30_001
        assertTrue(observed.freshKinds(scope, now).isEmpty())
    }

    @Test fun pendingActionLosesFreshnessWithoutHidingItsUncertainStateOrRefreshingOthers() {
        val initial = snapshot(5)
        val pending = initial.pending(scope, initial.epoch, VehicleCommandKind.Idle)
        assertEquals(VehicleCommandPhase.Unknown, pending.values.getValue(VehicleCommandKind.Idle).phase)
        assertEquals(setOf(VehicleCommandKind.AbsTcs, VehicleCommandKind.Burnout), pending.freshKinds(scope, 6))
        assertTrue(pending.freshKinds(scope, 30_006).isEmpty())
    }

    @Test fun changedGaugeVehicleRevisionOrHashImmediatelyRetiresPreviousState() {
        val initial = snapshot()
        val scopes = listOf(scope.copy(gauge = "gauge-b"), scope.copy(vehicle = "car-b"), scope.copy(revision = 8), scope.copy(sha256 = "hash-b"))
        for (next in scopes) {
            assertTrue(initial.freshKinds(next, 10).isEmpty())
            val retired = initial.inScope(next)
            assertTrue(retired.values.isEmpty())
            assertSame(retired, retired.observed(scope, initial.epoch, VehicleCommandKind.Idle, status(VehicleCommandKind.Idle), 10))
        }
    }

    @Test fun returningToSameScopeAfterReconnectCannotPublishAnOldReply() {
        val initial = snapshot()
        val reconnected = initial.retired().inScope(scope)
        assertSame(reconnected, reconnected.snapshot(scope, initial.epoch, initial.values, 100))
        assertSame(reconnected, reconnected.observed(scope, initial.epoch, VehicleCommandKind.Idle, status(VehicleCommandKind.Idle), 100))
        val fresh = reconnected.observed(scope, reconnected.epoch, VehicleCommandKind.Idle, status(VehicleCommandKind.Idle), 101)
        assertEquals(setOf(VehicleCommandKind.Idle), fresh.freshKinds(scope, 101))
    }

    @Test fun bootChangeDiscardsSiblingReceiptsAndMixedBootSnapshotIsRejected() {
        val initial = snapshot()
        val next = initial.observed(scope, initial.epoch, VehicleCommandKind.Idle, status(VehicleCommandKind.Idle, boot = 18), 10)
        assertEquals(setOf(VehicleCommandKind.Idle), next.values.keys)
        val mixed = initial.values + (VehicleCommandKind.Idle to status(VehicleCommandKind.Idle, boot = 18))
        assertSame(initial, initial.snapshot(scope, initial.epoch, mixed, 10))
    }

    @Test fun invalidKindRevisionBootAndClockNeverGrantFreshness() {
        val initial = snapshot(10)
        assertTrue(initial.freshKinds(scope, 9).isEmpty())
        assertFalse(VehicleCommandKind.Idle in initial.freshKinds(scope, 30_011))
        val invalid = listOf(status(VehicleCommandKind.AbsTcs), status(VehicleCommandKind.Idle).copy(revision = 8), status(VehicleCommandKind.Idle, boot = 0))
        for (value in invalid) assertSame(initial, initial.observed(scope, initial.epoch, VehicleCommandKind.Idle, value, 20))
        assertSame(initial, initial.observed(scope, initial.epoch, VehicleCommandKind.Idle, status(VehicleCommandKind.Idle), -1))
    }

    @Test fun completeSnapshotReplacesUnsupportedLegacyKindsWithoutRevivingThem() {
        val initial = snapshot()
        val fresh = initial.snapshot(scope, initial.epoch, mapOf(VehicleCommandKind.Idle to status(VehicleCommandKind.Idle), VehicleCommandKind.AbsTcs to status(VehicleCommandKind.AbsTcs)), 10)
        assertFalse(fresh.values.containsKey(VehicleCommandKind.Burnout))
        assertEquals(setOf(VehicleCommandKind.Idle, VehicleCommandKind.AbsTcs), fresh.freshKinds(scope, 10))
    }
}
