package com.lstepnio.egauge

import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.alertEntryStatus
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class CarPresentationTest {
    private fun state(vararg sources: FaultSourceUi, incomplete: Boolean = false) = CarUiState(
        "Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", vehicleFaultStatus(sources.toList()),
        emptyList(), true, incomplete, false, emptyList(), faultSources = sources.toList()
    )
    private fun source(title: String, tone: StatusTone, vararg categories: FaultCategoryUi) = FaultSourceUi(title, StatusUi("fixture", tone = tone), categories.toList())
    private val fault = FaultUi("P0700", "Transmission fault", "Stored")

    @Test fun duplicateCategoriesMergeButIndependentControllersRemainSeparate() {
        val categories = arrayOf(
            FaultCategoryUi("Stored", "", listOf(fault)),
            FaultCategoryUi("Pending", "", listOf(fault.copy(category = "Pending")))
        )
        val s =
            state(
                source("Engine", StatusTone.Neutral, *categories),
                source("Transmission", StatusTone.Neutral, *categories)
            )
        assertEquals(2, carFaults(s).size)
        assertEquals(listOf("Engine", "Transmission"), carFaults(s).map { it.source })
        assertTrue(carFaults(s).all { it.categories == "Stored · Pending" })
    }
    private val event =
        AlertEvent(
            1, 1, 1, 0, 0, AlertSeverity.Critical, false, AlertLifecycle.Active,
            1, false, 100f, 90f, "Coolant", "degC", 0, 1
        )

    @Test fun unavailableSummaryDoesNotPresentRecordedValueAsCurrent() {
        assertEquals("Current reading unavailable", carAlertSummary(event.copy(unavailable = true), MeasurementSystem.Metric))
    }

    @Test fun unavailableAlertNeverReadsResolvedOrAcknowledged() {
        assertEquals(
            "Critical · Data unavailable · not confirmed resolved",
            alertEntryStatus(event.copy(unavailable = true, acknowledged = true), true)
        )
        assertEquals("Critical · Last checked", alertEntryStatus(event, false))
        assertEquals(
            "Critical · Resolved",
            alertEntryStatus(event.copy(lifecycle = AlertLifecycle.Resolved), false)
        )
    }

    @Test fun simulatedExpiryKeepsBothMeanings() {
        assertEquals(
            "Simulated · Critical · Expired",
            alertEntryStatus(
                event.copy(simulated = true, lifecycle = AlertLifecycle.Expired),
                false
            )
        )
    }

    @Test fun attentionMergesKnownDiagnosticEpisodesButRetainsOtherControllerWarning() {
        val s = state(
            source(
                "Engine",
                StatusTone.Warning,
                FaultCategoryUi("Stored", "", listOf(fault)),
                FaultCategoryUi("Pending", "", listOf(fault.copy(category = "Pending")))
            )
        )
        val episodes = listOf(
            CarAttentionAlert("engine", "Engine warning", "", 3, "Engine"),
            CarAttentionAlert("transmission", "Transmission warning", "", 3, "Transmission")
        )
        val issues = carAttention(s, episodes)
        assertEquals(2, issues.size)
        assertEquals("transmission", issues.first().alertId)
        assertEquals("P0700", issues.last().fault!!.code)
        assertEquals(listOf("Stored", "Pending"), issues.last().fault!!.categoryNames)
    }

    @Test fun attentionPrioritizesCriticalThenThresholdsAndKeepsEqualSeverityOrder() {
        val s = state(source("Engine", StatusTone.Warning, FaultCategoryUi("Stored", "", listOf(fault))))
        val alerts = listOf(
            CarAttentionAlert("first", "First threshold", "", 3),
            CarAttentionAlert("critical", "Critical threshold", "", 4),
            CarAttentionAlert("second", "Second threshold", "", 3)
        )
        assertEquals(listOf("critical", "first", "second", null), carAttention(s, alerts).map { it.alertId })
    }

    @Test fun aFreshCodeDoesNotDependOnTheLampOrAnotherCategory() {
        val s = state(
            source(
                "Engine",
                StatusTone.Stale,
                FaultCategoryUi("Stored", "Checked now", listOf(fault)),
                FaultCategoryUi("Pending", "Out of date", listOf(fault.copy(current = false)))
            )
        )
        assertEquals("P0700", carAttention(s, emptyList()).single().fault!!.code)
        assertFalse(carChecksComplete(s))
    }

    @Test fun staleFaultsStayOutOfCurrentAttentionAndIncompleteChecksCannotClaimQuietSuccess() {
        val s = state(source("Transmission", StatusTone.Stale, FaultCategoryUi("Stored", "", listOf(fault.copy(current = false)))))
        assertTrue(carAttention(s, emptyList()).isEmpty())
        assertFalse(carChecksComplete(s))
        val complete = state(source("Engine", StatusTone.Success, FaultCategoryUi("Stored", "", emptyList())))
        assertTrue(carChecksComplete(complete))
        assertFalse(carChecksComplete(complete.copy(incomplete = true)))
        assertFalse(carChecksComplete(state(source("Engine", StatusTone.Success))))
    }

    @Test fun duplicateUnknownDiagnosticProducersShareOneAttentionRowPerController() {
        val alerts = listOf(
            CarAttentionAlert("mil", "Transmission warning", "", 3, "Transmission"),
            CarAttentionAlert("codes", "Transmission warning", "", 3, "Transmission"),
            CarAttentionAlert("engine", "Engine warning", "", 3, "Engine")
        )
        assertEquals(listOf("mil", "engine"), carAttention(state(), alerts).map { it.alertId })
    }

    @Test fun newerLowPriorityCodeCheckCannotHideOlderActiveLampSeverity() {
        for (lowSeverity in listOf(1, 2)) {
            val alerts = listOf(
                CarAttentionAlert("new-codes", "Engine codes", "", lowSeverity, "Engine"),
                CarAttentionAlert("threshold", "Threshold", "", 2),
                CarAttentionAlert("older-mil", "Engine warning light", "", 3, "Engine")
            )
            val rows = carAttention(state(), alerts)
            assertEquals(listOf("older-mil", "threshold"), rows.map { it.alertId })
            assertEquals(3, rows.first().severity)
            assertEquals("Engine warning light", rows.first().title)
        }
    }
}
