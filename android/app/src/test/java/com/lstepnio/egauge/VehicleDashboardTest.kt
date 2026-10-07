package com.lstepnio.egauge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VehicleDashboardTest {
    private val vehicle = VehicleProfile("jeep", "Jeep", Draft(),
        AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"),
        TransmissionConnection(AdapterBinding("tcm", "AA:BB:CC:DD:EE:02", "public")))
    @Test fun combinedReadingsAndPageOrderSurviveStorageAndProjection() {
        assertTrue(ConfigurationProjector.pagePidIds("BOTH").containsAll(setOf("rpm", "tcmgear", "tcmtemp")))
        val combined = vehicle.combinedDraft()
        val changed = combined.copy(pages = combined.pages.reversed())
        val edited = vehicle.withDashboard(changed)
        assertEquals(changed.pages, edited.combinedDraft().pages)
        assertEquals(edited, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(ProfileCollection(edited.id,listOf(edited)))).active)
        val wire = JSONObject(ConfigurationProjector.projectCombined(File("src/main/assets/numeric_config_template.json").readText(),edited,33).second.toString(Charsets.UTF_8))
        assertEquals(changed.pages.map { it.id },(0 until wire.getJSONArray("pages").length()).map { wire.getJSONArray("pages").getJSONObject(it).getString("id") })
    }
    @Test fun addingAndRemovingPagesKeepsSiblingBindingsAndRoutesTheGesture() {
        val combined = vehicle.combinedDraft()
        val page = GaugePageDraft("page.custom.1", "TCM TEMP", GaugeLayout.Numeric, listOf("tcmtemp"))
        val edited = vehicle.withDashboard(combined.copy(pages = combined.pages + page, actions = listOf(PageAction(page.id))))
        assertEquals(vehicle.primaryAdapter,edited.primaryAdapter)
        assertEquals(vehicle.transmission!!.adapter,edited.transmission!!.adapter)
        assertEquals("child.page.custom.1",edited.combinedDraft().actions.single().pageId)
        val removed = edited.withDashboard(edited.combinedDraft().let { it.copy(pages = it.pages.filter { p -> p.id != "child.page.custom.1" }, actions=emptyList()) })
        assertTrue(removed.combinedDraft().actions.isEmpty())
        assertEquals(combined.pages,removed.combinedDraft().pages)
    }
    @Test fun mixedSourceDualAndRemovingTheLastControllerPageAreRejected() {
        val combined = vehicle.combinedDraft()
        val bad = combined.pages.first().copy(layout=GaugeLayout.Dual,pidIds=listOf("rpm","tcmtemp"))
        assertThrows(IllegalArgumentException::class.java) { vehicle.withDashboard(combined.copy(pages=listOf(bad)+combined.pages.drop(1))) }
        assertThrows(IllegalArgumentException::class.java) { vehicle.withDashboard(combined.copy(pages=vehicle.draft.pages)) }
        assertEquals(combined,vehicle.combinedDraft())
    }
    @Test fun movedSingleAdapterStillHasOneEditableVehicleDashboard() {
        val shared = vehicle.copy(transmission = vehicle.transmission!!.copy(adapter = vehicle.primaryAdapter))
        val dashboard = shared.dashboardDraft()
        assertEquals(vehicle.combinedDraft().pages, dashboard.pages)
        assertTrue(ConfigurationProjector.pagePidIds(dashboard.source).containsAll(setOf("rpm", "tcmtemp", "tcmgear")))
        val added = GaugePageDraft("page.custom.1", "ENGINE RPM", GaugeLayout.Numeric, listOf("rpm"))
        val edited = shared.withDashboard(dashboard.copy(pages = dashboard.pages + added))
        assertEquals(shared.transmission, edited.transmission)
        assertEquals(shared.primaryAdapter, edited.primaryAdapter)
        assertTrue(edited.dashboardDraft().pages.any { it.id == added.id })
        assertThrows(IllegalArgumentException::class.java) { edited.combinedDraft() }
        val restored = ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(ProfileCollection(edited.id, listOf(edited)))).active
        assertEquals(edited.dashboardDraft(), restored.dashboardDraft())
        val action = PageAction(dashboard.pages.first().id)
        assertEquals(listOf(action), shared.withCombinedPageAction(action).draft.actions)
        assertTrue(shared.withCombinedPageAction(action).transmission!!.draft.actions.isEmpty())
    }
    @Test fun unfinishedChildBindingDoesNotHideVehicleReadingsAndOrdinaryVehicleStaysSingle() {
        val unfinished = vehicle.copy(transmission = vehicle.transmission!!.copy(adapter = null))
        assertEquals(vehicle.combinedDraft().pages, unfinished.dashboardDraft().pages)
        assertThrows(IllegalArgumentException::class.java) { unfinished.combinedDraft() }
        val ordinary = vehicle.copy(transmission = null)
        assertEquals(ordinary.draft, ordinary.dashboardDraft())
        assertEquals(ConfigurationProjector.supportedPidIds, ConfigurationProjector.pagePidIds(ordinary.dashboardDraft().source))
    }
    @Test fun olderProfilesKeepTheirExistingOrderAndActions() {
        val profile = ProfileCollection(vehicle.id,listOf(vehicle))
        val old = JSONObject(ProfileDocumentCodec.encode(profile)).put("schemaVersion",7)
        old.getJSONArray("profiles").getJSONObject(0).remove("pageOrder")
        assertEquals(profile,ProfileDocumentCodec.decode(old.toString()))
    }
}
