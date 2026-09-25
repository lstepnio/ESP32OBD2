package com.lstepnio.egauge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PidLabTest {
    @Test fun decodesDocumentedMode01Examples() {
        assertEquals(DecodeResult.Value("2840 rpm"),
            decodeExample(demoCatalog.first { it.id == "rpm" }, "41 0C 2C 60"))
        assertEquals(DecodeResult.Value("92 °C"),
            decodeExample(demoCatalog.first { it.id == "coolant" }, "41 05 84"))
    }

    @Test fun rejectsWrongPidAndVehicleSpecificPlaceholders() {
        assertTrue(decodeExample(demoCatalog.first { it.id == "rpm" }, "41 05 2C 60") is DecodeResult.Error)
        assertTrue(decodeExample(demoCatalog.first { it.id == "tcm" }, "41 0C 2C 60") is DecodeResult.Error)
        assertTrue(decodeExample(demoCatalog.first { it.id == "rpm" }, "41 0C ZZ") is DecodeResult.Error)
    }
}
