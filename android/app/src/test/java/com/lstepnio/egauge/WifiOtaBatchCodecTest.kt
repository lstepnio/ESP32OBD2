package com.lstepnio.egauge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiOtaBatchCodecTest {
    @Test fun encodesEightExistingChunkCommandsWithoutChangingThem() {
        val commands = (0 until 8).map { index ->
            ByteArray(1037) { position -> (position + index).toByte() }.also { it[0] = 0x23 }
        }

        val encoded = WifiOtaBatchCodec.encode(commands)

        assertEquals(1 + 8 * (2 + 1037), encoded.size)
        assertEquals(8, encoded[0].toInt())
        var offset = 1
        commands.forEach { command ->
            val length = (encoded[offset].toInt() and 255) or
                ((encoded[offset + 1].toInt() and 255) shl 8)
            assertEquals(command.size, length)
            assertArrayEquals(command, encoded.copyOfRange(offset + 2, offset + 2 + length))
            offset += 2 + length
        }
    }

    @Test fun rejectsUnsafeBatchShapes() {
        assertThrows { WifiOtaBatchCodec.encode(emptyList()) }
        assertThrows { WifiOtaBatchCodec.encode(List(9) { validCommand() }) }
        assertThrows { WifiOtaBatchCodec.encode(listOf(ByteArray(13) { 0x22 })) }
        assertThrows { WifiOtaBatchCodec.encode(listOf(ByteArray(1041) { 0x23 })) }
        assertThrows { WifiOtaBatchCodec.decode(ByteArray(57), 1) }
    }

    @Test fun returnsFinalWorkerStatusOnlyForExpectedBatchCount() {
        val status = ByteArray(56) { it.toByte() }
        val response = byteArrayOf(3) + status
        assertArrayEquals(status, WifiOtaBatchCodec.decode(response, 3))
    }

    private fun validCommand() = ByteArray(13).also { it[0] = 0x23 }

    private fun assertThrows(block: () -> Unit) {
        var threw = false
        try { block() } catch (_: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }
}
