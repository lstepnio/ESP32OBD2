package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.configuredSourceIndices
import com.lstepnio.egauge.ui.state.presentationBlockers
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DualAdapterConfigurationTest {
    private val engine = AdapterBinding("ecm.radio", "AA:BB:CC:DD:EE:01", "public")
    private val child = AdapterBinding("tcm.radio", "AA:BB:CC:DD:EE:02", "random")
    private val vehicle = VehicleProfile("jeep", "Jeep", Draft(), engine, TransmissionConnection(child))
    private fun template() = File("src/main/assets/numeric_config_template.json").readText()

    @Test fun combinedPayloadPreservesBothBindingsPagesAndEngineAlerts() {
        val (review, bytes) = ConfigurationProjector.projectCombined(template(), vehicle, 29)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(2, root.getInt("schemaVersion"))
        assertEquals(29, root.getInt("baseRevision"))
        assertEquals("jeep", root.getString("vehicleProfileId"))
        val sources = root.getJSONArray("sources")
        assertEquals(2, sources.length())
        assertEquals(engine, AdapterBinding.decode(sources.getJSONObject(0).getJSONObject("adapter")))
        assertEquals(child, AdapterBinding.decode(sources.getJSONObject(1).getJSONObject("adapter")))
        assertEquals(vehicle.draft.pages.size + vehicle.transmission!!.draft.pages.size, review.pages.size)
        assertTrue(review.pages.last().id.startsWith("child."))
        assertEquals(vehicle.draft.alerts.size, review.alerts.size)
        val definitions = root.getJSONArray("definitions")
        assertEquals(setOf("ecm", "tcm"), (0 until definitions.length()).map {
            definitions.getJSONObject(it).getString("sourceId") }.toSet())
    }

    @Test fun childEditsChangeCombinedBytesWithoutChangingPrimaryDraft() {
        val original = ConfigurationProjector.projectCombined(template(), vehicle, 29).second
        val revised = vehicle.withDraft(vehicle.transmission!!.draft.copy(pages = listOf(
            GaugePageDraft("gear.only", "GEAR", GaugeLayout.Numeric, listOf("tcmgear")))))
        assertEquals(vehicle.draft, revised.draft)
        assertFalse(original.contentEquals(ConfigurationProjector.projectCombined(template(), revised, 29).second))
    }

    @Test fun incompleteChildAndExcessCombinedPagesFailBeforeSending() {
        assertThrows(IllegalArgumentException::class.java) { vehicle.copy(transmission = null).combinedDraft() }
        assertThrows(IllegalArgumentException::class.java) { vehicle.copy(transmission = TransmissionConnection()).combinedDraft() }
        val many = vehicle.copy(draft = vehicle.draft.copy(pages = (1..8).map {
            GaugePageDraft("engine.$it", "RPM", GaugeLayout.Numeric, listOf("rpm")) }))
        assertTrue(ConfigurationProjector.blockers(many.combinedDraft()).isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) { ConfigurationProjector.projectCombined(template(), many, 29) }
    }

    @Test fun mixedSourcePageIsRejected() {
        val draft = vehicle.combinedDraft().copy(pages = listOf(
            GaugePageDraft("mixed", "MIXED", GaugeLayout.Dual, listOf("rpm", "tcmtemp"))))
        assertTrue(ConfigurationProjector.blockers(draft).isNotEmpty())
    }

    @Test fun adapterModeIsPerGaugeAndMissingChildNeverFallsBack() {
        val primary = KnownGauge("g1", "Dashboard", "jeep", "ECM", bothAdapters = true)
        val second = KnownGauge("g2", "Transmission", "jeep", "TCM")
        val saved = GaugeAssociations("g1", listOf(primary, second))
        assertEquals(saved, GaugeAssociationDocumentCodec.decode(GaugeAssociationDocumentCodec.encode(saved)))
        val profiles = ProfileCollection(vehicle.id, listOf(vehicle))
        assertNotNull(saved.context("g1", profiles))
        assertNull(saved.context("g1", profiles.copy(profiles = listOf(vehicle.copy(transmission = null)))))
        assertFalse(saved.assign("g2", "jeep", "ECM").gauges[1].bothAdapters)
        assertTrue(saved.assign("g1", "jeep", "TCM").gauges[0].bothAdapters)
    }

    @Test fun sourceIndicesComeFromConfirmedDocumentOrder() {
        val bytes = ConfigurationProjector.projectCombined(template(), vehicle, 29).second
        val document = GaugeConfigTransferClient.ActiveDocument(30, "a".repeat(64), bytes.size, "jeep", 1, 1, 0, bytes.toString(Charsets.UTF_8))
        assertEquals(mapOf("ECM" to 0, "TCM" to 1), configuredSourceIndices(document))
        val root = JSONObject(document.json)
        root.getJSONArray("sources").getJSONObject(1).put("role", "ecm")
        assertTrue(configuredSourceIndices(document.copy(json = root.toString())).isEmpty())
        assertNull(GaugeDraftComparison.savedDraft(document, "jeep"))
    }
}
