package com.lstepnio.egauge

import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.*
import com.lstepnio.egauge.ui.alertEntryStatus
import org.junit.Assert.*
import org.junit.Test

class CarPresentationTest {
    private fun state(vararg sources: FaultSourceUi, incomplete: Boolean = false) = CarUiState(
        "Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", vehicleFaultStatus(sources.toList()),
        emptyList(), true, incomplete, false, emptyList(), faultSources = sources.toList())
    private fun source(title: String, tone: StatusTone, vararg categories: FaultCategoryUi) =
        FaultSourceUi(title, StatusUi("fixture", tone = tone), categories.toList())
    private val fault = FaultUi("P0700", "Transmission fault", "Stored")

    @Test fun healthyEngineCannotHideMissingChildOrLoseItsSavedCode() {
        val s = state(source("Engine", StatusTone.Success), source("Transmission", StatusTone.Stale,
            FaultCategoryUi("Stored", "Out of date", listOf(fault.copy(category = "Last checked · Stored")))))
        assertEquals("Fault checks incomplete", carHealthStatus(s).title)
        assertTrue(carHealthStatus(s).detail.contains("Transmission"))
        assertEquals("Last checked · Stored", carFaults(s).single().categories)
    }
    @Test fun initialWaitingHasNoAllClearOrEmptyCategoryNoise() {
        val s = state(source("Engine", StatusTone.Disabled))
        assertEquals("Waiting for car", carHealthStatus(s).title)
        assertTrue(carFaults(s).isEmpty())
    }
    @Test fun partialHealthyResultIsStillIncomplete() {
        val s = state(source("Engine", StatusTone.Success), incomplete = true)
        assertEquals("Fault checks incomplete", carHealthStatus(s).title)
        assertEquals(StatusTone.Stale, carHealthStatus(s).tone)
    }
    @Test fun categoryTruncationCannotBecomeAnAllClear() {
        val s = state(source("Engine", StatusTone.Success, FaultCategoryUi("Stored", "", emptyList(), true)))
        assertEquals("Fault checks incomplete", carHealthStatus(s).title)
    }
    @Test fun warningWinsWhileMissingSiblingRemainsExplicit() {
        val s = state(source("Engine", StatusTone.Warning), source("Transmission", StatusTone.Stale))
        assertEquals("Vehicle warning reported", carHealthStatus(s).title)
        assertEquals(StatusTone.Warning, carHealthStatus(s).tone)
        assertTrue(carHealthStatus(s).detail.contains("Transmission"))
    }
    @Test fun lampOffDoesNotHideCodes() {
        val s = state(source("Engine", StatusTone.Success, FaultCategoryUi("Stored", "", listOf(fault))))
        assertEquals("Fault codes recorded", carHealthStatus(s).title)
        assertTrue(carHealthStatus(s).detail.contains("warning light can be off"))
    }
    @Test fun onlyFreshSuccessCanReportNoWarning() {
        assertEquals("No warning reported", carHealthStatus(state(source("Engine", StatusTone.Success))).title)
        assertNotEquals("No warning reported", carHealthStatus(state(source("Engine", StatusTone.Neutral))).title)
    }
    @Test fun duplicateCategoriesMergeButIndependentControllersRemainSeparate() {
        val categories = arrayOf(FaultCategoryUi("Stored", "", listOf(fault)),
            FaultCategoryUi("Pending", "", listOf(fault.copy(category = "Pending"))))
        val s = state(source("Engine", StatusTone.Neutral, *categories), source("Transmission", StatusTone.Neutral, *categories))
        assertEquals(2, carFaults(s).size)
        assertEquals(listOf("Engine", "Transmission"), carFaults(s).map { it.source })
        assertTrue(carFaults(s).all { it.categories == "Stored · Pending" })
    }
    private val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Critical, false, AlertLifecycle.Active,
        1, false, 100f, 90f, "Coolant", "degC", 0, 1)
    @Test fun unavailableAlertNeverReadsResolvedOrAcknowledged() {
        assertEquals("Critical · Data unavailable · not confirmed resolved", alertEntryStatus(event.copy(unavailable = true, acknowledged = true), true))
        assertEquals("Critical · Last checked", alertEntryStatus(event, false))
        assertEquals("Critical · Resolved", alertEntryStatus(event.copy(lifecycle = AlertLifecycle.Resolved), false))
    }
    @Test fun simulatedExpiryKeepsBothMeanings() {
        assertEquals("Simulated · Critical · Expired", alertEntryStatus(event.copy(simulated = true, lifecycle = AlertLifecycle.Expired), false))
    }
}
