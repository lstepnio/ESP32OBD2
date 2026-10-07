package com.lstepnio.egauge.connection

/** Read-only foreground polling. A failure never invalidates an independently checked gauge. */
class VehiclePollSchedule {
    private var key: String? = null
    private var failures = 0
    private var nextAt = 0L
    fun reset() { key = null; failures = 0; nextAt = 0 }
    fun due(scope: String, now: Long): Boolean {
        if (key != scope) { key = scope; failures = 0; nextAt = 0 }
        return now >= nextAt
    }
    fun completed(now: Long, healthy: Boolean) {
        failures = if (healthy) 0 else (failures + 1).coerceAtMost(5)
        nextAt = now + if (healthy) 20_000L else reconnectDelayMs(failures)
    }
    fun pause(now: Long): Long = (nextAt - now).coerceIn(1_000L, 20_000L)
}
