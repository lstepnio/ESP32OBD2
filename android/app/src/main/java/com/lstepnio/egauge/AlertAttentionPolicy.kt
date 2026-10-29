package com.lstepnio.egauge

/** Ownership/current evidence is supplied by the live owner, never inferred from stored flags. */
fun canChangeAlertAttention(entry: StoredAlert, current: Boolean, activeOwner: Boolean, snooze: Boolean = false): Boolean {
    val event = entry.event
    if (!current || !activeOwner || event.lifecycle != AlertLifecycle.Active || event.simulated || event.unavailable || event.snoozed) return false
    return if (snooze) event.severity < AlertSeverity.Critical else !event.acknowledged
}
