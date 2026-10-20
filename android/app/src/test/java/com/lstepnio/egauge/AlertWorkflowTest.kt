package com.lstepnio.egauge

import com.lstepnio.egauge.ui.AlertListItem
import com.lstepnio.egauge.ui.alertEntryStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AlertWorkflowTest {
    private val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 108f, 105f, "Coolant", "degC", 0, 1)
    private fun item(e: AlertEvent = event, current: Boolean = true, busy: Boolean = false) = AlertListItem(StoredAlert("episode", "vehicle", "gauge", 0, true, e, JSONObject()), current, MeasurementSystem.Metric, busy)

    @Test fun directActionsRequireCurrentRealActiveEpisodeAndIdleOperation() {
        assertTrue(item().canMarkSeen)
        assertTrue(item().canSnooze)
        listOf(item(current = false), item(busy = true), item(event.copy(simulated = true)), item(event.copy(lifecycle = AlertLifecycle.Resolved)), item(event.copy(lifecycle = AlertLifecycle.Expired))).forEach {
            assertFalse(it.canMarkSeen)
            assertFalse(it.canSnooze)
        }
        assertFalse(item(event.copy(severity = AlertSeverity.Critical)).canSnooze)
        assertTrue(item(event.copy(severity = AlertSeverity.Critical)).canMarkSeen)
    }

    @Test fun seenAndSnoozedConditionsRemainActiveWithoutRepeatedAttention() {
        listOf(event.copy(acknowledged = true), event.copy(snoozed = true)).forEach {
            assertTrue(item(it).active)
            assertFalse(item(it).needsAttention)
            assertFalse(item(it).canMarkSeen)
        }
        assertTrue(item().needsAttention)
        assertEquals("Warning · Seen · still active", alertEntryStatus(event.copy(acknowledged = true), true))
        assertEquals("Warning · Data unavailable · not confirmed resolved", alertEntryStatus(event.copy(unavailable = true, acknowledged = true), true))
    }
}
