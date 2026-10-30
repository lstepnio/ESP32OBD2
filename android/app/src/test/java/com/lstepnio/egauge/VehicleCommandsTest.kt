package com.lstepnio.egauge

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VehicleCommandsTest {
    private fun sessions(session: VehicleCommandSession) = object : VehicleCommandSessions {
        override suspend fun <T> use(action: suspend VehicleCommandSession.() -> T): T = session.action()
    }

    private fun ready(kind: VehicleCommandKind = VehicleCommandKind.Idle) = VehicleCommandStatus(
        kind,
        VehicleCommandPhase.Ready,
        revision = 7,
        link = 9,
        boot = 17,
        qualified = true
    )

    @Test fun capturedTargetWireMatchesTheNativeContractAndRejectsInventedRpm() {
        val request = VehicleCommandProtocol.request(ready(), false, IdlePreset(IdlePresetId.AirUp, seconds = 10))
        assertEquals(File("../../contracts/examples/vehicle-command-idle-start.hex").readText().trim(), request.joinToString("") { "%02x".format(it.toInt() and 255) })
        IdlePresetId.entries.forEach { id ->
            listOf(400, 1200, 3000).forEach { rpm ->
                assertTrue(runCatching { VehicleCommandProtocol.request(ready(), false, IdlePreset(id, rpm)) }.isFailure)
            }
        }
        assertTrue(runCatching { VehicleCommandProtocol.request(ready().copy(qualified = false), false, IdlePreset(IdlePresetId.AirUp)) }.isFailure)
        assertTrue(runCatching { VehicleCommandProtocol.request(ready().copy(lastOperation = 0xffffffffL), false, IdlePreset(IdlePresetId.AirUp)) }.isFailure)
        assertTrue(runCatching { VehicleCommandProtocol.request(ready(VehicleCommandKind.AbsTcs), false, IdlePreset(IdlePresetId.AirUp)) }.isFailure)
    }
    private fun statusBytes(): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
        put(22)
        put(2)
        put(7)
        put(0)
        putInt(3)
        putInt(7)
        putInt(9)
        putInt(17)
        putInt(8)
        putShort(0)
        put(0)
        put(3)
        putInt(3)
    }.array()

    @Test fun absReturnNeverOffersAFalseRestoredStateOrAllowsRepeatedShutdown() {
        val status = VehicleCommandProtocol.status(statusBytes())
        assertEquals(VehicleCommandPhase.RestartRequired, status.phase)
        assertTrue(status.restartRequired)
        assertFalse(status.canStart)
        assertFalse(status.canStop)
        assertTrue(status.message.contains("After Restore, reset ignition"))
        val active = status.copy(phase = VehicleCommandPhase.Active)
        assertTrue(active.canStop)
        val stop = VehicleCommandProtocol.request(active, true)
        assertEquals(3, ByteBuffer.wrap(stop, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        for ((at, value) in listOf(0 to 1, 1 to 4, 2 to 10, 3 to 9, 27 to 4)) {
            val corrupt = statusBytes()
            corrupt[at] = value.toByte()
            assertTrue(runCatching { VehicleCommandProtocol.status(corrupt) }.isFailure)
        }
    }

    @Test fun burnoutIsGaugeStartOnlyAndUsesVersionTwoForStop() {
        val ready = ready(VehicleCommandKind.Burnout)
        assertFalse(ready.canStart)
        assertTrue(runCatching { VehicleCommandProtocol.request(ready, false) }.isFailure)
        val active = ready.copy(phase = VehicleCommandPhase.Active, operation = 3, remainingSeconds = 10)
        val request = VehicleCommandProtocol.request(active, true)
        assertEquals(24, request.size)
        assertEquals(2, request[0].toInt())
        assertEquals(3, request[1].toInt())
        assertEquals(0, request[3].toInt())
        assertTrue(active.copy(phase = VehicleCommandPhase.Unknown).message.contains("not verified"))
        assertFalse(active.copy(phase = VehicleCommandPhase.Restored).message.contains("restored"))
        val unknown = active.copy(phase = VehicleCommandPhase.Unknown)
        assertTrue(unknown.canStop)
        assertEquals(2, VehicleCommandProtocol.request(unknown, true)[2].toInt())
        assertFalse(unknown.copy(operation = 0).canStop)
        assertFalse(unknown.copy(qualified = false).canStop)
        assertFalse(active.copy(qualified = false).canStop)
        assertEquals(listOf(VehicleCommandKind.Idle, VehicleCommandKind.AbsTcs), VehicleCommandKind.supported(1))
        assertEquals(VehicleCommandKind.entries.toList(), VehicleCommandKind.supported(1, 1))
        assertTrue(VehicleCommandKind.supported(2).isEmpty())
        assertTrue(VehicleCommandKind.supported(null).isEmpty())
    }

    @Test fun burnoutStatusBoundsAndFlagsAreStrict() {
        val bytes = statusBytes().apply {
            this[1] = 3
            this[2] = 3
            this[24] = 10
            this[27] = 9
        }
        val status = VehicleCommandProtocol.status(bytes)
        assertTrue(status.gaugeStartRequired)
        assertTrue(status.canStop)
        assertEquals(10, status.remainingSeconds)
        for ((at, value) in listOf(24 to 11, 26 to 1, 27 to 11, 27 to 13, 27 to 25, 2 to 7)) {
            assertTrue(runCatching { VehicleCommandProtocol.status(bytes.copyOf().apply { this[at] = value.toByte() }) }.isFailure)
        }
    }

    @Test fun ownerSupervisedBrakesKeepWireStrictAndPhoneStartDisabled() = runBlocking {
        for (kind in listOf(VehicleCommandKind.AbsTcs, VehicleCommandKind.Burnout)) {
            val local = ready(kind).withBrakeActionsTestVersion(1)
            assertTrue(local.ownerSupervised && local.gaugeStartRequired)
            assertFalse(local.canStart)
            assertTrue(runCatching { VehicleCommandProtocol.request(local, false) }.isFailure)
            var writes = 0
            val session = object : VehicleCommandSession {
                override suspend fun read(kind: VehicleCommandKind) = local
                override suspend fun writeOnce(request: ByteArray) {
                    writes++
                }
            }
            assertTrue(runCatching { VehicleCommandClient(sessions(session)) {}.execute(kind, false, expected = local) }.isFailure)
            assertEquals(0, writes)
            val active = local.copy(phase = VehicleCommandPhase.Active, operation = 3)
            assertTrue(active.canStop)
            assertEquals(2, VehicleCommandProtocol.request(active, true)[2].toInt())
            assertTrue(local.copy(phase = VehicleCommandPhase.Verifying).message.contains("confirm on the gauge"))
            assertTrue(local.copy(phase = VehicleCommandPhase.Restored).message.contains("confirmed on the gauge"))
            assertFalse(local.message.contains("unqualified"))
            assertFalse(local.message.contains("unavailable"))
        }
        assertTrue(ready(VehicleCommandKind.AbsTcs).withBrakeActionsTestVersion(0).canStart)
        assertEquals(ready(), ready().withBrakeActionsTestVersion(1))
        val wire = statusBytes().apply { this[27] = 11 }
        assertTrue(runCatching { VehicleCommandProtocol.status(wire) }.isFailure)
        val abs = VehicleCommandProtocol.status(statusBytes()).withBrakeActionsTestVersion(1)
        assertTrue(abs.gaugeStartRequired)
        assertEquals(VehicleCommandPhase.RestartRequired, abs.phase)
        assertTrue(abs.message.contains("Reset ignition while parked"))
        val uncertain = abs.copy(phase = VehicleCommandPhase.Unknown)
        assertEquals("Use Restore on the gauge, then reset ignition while parked and check the dash", uncertain.message)
        assertFalse(uncertain.message.contains("confirm"))
    }

    @Test fun coolantTimerRetainsStopAndRequiresGaugeRestart() {
        val bytes = statusBytes().apply {
            this[1] = 1
            this[2] = 3
            this[3] = 9
            this[24] = 47
            this[26] = 1
            this[27] = 29
        }
        val timed = VehicleCommandProtocol.status(bytes)
        assertTrue(timed.timedFallback && timed.gaugeStartRequired && timed.coolantUnavailable)
        assertTrue(timed.canStop)
        assertFalse(timed.canStart)
        assertTrue(timed.message.contains("0:47"))
        assertEquals(2, VehicleCommandProtocol.request(timed, true)[2].toInt())
        val restored = timed.copy(phase = VehicleCommandPhase.Restored)
        assertFalse(restored.canStart)
        assertTrue(restored.message.contains("one minute"))
        assertTrue(runCatching { VehicleCommandProtocol.request(restored, false, IdlePreset(IdlePresetId.AirUp)) }.isFailure)
        assertEquals("Start again on the gauge.", restored.copy(coolantUnavailable = false).message)
        assertFalse(timed.copy(coolantUnavailable = false).message.contains("unavailable"))
        assertTrue(ready().canStart)
        bytes[27] = 32
        assertTrue(runCatching { VehicleCommandProtocol.status(bytes) }.isFailure)
    }

    @Test fun uncertainWriteUsesReadbackWithoutResubmission() = runBlocking {
        var writes = 0
        var state = ready()
        val session = object : VehicleCommandSession {
            override suspend fun read(kind: VehicleCommandKind) = state
            override suspend fun writeOnce(request: ByteArray) {
                writes++
                state = state.copy(phase = VehicleCommandPhase.Active, operation = 1, preset = IdlePresetId.AirUp, remainingSeconds = 10)
                error("Lost write acknowledgement")
            }
        }
        val result = VehicleCommandClient(sessions(session)) {}.execute(VehicleCommandKind.Idle, false, IdlePreset(IdlePresetId.AirUp, seconds = 10))
        assertEquals(VehicleCommandPhase.Active, result.phase)
        assertEquals(1, writes)
    }

    @Test fun scopeChangeMissingReplyAndCancellationNeverBecomeSuccessOrRetry() = runBlocking {
        for (mode in 0..2) {
            var writes = 0
            var reads = 0
            val session = object : VehicleCommandSession {
                override suspend fun read(kind: VehicleCommandKind): VehicleCommandStatus {
                    reads++
                    return if (reads == 1) {
                        ready()
                    } else {
                        when (mode) {
                            0 -> ready().copy(boot = 18)
                            1 -> ready()
                            else -> throw CancellationException("Scope retired")
                        }
                    }
                }
                override suspend fun writeOnce(request: ByteArray) {
                    writes++
                }
            }
            val result = runCatching { VehicleCommandClient(sessions(session)) {}.execute(VehicleCommandKind.Idle, false, IdlePreset(IdlePresetId.Winch)) }
            if (mode == 2) {
                assertTrue(result.exceptionOrNull() is CancellationException)
            } else {
                assertEquals(VehicleCommandPhase.Unknown, result.getOrThrow().phase)
            }
            assertEquals(1, writes)
            assertTrue(reads <= 71)
        }
    }

    @Test fun confirmationScopeIsRecheckedBeforeAnyWrite() = runBlocking {
        for (changed in listOf(ready().copy(boot = 18), ready().copy(revision = 8), ready().copy(link = 10))) {
            var writes = 0
            val session = object : VehicleCommandSession {
                override suspend fun read(kind: VehicleCommandKind) = changed
                override suspend fun writeOnce(request: ByteArray) {
                    writes++
                }
            }
            assertTrue(runCatching { VehicleCommandClient(sessions(session)) {}.execute(VehicleCommandKind.Idle, false, IdlePreset(IdlePresetId.AirUp), ready()) }.isFailure)
            assertEquals(0, writes)
        }
        var writes = 0
        val active = ready().copy(phase = VehicleCommandPhase.Active, operation = 2)
        val session = object : VehicleCommandSession {
            override suspend fun read(kind: VehicleCommandKind) = active
            override suspend fun writeOnce(request: ByteArray) {
                writes++
            }
        }
        assertTrue(runCatching { VehicleCommandClient(sessions(session)) {}.execute(VehicleCommandKind.Idle, true, expected = active.copy(operation = 1)) }.isFailure)
        assertEquals(0, writes)
    }
}
