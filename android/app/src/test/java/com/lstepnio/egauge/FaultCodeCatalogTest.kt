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
        for (code in listOf("P1999", "P0999", "C1234")) {
            assertNull(FaultCodeCatalog.lookup(code).reference)
            assertTrue(FaultCodeCatalog.lookup(code).explanation.contains("not included"))
        }
        assertEquals("Unrecognized fault code", faultDescription("P03001"))
    }

    @Test fun manufacturerReferencesAreNamedAndDoNotInventARepair() {
        assertEquals("Chrysler/Jeep", FaultCodeCatalog.lookup("P1DCA").manufacturer)
        assertEquals("Park sense circuit open", FaultCodeCatalog.lookup("P1DCA").title)
        assertEquals("Controller not initialized", FaultCodeCatalog.lookup("P1DF3").title)
        assertTrue(FaultCodeCatalog.lookup("P1DF3").nextStep.contains("does not identify"))
    }

    @Test fun transmissionReferenceSubsetHasValidUniqueCodeKeysAndSources() {
        assertEquals(35, FcaTransmissionFaultCodes.entries.size)
        FcaTransmissionFaultCodes.entries.forEach { (code, meaning) ->
            assertTrue(Regex("[PCBU][0-3][0-9A-F]{3}").matches(code))
            assertEquals("Chrysler/Jeep", meaning.manufacturer)
            assertTrue(meaning.reference!!.startsWith("FCA") || meaning.reference.startsWith("Stellantis"))
            assertTrue(meaning.explanation.isNotBlank())
            assertEquals(meaning, FaultCodeCatalog.lookup(code.lowercase()))
        }
        assertEquals("Gear 1 ratio mismatch", FaultCodeCatalog.lookup("P0731").title)
        assertEquals("Gear 3 ratio mismatch", FaultCodeCatalog.lookup("P0733").title)
        assertEquals("Converter clutch response", FaultCodeCatalog.lookup("P1DB7").title)
        assertNull(FaultCodeCatalog.lookup("420641").reference)
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
