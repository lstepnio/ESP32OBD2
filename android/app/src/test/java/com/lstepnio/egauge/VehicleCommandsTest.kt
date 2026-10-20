package com.lstepnio.egauge

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VehicleCommandsTest {
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
        assertTrue(runCatching { VehicleCommandProtocol.request(ready(), false, IdlePreset(IdlePresetId.AirUp, 1200)) }.isFailure)
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
        assertTrue(status.message.contains("Restart"))
        val active = status.copy(phase = VehicleCommandPhase.Active)
        assertTrue(active.canStop)
        val stop = VehicleCommandProtocol.request(active, true)
        assertEquals(3, ByteBuffer.wrap(stop, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        for ((at, value) in listOf(0 to 1, 1 to 3, 2 to 10, 3 to 9, 27 to 4)) {
            val corrupt = statusBytes()
            corrupt[at] = value.toByte()
            assertTrue(runCatching { VehicleCommandProtocol.status(corrupt) }.isFailure)
        }
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
        val result = VehicleCommandClient(session) {}.execute(VehicleCommandKind.Idle, false, IdlePreset(IdlePresetId.AirUp, seconds = 10))
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
            val result = runCatching { VehicleCommandClient(session) {}.execute(VehicleCommandKind.Idle, false, IdlePreset(IdlePresetId.Winch)) }
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
            assertTrue(runCatching { VehicleCommandClient(session) {}.execute(VehicleCommandKind.Idle, false, IdlePreset(IdlePresetId.AirUp), ready()) }.isFailure)
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
        assertTrue(runCatching { VehicleCommandClient(session) {}.execute(VehicleCommandKind.Idle, true, expected = active.copy(operation = 1)) }.isFailure)
        assertEquals(0, writes)
    }
}
