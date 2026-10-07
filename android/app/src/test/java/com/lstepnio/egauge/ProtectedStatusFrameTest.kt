package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class ProtectedStatusFrameTest {
    @Test fun hardwareSnapshotLeftByPollingCanEstablishOwnerSession() {
        val hardware = ByteArray(56).apply { this[0] = 10; this[1] = 1 }
        assertTrue(GaugeProtocolCodec.isProtectedStatusFrame(hardware))
        assertTrue(GaugeProtocolCodec.isProtectedStatusFrame(ByteArray(8).apply { this[0] = 10 }))
    }
    @Test fun unknownEmptyAndTruncatedFramesCannotEstablishOwnerSession() {
        assertFalse(GaugeProtocolCodec.isProtectedStatusFrame(byteArrayOf()))
        assertFalse(GaugeProtocolCodec.isProtectedStatusFrame(ByteArray(55).apply { this[0] = 10 }))
        assertFalse(GaugeProtocolCodec.isProtectedStatusFrame(ByteArray(56).apply { this[0] = 16 }))
        assertFalse(GaugeProtocolCodec.isProtectedStatusFrame(ByteArray(56).apply { this[0] = 11 }))
    }
}
