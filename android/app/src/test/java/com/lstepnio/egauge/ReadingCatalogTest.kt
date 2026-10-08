package com.lstepnio.egauge

import com.lstepnio.egauge.ui.customize.AlertForm
import com.lstepnio.egauge.ui.state.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReadingCatalogTest {
    private val template = File("src/main/assets/numeric_config_template.json").readText()
    private val temperature = GaugeAlertDraft("alert.tcmtemp", "tcmtemp", warning = 85.5, critical = 95.5, hysteresis = 2.5)
    private val gear = GaugeAlertDraft("alert.tcmgear", "tcmgear", AlertDirection.Equals, 11.0, 13.0, 0.0)
    private fun document(json: String) = GaugeConfigTransferClient.ActiveDocument(3, "a".repeat(64), json.length,
        "default", 1, 1, 1, json)

    @Test fun everyCatalogReadingCanBeSelectedAndAlertedWithoutPollingTheWholeCatalog() {
        assertEquals(55, readingCatalog.size)
        assertEquals(53, ConfigurationProjector.supportedPidIds.size)
        readingCatalog.forEach { reading ->
            val range = readingBounds(reading.id)
            val span = range.endInclusive - range.start
            val directions = if (reading.alertKind == "gear") listOf(AlertDirection.Equals) else listOf(AlertDirection.Above, AlertDirection.Below)
            directions.forEach { direction ->
                val alert = if (direction == AlertDirection.Equals) gear else GaugeAlertDraft("alert.${reading.id}", reading.id, direction,
                    range.start + span * (if (direction == AlertDirection.Above) .6 else .4),
                    range.start + span * (if (direction == AlertDirection.Above) .8 else .2), span * .05)
                val page = GaugePageDraft("page.catalog", reading.gaugeLabel, GaugeLayout.Numeric, listOf(reading.id))
                val draft = Draft(pidId = reading.id, pages = listOf(page), alerts = listOf(alert))
                assertTrue("${reading.id}: ${ConfigurationProjector.blockers(draft)}", ConfigurationProjector.blockers(draft).isEmpty())
                val wire = JSONObject(ConfigurationProjector.project(template, draft, "default", 2, schemaVersion = 2).second.toString(Charsets.UTF_8))
                assertEquals(1, wire.getJSONArray("definitions").length())
                assertEquals(reading.definitionId, wire.getJSONArray("definitions").getJSONObject(0).getString("id"))
                val doc = document(wire.toString())
                assertEquals(draft.pages, GaugeDraftComparison.savedDraft(doc, "default")!!.pages)
                assertEquals(listOf(alert), GaugeDraftComparison.savedDraft(doc, "default")!!.alerts)
                assertTrue(GaugeDraftComparison.from(doc, "default", draft).fields.single { it.label == "All alert settings" }.matches == true)
                assertTrue(AlertForm.from(alertUi(alert)).errors(range).isEmpty())
            }
        }
    }
    @Test fun helperDefaultsRemainValidForEveryCatalogReadingIncludingSubUnitRanges() {
        readingCatalog.forEach { reading ->
            assertTrue(reading.id, ConfigurationProjector.blockers(Draft(alerts = listOf(defaultAlert(reading.id)))).isEmpty())
        }
    }
    @Test fun fractionalVoltageAlertSurvivesPersistenceAndExactWireReadback() {
        val alert = GaugeAlertDraft("alert.voltage", "voltage", AlertDirection.Below, 12.2, 11.7, .1)
        val draft = Draft(alerts = listOf(alert))
        val profiles = ProfileCollection("default", listOf(VehicleProfile("default", "Vehicle", draft)))
        assertEquals(12, JSONObject(ProfileDocumentCodec.encode(profiles)).getInt("schemaVersion"))
        assertEquals(profiles, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(profiles)))
        val bytes = ConfigurationProjector.project(template, draft, "default", 2, schemaVersion = 2).second
        assertEquals(listOf(alert), GaugeDraftComparison.savedDraft(document(bytes.toString(Charsets.UTF_8)), "default")!!.alerts)
        assertEquals(alert, AlertForm.from(alertUi(alert)).saved("voltage", readingBounds("voltage"), alertUi(alert)))
    }
    @Test fun childAlertsMergePersistRouteAndSurviveRemovingTheChildTransport() {
        val parent = VehicleProfile("default", "Vehicle", Draft(), AdapterBinding("primary", "AA:BB:CC:DD:EE:01", "public"),
            TransmissionConnection(AdapterBinding("child", "AA:BB:CC:DD:EE:02", "public")))
        val edited = parent.withDashboard(parent.dashboardDraft().copy(alerts = listOf(defaultAlert(), temperature, gear)))
        assertEquals(listOf(temperature, gear), edited.transmission!!.draft.alerts)
        assertEquals(listOf(defaultAlert(), temperature, gear), edited.dashboardDraft().alerts)
        assertEquals(edited, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(ProfileCollection(edited.id, listOf(edited)))).active)
        val routed = JSONObject(ConfigurationProjector.projectCombined(template, edited, 2).second.toString(Charsets.UTF_8))
        val defs = routed.getJSONArray("definitions")
        val temp = (0 until defs.length()).map { defs.getJSONObject(it) }.single { it.getString("id") == "transmission.temperature.experimental" }
        assertEquals("tcm", temp.getString("sourceId"))
        val single = edited.withoutTransmissionAdapter()
        assertEquals(edited.dashboardDraft().alerts, single.dashboardDraft().alerts)
        val one = JSONObject(ConfigurationProjector.projectVehicle(template, single, 2, false).second.toString(Charsets.UTF_8))
        assertEquals(1, one.getJSONArray("sources").length())
        assertEquals(3, one.getJSONArray("alerts").length())
    }
    @Test fun transmissionAlertDoesNotRequireATransmissionPage() {
        val draft = Draft(alerts = listOf(temperature, gear))
        assertTrue(ConfigurationProjector.blockers(draft).isEmpty())
        assertTrue(requiresVehicleDashboardFirmware(draft, false))
        val wire = JSONObject(ConfigurationProjector.project(template, draft, "default", 2, schemaVersion = 2).second.toString(Charsets.UTF_8))
        assertEquals(5, wire.getJSONArray("definitions").length())
    }
    @Test fun unavailableDefinitionsAndTooManyActiveReadingsAreRejected() {
        val unknown = Draft(alerts = listOf(temperature.copy(pidId = "unknown")))
        assertFalse(ConfigurationProjector.blockers(unknown).isEmpty())
        val alerts = readingCatalog.filter { it.source == "ECM" && it.id !in setOf("rpm", "coolant", "speed") }.take(32).map { row ->
            val span = row.maximum - row.minimum
            GaugeAlertDraft("alert.${row.id}", row.id, warning = row.minimum + span * .6, critical = row.minimum + span * .8, hysteresis = span * .05)
        }
        assertTrue(ConfigurationProjector.blockers(Draft(alerts = alerts)).any { "32 different" in it })
    }
    @Test fun finiteOrderedThresholdsAndGearCodesAreRequired() {
        listOf(temperature.copy(warning = Double.NaN), temperature.copy(critical = Double.POSITIVE_INFINITY),
            temperature.copy(hysteresis = Double.NaN), temperature.copy(hysteresis = 10.0),
            temperature.copy(direction = AlertDirection.Equals), gear.copy(direction = AlertDirection.Above),
            gear.copy(critical = 12.0), gear.copy(warning = 1.5), gear.copy(hysteresis = .1)).forEach {
            assertFalse("$it", ConfigurationProjector.blockers(Draft(alerts = listOf(it))).isEmpty())
        }
        assertNotNull(AlertForm(AlertDirection.Equals, "11", "13", "0").saved("tcmgear", readingBounds("tcmgear"), null))
        assertNotNull(AlertForm(AlertDirection.Below, "12.2", "11.7", "0.1").saved("voltage", readingBounds("voltage"), null))
        assertNotNull(AlertForm(warning = "0.8", critical = "1.2", reset = "0.02").saved("equivalence", readingBounds("equivalence"), null))
        assertTrue(AlertForm(warning = "-39", critical = "0", reset = "3").errors(readingBounds("coolant")).containsKey("reset"))
    }
    @Test fun imperialFractionalUnitsConvertWithoutChangingCanonicalThresholds() {
        val converted = MeasurementUnits.value(100.0, "kPa", MeasurementSystem.Imperial)
        assertEquals(14.50377377, converted, 1e-8)
        assertEquals(100.0, MeasurementUnits.canonical(converted, "kPa", MeasurementSystem.Imperial), 1e-8)
        assertEquals("14.2", MeasurementUnits.displayValue("14.2", "V", MeasurementSystem.Imperial))
        assertEquals("US gal/h", MeasurementUnits.label("L/h", MeasurementSystem.Imperial))
        assertEquals(.1, MeasurementUnits.canonicalDistance(MeasurementUnits.distance(.1, "°C", MeasurementSystem.Imperial), "°C", MeasurementSystem.Imperial), 1e-9)
    }
    @Test fun oldGaugeCanKeepExistingPagesButCannotReceiveNewAlertSemantics() {
        assertFalse(requiresPidCatalogFirmware(Draft()))
        assertTrue(requiresPidCatalogFirmware(Draft(alerts = listOf(temperature))))
        assertTrue(requiresPidCatalogFirmware(Draft(pages = listOf(GaugePageDraft("page.voltage", "VOLTAGE", GaugeLayout.Arc, listOf("voltage"))))))
        val raw = """{"protocolMajor":0,"board":"test","maxAdapterLinks":1,"configWrite":false,"ota":false,"cfg":5}"""
        val caps = GaugeProtocolCodec.capabilities(raw.toByteArray())
        assertEquals(1, caps.pidCatalogVersion)
        assertEquals(5, caps.configurationVersion)
        assertEquals(GaugeLayout.entries.toSet(), caps.supportedRenderers)
        assertEquals(0, GaugeProtocolCodec.capabilities(raw.replace("\"cfg\":5", "\"cfg\":4").toByteArray()).pidCatalogVersion)
    }
}
