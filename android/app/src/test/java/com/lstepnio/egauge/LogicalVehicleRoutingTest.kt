package com.lstepnio.egauge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LogicalVehicleRoutingTest {
    private val adapter = AdapterBinding("primary", "AA:BB:CC:DD:EE:01", "public")
    private val child = AdapterBinding("child", "AA:BB:CC:DD:EE:02", "public")
    private val template = File("src/main/assets/numeric_config_template.json").readText()
    private val mixed = GaugePageDraft("page.mixed", "RPM + TEMP", GaugeLayout.Dual, listOf("rpm", "tcmtemp"))
    private fun wire(vehicle: VehicleProfile, second: Boolean) =
        ConfigurationProjector.projectVehicle(template, vehicle, 42, second).let {
            it.first to JSONObject(it.second.toString(Charsets.UTF_8))
        }
    private fun definitions(root: JSONObject) = root.getJSONArray("definitions").let { values ->
        (0 until values.length()).associate { i -> values.getJSONObject(i).let { it.getString("id") to it } }
    }

    @Test fun ordinaryVehicleUsesOneTransportForAllConfiguredReadings() {
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(pages = listOf(mixed)), adapter)
        val (review, root) = wire(vehicle, false)
        assertEquals(1, root.getJSONArray("sources").length())
        assertEquals("ecm", root.getJSONArray("sources").getJSONObject(0).getString("role"))
        assertEquals(listOf(mixed.id), review.pages.map { it.id })
        assertTrue(definitions(root).values.all { it.getString("sourceId") == "ecm" })
        assertEquals("7E8", definitions(root).getValue("engine.rpm").getJSONObject("request").getString("responseId"))
        assertEquals("7E9", definitions(root).getValue("transmission.temperature.experimental").getJSONObject("request").getString("responseId"))
        assertEquals("22", definitions(root).getValue("transmission.temperature.experimental").getJSONObject("request").getString("service"))
        assertTrue(ConfigurationProjector.pagePidIds(vehicle.dashboardDraft().source).containsAll(setOf("rpm", "tcmtemp", "tcmgear")))
    }

    @Test fun enablingAChildChangesRoutingWithoutChangingTheDashboard() {
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(pages = listOf(mixed), actions = listOf(PageAction(mixed.id))), adapter,
            TransmissionConnection(child, TransmissionSetup.draft().copy(pages = emptyList())))
        val (singleReview, single) = wire(vehicle, false)
        val (dualReview, dual) = wire(vehicle, true)
        assertEquals(singleReview, dualReview)
        for (field in listOf("pages", "alerts", "actions")) assertEquals(single.getJSONArray(field).toString(), dual.getJSONArray(field).toString())
        assertEquals(2, dual.getJSONArray("sources").length())
        assertEquals("ecm", definitions(dual).getValue("engine.rpm").getString("sourceId"))
        assertEquals("tcm", definitions(dual).getValue("transmission.temperature.experimental").getString("sourceId"))
        assertEquals(vehicle, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(ProfileCollection(vehicle.id, listOf(vehicle)))).active)
    }

    @Test fun legacyTcmOnlyVehicleCanAddEngineReadingsWithoutAnExpertSwitch() {
        val original = VehicleProfile("old.jeep", "Jeep", TransmissionSetup.draft(), adapter)
        assertEquals("ECM", original.dashboardDraft().source)
        val added = original.withDashboard(original.dashboardDraft().let { it.copy(pages = it.pages + mixed) })
        assertEquals("ECM", added.draft.source)
        assertEquals(original.id, added.id)
        assertEquals(original.primaryAdapter, added.primaryAdapter)
        assertNull(added.transmission)
        val (review, root) = wire(added, false)
        assertEquals(added.dashboardDraft().pages.map { it.id }, review.pages.map { it.id })
        assertEquals(1, root.getJSONArray("sources").length())
        assertTrue(definitions(root).values.all { it.getString("sourceId") == "ecm" })
        assertEquals(added, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(ProfileCollection(added.id, listOf(added)))).active)
    }

    @Test fun aMovedOrUnselectedChildUsesThePrimaryTransportUnlessExplicitlyEnabled() {
        for (binding in listOf(adapter, null)) {
            val vehicle = VehicleProfile("jeep", "Jeep", Draft(), adapter, TransmissionConnection(binding))
            assertEquals(vehicle.dashboardDraft().pages.size, wire(vehicle, false).first.pages.size)
            assertThrows(IllegalArgumentException::class.java) { wire(vehicle, true) }
        }
    }

    @Test fun removingTheSecondTransportRetainsAllPagesAlertsAndGestures() {
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(), adapter, TransmissionConnection(child))
            .let { it.withCombinedPageAction(PageAction(it.dashboardDraft().pages.last().id)) }
        val before = wire(vehicle, true).first
        val removed = vehicle.withoutTransmissionAdapter()
        assertNull(removed.transmission)
        assertEquals(before, wire(removed, false).first)
        assertEquals(vehicle.primaryAdapter, removed.primaryAdapter)
    }

    @Test fun capabilityAliasesKeepLegacyAndNewDiscoveryCompatible() {
        val root = JSONObject().put("board", "ESP32-S3-Touch-LCD-1.28").put("protocolMajor", 0)
            .put("maxAdapterLinks", 1).put("configWrite", false).put("ota", false).put("cfg", 4)
        fun decode() = GaugeProtocolCodec.capabilities(root.toString().toByteArray())
        assertEquals(0, decode().vehicleDashboardVersion)
        root.put("va", 1).put("qs", true)
        assertEquals(1, decode().vehicleDashboardVersion)
        assertTrue(decode().quickSelect)
        root.remove("qs"); root.put("quickSelect", true)
        assertTrue(decode().quickSelect)
        root.put("va", 2)
        assertThrows(IllegalArgumentException::class.java) { decode() }
    }

    @Test fun newExecutionRequiresItsCapabilityWhileAnEngineOnlySetupRemainsCompatible() {
        assertFalse(requiresVehicleDashboardFirmware(Draft(), false))
        assertTrue(requiresVehicleDashboardFirmware(Draft(pages = listOf(mixed)), false))
        assertTrue(requiresVehicleDashboardFirmware(Draft(), true))
    }
}
