package com.lstepnio.egauge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VehicleCommandOwnerSessionTest {
    private class Owners(
        stop: Boolean = false,
        private val uncertainWrite: Boolean = true,
        private val cancelledWrite: Boolean = false,
        private val observed: (VehicleCommandStatus) -> VehicleCommandStatus = { it },
        private val readFailure: Exception? = null,
        private val openFailure: Exception? = null
    ) : VehicleCommandSessions {
        val events = mutableListOf<String>()
        val opcodes = mutableListOf<Int>()
        var opened = 0
        var state = VehicleCommandStatus(
            VehicleCommandKind.Idle,
            if (stop) VehicleCommandPhase.Active else VehicleCommandPhase.Ready,
            operation = if (stop) 4 else 0,
            revision = 7,
            link = 9,
            boot = 17,
            qualified = true
        )
        private val stopping = stop

        override suspend fun <T> use(action: suspend VehicleCommandSession.() -> T): T {
            val number = ++opened
            events += "open$number"
            var reads = 0
            val channel = object : GaugeOwnerChannel {
                override val mtu = 173
                override suspend fun read(): OwnerStateRead {
                    if (reads++ == 0) return OwnerStateRead(0, ByteArray(64).apply { this[0] = 3 })
                    if (number == 2) readFailure?.let { throw it }
                    val value = if (number == 2) observed(state) else state
                    return OwnerStateRead(0, encode(value))
                }
                override suspend fun write(bytes: ByteArray) {
                    val opcode = bytes[0].toInt() and 255
                    opcodes += opcode
                    if (opcode == 0x66) {
                        check(number == 1) { "Mutation was replayed on reconciliation owner" }
                        if (cancelledWrite) throw CancellationException("cancelled mutation")
                        val operation = ByteBuffer.wrap(bytes, 5, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
                        state = state.copy(phase = if (stopping) VehicleCommandPhase.Restored else VehicleCommandPhase.Active, operation = operation)
                        if (uncertainWrite) throw GaugeLinkException("Lost mutation acknowledgement")
                    }
                }
            }
            return try {
                if (number == 2) openFailure?.let { throw it }
                useGaugeOwnerSession(channel) { owner ->
                    owner.transaction {
                        val io = this
                        action(object : VehicleCommandSession {
                            override suspend fun read(kind: VehicleCommandKind): VehicleCommandStatus {
                                io.writeRaw(byteArrayOf(0x67, 1, 0, 0, 0, kind.wire.toByte()))
                                return VehicleCommandProtocol.status(io.readRaw())
                            }
                            override suspend fun writeOnce(request: ByteArray) = io.writeRaw(byteArrayOf(0x66) + request)
                        })
                    }
                }
            } finally {
                events += "close$number"
            }
        }

        private fun encode(value: VehicleCommandStatus): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(22.toByte())
            put(value.kind.wire.toByte())
            put(value.phase.ordinal.toByte())
            put(0.toByte())
            putInt(value.operation.toInt())
            putInt(value.revision.toInt())
            putInt(value.link.toInt())
            putInt(value.boot.toInt())
            putInt(0)
            putShort(0.toShort())
            put(0.toByte())
            put(1.toByte())
            putInt(0)
        }.array()
    }

    private suspend fun execute(owners: Owners, stop: Boolean = false) = VehicleCommandClient(owners) {}.execute(
        VehicleCommandKind.Idle,
        stop,
        if (stop) null else IdlePreset(IdlePresetId.AirUp),
        owners.state
    )

    @Test fun uncertainStartAndStopCloseRetiredOwnerBeforeFreshReadbackWithoutReplay() = runBlocking {
        for (stop in listOf(false, true)) {
            val owners = Owners(stop = stop)
            val result = execute(owners, stop)
            assertEquals(if (stop) VehicleCommandPhase.Restored else VehicleCommandPhase.Active, result.phase)
            assertEquals(listOf("open1", "close1", "open2", "close2"), owners.events)
            assertEquals(listOf(0x67, 0x66, 0x67), owners.opcodes)
            assertEquals(if (stop) 4L else 1L, result.operation)
        }
    }

    @Test fun normalSuccessKeepsAdmissionMutationAndReadbackOnOneOwner() = runBlocking {
        val owners = Owners(uncertainWrite = false)
        assertEquals(VehicleCommandPhase.Active, execute(owners).phase)
        assertEquals(listOf("open1", "close1"), owners.events)
        assertEquals(listOf(0x67, 0x66, 0x67), owners.opcodes)
    }

    @Test fun freshOwnerScopeOrOperationMismatchCannotConfirmOrRepeatMutation() = runBlocking {
        val changes: List<(VehicleCommandStatus) -> VehicleCommandStatus> = listOf(
            { it.copy(boot = 18) },
            { it.copy(revision = 8) },
            { it.copy(link = 10) },
            { it.copy(kind = VehicleCommandKind.AbsTcs) },
            { it.copy(operation = 2) }
        )
        for (change in changes) {
            val owners = Owners(observed = change)
            assertEquals(VehicleCommandPhase.Unknown, execute(owners).phase)
            assertEquals(1, owners.opcodes.count { it == 0x66 })
            assertEquals(listOf("open1", "close1", "open2", "close2"), owners.events)
        }
    }

    @Test fun unavailableFreshOwnerOrReplyReturnsUnknownAndNeverReplays() = runBlocking {
        for (owners in listOf(Owners(readFailure = GaugeLinkException("missing reply")), Owners(openFailure = GaugeLinkException("unavailable owner")))) {
            assertEquals(VehicleCommandPhase.Unknown, execute(owners).phase)
            assertEquals(1, owners.opcodes.count { it == 0x66 })
            assertEquals(listOf("open1", "close1", "open2", "close2"), owners.events)
        }
    }

    @Test fun cancellationDuringWriteOrFreshReadPropagatesWithoutSuccessOrReplay() = runBlocking {
        for (owners in listOf(Owners(cancelledWrite = true), Owners(readFailure = CancellationException("cancelled read")), Owners(openFailure = CancellationException("cancelled connect")))) {
            assertTrue(runCatching { execute(owners) }.exceptionOrNull() is CancellationException)
            assertEquals(1, owners.opcodes.count { it == 0x66 })
            assertEquals(if (owners.opened == 1) listOf("open1", "close1") else listOf("open1", "close1", "open2", "close2"), owners.events)
        }
    }
}
