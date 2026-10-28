package com.lstepnio.egauge

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsReadTest {
    private class Sessions(val rejection: Int? = null) {
        val events = mutableListOf<String>()
        val commands = mutableListOf<Int>()
        var opened = 0
        var authenticationFailure: Exception? = null
        var readFailure: Exception? = null
        val snapshot = ByteArray(32).apply { this[0] = 5 }
        suspend fun read(action: suspend GaugeOwnerTransaction.() -> ByteArray): ByteArray {
            val number = ++opened
            events += "open$number"
            var reads = 0
            val channel = object : GaugeOwnerChannel {
                override val mtu = 173
                override suspend fun read(): OwnerStateRead {
                    if (reads++ == 0) {
                        authenticationFailure?.let { throw it }
                        return OwnerStateRead(0, ByteArray(64).apply { this[0] = 3 })
                    }
                    readFailure?.let { throw it }
                    return OwnerStateRead(0, snapshot)
                }
                override suspend fun write(bytes: ByteArray) {
                    val command = bytes[0].toInt() and 255
                    commands += command
                    if (command in setOf(0x39, 0x3a) && rejection != null) throw GaugeCommandRejectedException(rejection)
                }
            }
            return try {
                useGaugeOwnerSession(channel) { it.transaction(action) }
            } finally {
                events += "close$number"
            }
        }
    }

    @Test fun confirmedUnsupportedSnapshotUsesFreshOwnerAfterRetirementAndCleanup() = runBlocking {
        for (status in listOf(6, 13)) {
            val owner = Sessions(status)
            assertArrayEquals(owner.snapshot, readDiagnosticsSnapshot(null, owner::read))
            assertEquals(listOf(0x39, 0x30), owner.commands)
            assertEquals(listOf("open1", "close1", "open2", "close2"), owner.events)
            assertEquals("ECM", GaugeProtocolCodec.diagnostics(owner.snapshot).source)
        }
    }

    @Test fun supportedSnapshotUsesOneOwnerWithoutLegacyRequest() = runBlocking {
        val owner = Sessions()
        readDiagnosticsSnapshot(null, owner::read)
        assertEquals(listOf(0x39), owner.commands)
        assertEquals(1, owner.opened)
    }

    @Test fun scopedUnsupportedAndOtherGattFailuresNeverDowngrade() = runBlocking {
        for ((source, status) in listOf(1 to 6, 0 to 13, null to 5, null to 17)) {
            val owner = Sessions(status)
            val error = runCatching { readDiagnosticsSnapshot(source, owner::read) }.exceptionOrNull()
            assertTrue(error is GaugeCommandRejectedException)
            assertEquals(status, (error as GaugeCommandRejectedException).status)
            assertEquals(1, owner.opened)
            assertEquals(listOf(if (source == null) 0x39 else 0x3a), owner.commands)
        }
    }

    @Test fun ownerAuthenticationRejectionIsNeverMistakenForUnsupportedDiagnostics() = runBlocking {
        val owner = Sessions()
        val error = GaugeCommandRejectedException(6)
        owner.authenticationFailure = error
        assertSame(error, runCatching { readDiagnosticsSnapshot(null, owner::read) }.exceptionOrNull())
        assertEquals(1, owner.opened)
        assertTrue(owner.commands.isEmpty())
    }

    @Test fun readFailureAndMalformedPayloadNeverCauseLegacyDowngrade() = runBlocking {
        val owner = Sessions()
        val error = GaugeLinkException("late reply or link loss")
        owner.readFailure = error
        val observed = runCatching { readDiagnosticsSnapshot(null, owner::read) }.exceptionOrNull()
        assertTrue(observed is GaugeLinkException)
        assertEquals(error.message, observed?.message)
        assertEquals(listOf(0x39), owner.commands)
        val malformed = Sessions().also { it.snapshot[0] = 99 }
        assertTrue(runCatching { GaugeProtocolCodec.diagnostics(readDiagnosticsSnapshot(null, malformed::read)) }.isFailure)
        assertEquals(listOf(0x39), malformed.commands)
        assertEquals(1, malformed.opened)
    }

    @Test fun cancellationAndInvalidSourceDoNotOpenFallback() = runBlocking {
        val owner = Sessions()
        owner.readFailure = CancellationException("cancelled read")
        assertTrue(runCatching { readDiagnosticsSnapshot(null, owner::read) }.exceptionOrNull() is CancellationException)
        assertEquals(1, owner.opened)
        val invalid = Sessions()
        assertTrue(runCatching { readDiagnosticsSnapshot(2, invalid::read) }.isFailure)
        assertEquals(0, invalid.opened)
    }
}
