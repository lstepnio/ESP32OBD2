package com.lstepnio.egauge

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VariantFeatureContractTest {
    private val template = File("src/main/assets/numeric_config_template.json").readText()
    private fun page(pid: String) = GaugePageDraft("reading", "Reading", GaugeLayout.Numeric, listOf(pid))

    @Test fun releaseSelectableCatalogContainsEveryStandardReadingAndNoDevelopmentReading() {
        assertFalse(BuildConfig.DEBUG)
        val expected = readingCatalog.filter { it.source == "ECM" }.map { it.id }.toSet()
        assertTrue(expected.isNotEmpty())
        listOf("ECM", "BOTH", "TCM").forEach { source ->
            assertEquals(expected, ConfigurationProjector.pagePidIds(source))
        }
    }

    @Test fun developmentReadingsAreRejectedForPagesAndAlerts() {
        ConfigurationProjector.transmissionPidIds.forEach { pid ->
            val draft = Draft(pidId = pid, pages = listOf(page(pid)), alerts = emptyList())
            assertTrue(ConfigurationProjector.blockers(draft).isNotEmpty())
            assertTrue(ConfigurationProjector.blockers(Draft(alerts = listOf(defaultAlert(pid)))).isNotEmpty())
            assertThrows(IllegalArgumentException::class.java) {
                ConfigurationProjector.project(template, draft, "vehicle", 1, schemaVersion = 2)
            }
        }
    }

    @Test fun transmissionSourceAndMixedControllerLayoutsStayGated() {
        assertTrue(ConfigurationProjector.blockers(Draft(source = "TCM")).isNotEmpty())
        val mixed = GaugePageDraft("mixed", "Mixed", GaugeLayout.Dual, listOf("rpm", "tcmtemp"))
        val draft = Draft(pages = listOf(mixed), alerts = emptyList())
        assertTrue(ConfigurationProjector.blockers(draft).isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationProjector.project(template, draft, "vehicle", 1, schemaVersion = 2)
        }
    }

    @Test fun ordinaryEngineConfigurationStillProjectsAndRetainsSourceIdentity() {
        val draft = Draft(pages = listOf(page("rpm")), alerts = emptyList())
        assertTrue(ConfigurationProjector.blockers(draft).isEmpty())
        val document = JSONObject(ConfigurationProjector.project(template, draft, "vehicle", 1, schemaVersion = 2).second.toString(Charsets.UTF_8))
        assertEquals("vehicle", document.getString("vehicleProfileId"))
        assertEquals(1, document.getJSONArray("sources").length())
        assertEquals("ecm", document.getJSONArray("sources").getJSONObject(0).getString("role"))
        assertEquals(1, document.getJSONArray("definitions").length())
    }

    @Test fun childOrPrimaryTransportSelectionCannotBypassDevelopmentGates() {
        val primary = AdapterBinding("engine", "AA:BB:CC:DD:EE:01", "public")
        val child = AdapterBinding("transmission", "AA:BB:CC:DD:EE:02", "random")
        val vehicle = VehicleProfile("vehicle", "Jeep", Draft(), primary, TransmissionConnection(child))
        listOf(false, true).forEach { useChild ->
            assertThrows(IllegalArgumentException::class.java) {
                ConfigurationProjector.projectVehicle(template, vehicle, 1, useChild)
            }
        }
    }

    @Test fun gatedSavedDataIsRetainedWithoutMakingItExecutable() {
        val draft = Draft(pidId = "tcmtemp", source = "TCM", pages = listOf(page("tcmtemp")), alerts = emptyList())
        val saved = ProfileCollection("vehicle", listOf(VehicleProfile("vehicle", "Jeep", draft)))
        assertEquals(saved, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(saved)))
        assertTrue(ConfigurationProjector.blockers(saved.active.draft).isNotEmpty())
    }
}
