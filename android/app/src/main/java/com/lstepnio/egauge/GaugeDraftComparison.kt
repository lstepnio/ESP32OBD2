package com.lstepnio.egauge

import org.json.JSONObject
import java.util.Locale

/** A comparison of one verified gauge readback with the current local draft. */
data class GaugeDraftField(val label: String, val phone: String, val gauge: String?, val matches: Boolean?)

data class GaugeDraftComparison(val revision: Long, val fields: List<GaugeDraftField>) {
    val matchingCount: Int get() = fields.count { it.matches == true }
    val differingCount: Int get() = fields.count { it.matches == false }
    val unknownCount: Int get() = fields.count { it.matches == null }

    companion object {
        fun from(document: GaugeConfigTransferClient.ActiveDocument,
                 profileId: String, draft: Draft): GaugeDraftComparison {
            val saved = JSONObject(document.json)
            val pages = saved.optJSONArray("pages")
            val primary = pages?.optJSONObject(0)
            val primaryPid = primary?.optJSONArray("pidIds")?.optString(0)?.takeIf { it.isNotBlank() }
            val definitions = saved.optJSONArray("definitions")
            val definition = (0 until (definitions?.length() ?: 0))
                .mapNotNull { definitions?.optJSONObject(it) }
                .firstOrNull { it.optString("id") == primaryPid }
            val sourceId = definition?.optString("sourceId")?.takeIf { it.isNotBlank() }
            val sources = saved.optJSONArray("sources")
            val sourceRole = (0 until (sources?.length() ?: 0))
                .mapNotNull { sources?.optJSONObject(it) }
                .firstOrNull { it.optString("id") == sourceId }
                ?.optString("role")?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
            val alerts = saved.optJSONArray("alerts")
            val coolantAlert = (0 until (alerts?.length() ?: 0))
                .mapNotNull { alerts?.optJSONObject(it) }
                .firstOrNull { it.optString("pidId") == "engine.coolant" &&
                    it.optString("direction") == "above" }

            fun field(label: String, phone: String, gauge: String?): GaugeDraftField =
                GaugeDraftField(label, phone, gauge, gauge?.let { it == phone })
            fun alertField(label: String, phone: Int, key: String, unit: String): GaugeDraftField {
                val raw = coolantAlert?.opt(key)
                val value = (raw as? Number)?.takeIf { it.toDouble() == it.toInt().toDouble() }
                    ?.toInt()
                return field(label, "$phone $unit", value?.let { "$it $unit" })
            }
            val draftDefinition = when (draft.pidId) {
                "rpm" -> "engine.rpm"
                "coolant" -> "engine.coolant"
                "speed" -> "vehicle.speed"
                "load" -> "engine.load"
                "fuel" -> "vehicle.fuel"
                else -> draft.pidId
            }
            return GaugeDraftComparison(document.revision, listOf(
                field("Vehicle profile ID", profileId, document.vehicleProfileId),
                field("Primary PID", draftDefinition, primaryPid),
                field("Renderer", draft.layout.name.lowercase(Locale.ROOT),
                    primary?.optString("renderer")?.takeIf { it.isNotBlank() }),
                field("Primary source", draft.source, sourceRole),
                alertField("Coolant warning", draft.warning, "warning", "°C"),
                alertField("Coolant critical", draft.critical, "critical", "°C"),
                alertField("Hysteresis", draft.hysteresis, "hysteresis", "°C"),
                alertField("Alert after", draft.triggerDwellMs, "triggerDwellMs", "ms"),
                alertField("Clear after", draft.clearDwellMs, "clearDwellMs", "ms"),
            ))
        }
    }
}
