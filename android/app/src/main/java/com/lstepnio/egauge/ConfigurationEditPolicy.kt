package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject

/** A quick edit must not send an unrelated staged adapter, action or dashboard change. */
object ConfigurationEditPolicy {
    fun matchesRunning(
        document: GaugeConfigTransferClient.ActiveDocument,
        vehicle: VehicleProfile,
        bothAdapters: Boolean,
        actions: List<PageAction>
    ): Boolean = runCatching {
        require(document.vehicleProfileId == vehicle.id)
        val running = JSONObject(document.json)
        val template = JSONObject(document.json)
        // The projector starts from a primary-only template and adds the optional child.
        template.put("sources", JSONArray().put(template.getJSONArray("sources").getJSONObject(0)))
        val projected = ConfigurationProjector.projectVehicle(
            template.toString(),
            vehicle,
            running.getLong("baseRevision"),
            bothAdapters,
            running.getInt("schemaVersion"),
            actions
        ).second
        normalized(running) == normalized(JSONObject(projected.toString(Charsets.UTF_8)))
    }.getOrDefault(false)

    private fun normalized(value: Any?, depth: Int = 0): Any? {
        require(depth < 32)
        return when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { normalized(value.get(it), depth + 1) }
            is JSONArray -> (0 until value.length()).map { normalized(value.get(it), depth + 1) }
            is Number -> value.toString().toBigDecimal().stripTrailingZeros()
            JSONObject.NULL -> null
            else -> value
        }
    }
}
