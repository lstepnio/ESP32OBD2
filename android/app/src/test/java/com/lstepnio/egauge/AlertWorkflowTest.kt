package com.lstepnio.egauge

import com.lstepnio.egauge.ui.alertAttentionState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AlertWorkflowTest {
    private val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 108f, 105f, "Coolant", "degC", 0, 1)
    private fun entry(event: AlertEvent = this.event) = StoredAlert("one", "car", "gauge", 1000, true, event, JSONObject())

    @Test fun onlyCurrentRealAvailableOwnedEpisodesOfferAttentionCommands() {
        assertTrue(canChangeAlertAttention(entry(), true, true))
        assertTrue(canChangeAlertAttention(entry(), true, true, snooze = true))
        listOf(
            entry(event.copy(simulated = true)),
            entry(event.copy(unavailable = true)),
            entry(event.copy(lifecycle = AlertLifecycle.Resolved)),
            entry(event.copy(lifecycle = AlertLifecycle.Expired)),
            entry(event.copy(snoozed = true))
        ).forEach {
            assertFalse(canChangeAlertAttention(it, true, true))
            assertFalse(canChangeAlertAttention(it, true, true, snooze = true))
        }
        assertFalse(canChangeAlertAttention(entry(), false, true))
        assertFalse(canChangeAlertAttention(entry(), true, false))
        assertFalse(canChangeAlertAttention(entry(), false, true, snooze = true))
        assertFalse(canChangeAlertAttention(entry(), true, false, snooze = true))
    }

    @Test fun criticalCannotSnoozeAndSeenCanSnoozeWithoutAnotherAcknowledgment() {
        val critical = entry(event.copy(severity = AlertSeverity.Critical))
        assertTrue(canChangeAlertAttention(critical, true, true))
        assertFalse(canChangeAlertAttention(critical, true, true, snooze = true))
        val seen = entry(event.copy(acknowledged = true))
        assertFalse(canChangeAlertAttention(seen, true, true))
        assertTrue(canChangeAlertAttention(seen, true, true, snooze = true))
        val busy = alertAttentionState(entry(), true, true, busy = true)
        assertTrue(busy.offersSeen)
        assertTrue(busy.offersSnooze)
        assertTrue(busy.busy)
    }
}
