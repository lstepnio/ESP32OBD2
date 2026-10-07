package com.lstepnio.egauge

import com.lstepnio.egauge.connection.VehiclePollSchedule
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class VehicleConnectionUxTest {
    private val connected = StatusUi("Car connected", "", StatusTone.Success)
    private val retrying = StatusUi("Connecting to car", "", StatusTone.Loading)
    private fun link(id: String, status: StatusUi) = ConnectionLinkUi(id, id, status)
    private val status = AdapterSourceStatus(4, 0, true, 1, 0, 0x7e8, "jeep", "engine", false, emptyMap(), 100)

    @Test fun dualAdaptersRequireBothLinksAndTheGaugeToBeReady() {
        val links = listOf(link("engine", connected), link("transmission", connected))
        assertEquals("Connected", appConnectionLabel("Gauge ready", true, links))
        assertEquals("Car reconnecting", appConnectionLabel("Gauge ready", true,
            listOf(links.first(), link("transmission", retrying))))
        assertEquals("Reconnecting", appConnectionLabel("Reconnecting", false, links))
        assertEquals("Car setup needed", appConnectionLabel("Gauge ready", true, emptyList()))
        assertEquals("Example mode", appConnectionLabel("Gauge ready", true,
            listOf(link("engine", StatusUi("Example adapter", "", StatusTone.Disabled)), links.last())))
    }
    @Test fun aReadMustBeFreshRealBoundAndScopedToTheSelectedCarAndSource() {
        fun present(value: AdapterSourceStatus = status, age: Long = 0, failed: Boolean = false) =
            vehicleConnectionStatus(value, age, "jeep", "engine", true, true, failed)
        assertEquals(StatusTone.Success, present(age = 30_000).tone)
        assertNotEquals(StatusTone.Success, present(age = 30_001).tone)
        assertNotEquals(StatusTone.Success, present(age = -1).tone)
        assertNotEquals(StatusTone.Success, present(failed = true).tone)
        assertNotEquals(StatusTone.Success, present(status.copy(vehicleId = "other")).tone)
        assertNotEquals(StatusTone.Success, present(status.copy(sourceId = "tcm")).tone)
        assertNotEquals(StatusTone.Success, present(status.copy(simulated = true)).tone)
        assertNotEquals(StatusTone.Success, present(status.copy(bound = false)).tone)
        assertNotEquals(StatusTone.Success, present(status.copy(phase = 6)).tone)
    }
    @Test fun settingsRequireAFreshSuccessfulReadBeforeEditing() {
        assertTrue(settingsReadCurrent(1_000, 61_000, false))
        assertFalse(settingsReadCurrent(1_000, 61_001, false))
        assertFalse(settingsReadCurrent(1_000, 999, false))
        assertFalse(settingsReadCurrent(1_000, 1_001, true))
        assertFalse(settingsReadCurrent(null, 1_001, false))
    }
    @Test fun connectionSetupUsesTheConfiguredSourceIdAndCompleteBinding() {
        val adapter = AdapterBinding("vgate", "AA:BB:CC:DD:EE:FF", "public")
        val json = org.json.JSONObject().put("sources", org.json.JSONArray().put(
            org.json.JSONObject().put("id", "engine").put("role", "ecm").put("adapter", adapter.json())))
        val document = GaugeConfigTransferClient.ActiveDocument(1, "a".repeat(64), 0, "jeep", 1, 1, 0, json.toString())
        assertEquals("engine", configuredSourceId(document, "ECM"))
        assertTrue(vehicleSetupMatches(document, "jeep", "ECM", adapter))
        assertFalse(vehicleSetupMatches(document, "other", "ECM", adapter))
        assertFalse(vehicleSetupMatches(document, "jeep", "TCM", adapter))
        assertFalse(vehicleSetupMatches(document, "jeep", "ECM", adapter.copy(addressType = "random")))
        assertFalse(vehicleSetupMatches(document, "jeep", "ECM", null))
    }
    @Test fun resumeMakesEachIndependentReadDueImmediately() {
        val settings = VehiclePollSchedule()
        val child = VehiclePollSchedule()
        settings.due("gauge:settings", 100)
        child.due("gauge:tcm", 100)
        settings.completed(100, true)
        child.completed(100, false)
        assertFalse(settings.due("gauge:settings", 101))
        assertFalse(child.due("gauge:tcm", 101))
        settings.reset()
        assertTrue(settings.due("gauge:settings", 101))
        assertFalse(child.due("gauge:tcm", 101))
        child.reset()
        assertTrue(child.due("gauge:tcm", 101))
        child.completed(101, false)
        assertTrue(child.due("gauge:tcm", 2101))
    }
    @Test fun vehicleRetriesBackOffResetAfterSuccessAndRestartForNewBinding() {
        val schedule = VehiclePollSchedule()
        var now = 1_000L
        assertTrue(schedule.due("engine:first", now))
        listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L, 30_000L).forEach { delay ->
            schedule.completed(now, false)
            assertFalse(schedule.due("engine:first", now + delay - 1))
            now += delay
            assertTrue(schedule.due("engine:first", now))
        }
        schedule.completed(now, true)
        assertFalse(schedule.due("engine:first", now + 19_999))
        assertTrue(schedule.due("engine:first", now + 20_000))
        schedule.completed(now + 20_000, false)
        assertTrue(schedule.due("engine:first", now + 22_000))
        assertTrue(schedule.due("transmission:second", now))
    }
}
