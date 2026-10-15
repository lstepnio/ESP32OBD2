package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class DisplayShowcaseProtocolTest {
    private fun packet(active: Boolean = true) = ByteArray(20).apply {
        this[0]=21;this[1]=if(active) 1 else 0;this[3]=3;this[4]=17;this[8]=21;this[9]=8;this[10]=1
        if(active){this[12]=0x60;this[13]=0xea.toByte()}
    }
    @Test fun validFrameKeepsIdentityAndPlaybackSeparateFromSavedSettings() {
        assertTrue(GaugeProtocolCodec.isProtectedStatusFrame(packet()))
        val state=GaugeProtocolCodec.displayShowcase(packet())
        assertTrue(state.active);assertEquals(17L,state.sequence);assertEquals(21,state.count)
        assertEquals(3,state.frame);assertEquals(8,state.intervalSeconds);assertEquals(60_000L,state.remainingMs)
        assertFalse(GaugeProtocolCodec.displayShowcase(packet(false)).active)
    }
    @Test fun invalidOrTruncatedStatusCannotConfirmDemo() {
        val wrong= listOf(packet().copyOf(19),packet().apply {this[10]=2},packet().apply {this[8]=2},
            packet().apply {this[1]=2},packet().apply {this[9]=4},packet().apply {this[16]=1},
            packet(false).apply {this[12]=1})
        wrong.forEach {
            assertTrue(runCatching { GaugeProtocolCodec.displayShowcase(it) }.isFailure)
            assertFalse(GaugeProtocolCodec.isProtectedStatusFrame(it))
        }
    }
}
