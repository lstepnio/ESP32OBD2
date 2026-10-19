package com.lstepnio.egauge.experimental.companion

// BEGIN EXPERIMENT: companion-content
// Removal: delete this package and its tests; see docs/development/companion-session-experiment.md.

import com.lstepnio.egauge.*
import kotlinx.coroutines.delay

/** Test-only scope. This is sender fencing for legacy 3E, not the proposed scoped v1 wire. */
internal data class SyntheticProbeScope(
    val vehicle: String,
    val gauge: String,
    val boot: Long,
    val revision: Long,
    val digest: String,
) {
    init {
        require(vehicle.isNotBlank() && gauge.isNotBlank() && boot in 1..0xffffffffL && revision > 0)
        require(digest.matches(Regex("[0-9a-f]{64}")))
    }
}

/** Inactive, synthetic-only existing-wire probe (packaged in debug and release). No source discovery, service, UI or live provider.
 * Invoke inside the existing operation lease and a bounded AndroidGaugeOwnerSessions.use block.
 * reserveSequence must durably reserve the legacy slot-zero sequence before returning.
 */
internal class SyntheticRoadProbe(
    private val session: GaugeOwnerSession,
    private val expected: SyntheticProbeScope,
    private val currentScope: () -> SyntheticProbeScope?,
    private val reserveSequence: suspend () -> Long,
    private val now: () -> Long,
    private val pause: suspend () -> Unit = { delay(50) },
) {
    private var ownedContext: String? = null
    private var lastReservedSequence = 0L
    private val title = "Phone alert test"

    suspend fun show(): AlertBatch = session.transaction {
        val receivedAt = now()
        verifyScope()
        val before = active()
        val occupied = before.events.singleOrNull { it.key == 36 }
        check(occupied == null || (ownedContext != null && matches(occupied, ownedContext!!))) {
            "Synthetic probe slot is occupied; leave it untouched"
        }
        val sequence = nextSequence()
        val context = "T$sequence"
        val notice = PhoneAlertBridge.fixtures.validate(
            PhoneAlert("synthetic", "session-probe", expected.vehicle, title, context,
                AlertSeverity.Advisory, 3_000, receivedAt, simulated = true), expected.vehicle, now())
        checkCurrent()
        writeRaw(AlertCodec.external(0, expected.boot, sequence, notice.severity, notice.ttlMs,
            notice.title, notice.context, simulated = true))
        val applied = awaitApplied { batch -> batch.events.any { matches(it, context) } }
        ownedContext = context
        applied
    }

    suspend fun withdraw(): AlertBatch = session.transaction {
        verifyScope()
        val before = active()
        val occupied = before.events.singleOrNull { it.key == 36 }
        if (occupied == null) { ownedContext = null; return@transaction before }
        check(ownedContext != null && matches(occupied, ownedContext!!)) {
            "Synthetic probe no longer owns the slot; leave it untouched"
        }
        val sequence = nextSequence()
        checkCurrent()
        writeRaw(AlertCodec.external(0, expected.boot, sequence, AlertSeverity.Advisory,
            0, title, "T$sequence", simulated = true))
        awaitApplied { batch -> batch.events.none { it.key == 36 } }.also { ownedContext = null }
    }

    private fun checkCurrent() = check(currentScope() == expected) { "Synthetic probe target changed" }

    private suspend fun nextSequence(): Long = reserveSequence().also {
        require(it in 1..0x7fffffffL && it > lastReservedSequence) { "Synthetic sequence reservation is invalid" }
        lastReservedSequence = it
        checkCurrent()
    }

    private suspend fun GaugeOwnerTransaction.verifyScope() {
        checkCurrent()
        // Legacy 3E cannot promise fragmented delivery. Do not guess about long-write support.
        require(mtu >= 67) { "Synthetic probe needs an ATT payload of at least 64 bytes" }
        writeRaw(byteArrayOf(0x33) + AlertCodec.le32(1))
        val runtime = GaugeProtocolCodec.runtimeIdentity(readRaw())
        check(runtime.running && !runtime.trial && !runtime.usedPreviousGeneration &&
            runtime.revision == expected.revision && runtime.sha256 == expected.digest) { "Synthetic probe configuration changed" }
        checkCurrent()
    }

    private suspend fun GaugeOwnerTransaction.active(): AlertBatch {
        var cursor = 0L
        val events = mutableListOf<AlertEvent>()
        repeat(12) {
            checkCurrent()
            writeRaw(byteArrayOf(0x3c, 1) + AlertCodec.le32(expected.boot) + AlertCodec.le32(cursor))
            val batch = AlertCodec.decode(readRaw())
            check(batch.active && batch.boot == expected.boot && !batch.gap) { "Synthetic probe boot/snapshot changed" }
            checkCurrent()
            events += batch.events
            check(events.size <= 44 && events.map { it.key }.distinct().size == events.size)
            if (batch.complete) return batch.copy(events = events)
            check(batch.next > cursor) { "Synthetic snapshot made no progress" }
            cursor = batch.next
        }
        error("Synthetic snapshot exceeded its page budget")
    }

    private suspend fun GaugeOwnerTransaction.awaitApplied(accepted: (AlertBatch) -> Boolean): AlertBatch {
        repeat(10) { attempt ->
            val snapshot = active()
            if (accepted(snapshot)) return snapshot
            if (attempt < 9) pause()
        }
        error("Synthetic content outcome was not confirmed; do not replay the write")
    }

    private fun matches(event: AlertEvent, context: String) = event.key == 36 && event.boot == expected.boot &&
        event.revision == expected.revision && event.simulated && !event.unavailable &&
        event.lifecycle == AlertLifecycle.Active && event.severity == AlertSeverity.Advisory &&
        event.label == title && event.unit == context
}

// END EXPERIMENT: companion-content
