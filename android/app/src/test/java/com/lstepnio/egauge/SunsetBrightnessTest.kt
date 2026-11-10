package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class SunsetBrightnessTest {
    @Test fun wireUsesSignedCoordinatesAndIndependentRevision() {
        val location = SunsetLocation(3970, -10500)
        val request = GaugeProtocolCodec.sunsetBrightnessWrite(true, 8, location, 1782100800, 3)
        assertEquals(15, request.size)
        assertEquals(0x6d, request[0].toInt())
        assertEquals(1, request[1].toInt())
        val state = byteArrayOf(25, 1, 7, 8) + request.copyOfRange(3, 7) + byteArrayOf(4, 0, 0, 0)
        assertEquals(SunsetBrightness(true, true, true, 8, location, 4), GaugeProtocolCodec.sunsetBrightness(state))
        assertThrows(IllegalArgumentException::class.java) { GaugeProtocolCodec.sunsetBrightness(state.copyOf(11)) }
        assertThrows(IllegalArgumentException::class.java) {
            GaugeProtocolCodec.sunsetBrightnessWrite(true, 4, location, 1782100800, 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GaugeProtocolCodec.sunsetBrightnessWrite(true, 8, location, 0, 3)
        }
    }
}
