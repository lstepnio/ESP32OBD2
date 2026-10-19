package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FaultCodeCatalogTest {
    @Test fun knownCodesHaveConsistentOfflineExplanations() {
        assertEquals("ABS communication lost", faultDescription("u0121"))
        assertEquals("Body communication lost", FaultCodeCatalog.lookup(" U0140 ").title)
        assertEquals("Cylinder 10 misfire", faultDescription("P0310"))
        assertNotNull(FaultCodeCatalog.lookup("P0301").reference)
        assertTrue(FaultCodeCatalog.lookup("P0420").nextStep.contains("does not identify a failed part"))
    }

    @Test fun manufacturerOrUnknownCodesAreNeverGuessed() {
        for (code in listOf("P1DCA", "P1DF3", "P0999", "C1234")) {
            assertNull(FaultCodeCatalog.lookup(code).reference)
            assertTrue(FaultCodeCatalog.lookup(code).explanation.contains("not included"))
        }
        assertEquals("Unrecognized fault code", faultDescription("P03001"))
    }

    @Test fun recordedContextDeduplicatesCategoriesButNotControllers() {
        val context = JSONObject("""{"diagnostics":{"ECM":{"categories":[{"name":"Stored","codes":["U0121"]},{"name":"Pending","codes":["U0121"]}]},"TCM":{"categories":[{"name":"Stored","codes":["U0121","P1DCA"]}]}}}""")
        val codes = recordedFaultCodes(context)
        assertEquals(3, codes.size)
        assertEquals(listOf("Stored", "Pending"), codes.first().categoryNames)
        assertTrue(codes.none { it.current })
        assertEquals(listOf("Engine", "Transmission", "Transmission"), codes.map { it.source })
        assertTrue(recordedFaultCodes(JSONObject()).isEmpty())
    }
    private val event = AlertEvent(1, 1, 1, 0, 35, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 2f, 0f, "TCM faults", "codes", 0, 1)

    @Test fun notificationUsesDecodedCodesFromTheCorrectRecordedController() {
        val codes = listOf(CarFaultUi("U0121", "", "Transmission", "Stored"), CarFaultUi("P0301", "", "Engine", "Stored"))
        assertEquals("Transmission fault codes", alertTitle(event))
        assertEquals("U0121 · ABS communication lost", alertNotificationText(event, MeasurementSystem.Metric, codes))
        assertFalse(alertNotificationText(event, MeasurementSystem.Metric).contains("TCM"))
        assertTrue(alertNotificationText(event.copy(key = 0, unit = "°C", value = 100f, limit = 90f), MeasurementSystem.Imperial).contains("212 °F"))
    }
}
