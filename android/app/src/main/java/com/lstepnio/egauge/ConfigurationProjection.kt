package com.lstepnio.egauge

import org.json.JSONObject

data class ProjectedPage(val id: String, val label: String, val pidId: String, val renderer: String)

data class ConfigurationProjection(
    val pages: List<ProjectedPage>,
    val warning: Int,
    val critical: Int,
    val hysteresis: Int,
    val triggerDwellMs: Int,
    val clearDwellMs: Int,
) {
    fun reviewLines(): List<String> = pages.mapIndexed { index, page ->
        "${index + 1}. ${page.label} • ${page.renderer}"
    } + listOf(
        "Coolant warning at $warning °C; critical at $critical °C",
        "Alert after ${triggerDwellMs / 1000.0} s; clear after ${clearDwellMs / 1000.0} s; hysteresis $hysteresis °C",
    )
}

object ConfigurationProjector {
    val supportedPidIds = setOf("rpm", "coolant", "speed")

    fun blockers(draft: Draft): List<String> = buildList {
        if (draft.source != "ECM") add("The current firmware can send only the primary vehicle adapter")
        if (draft.pidId !in supportedPidIds) add("The selected reading is not available in the current sender")
        if (draft.layout != GaugeLayout.Numeric) add("The current firmware can send only Numeric pages")
        if (draft.warning !in -40..215 || draft.critical !in -40..215)
            add("Coolant limits must be between -40 and 215 °C")
        if (draft.warning >= draft.critical) add("Critical temperature must be above warning")
        if (draft.hysteresis !in 0..20 || draft.warning - draft.hysteresis < -40 ||
            draft.warning + draft.hysteresis >= draft.critical)
            add("Coolant hysteresis does not fit the selected limits")
        if (draft.triggerDwellMs !in 0..60000 || draft.clearDwellMs !in 0..60000)
            add("Alert timing is outside the supported range")
    }

    fun project(template: String, draft: Draft, profileId: String, baseRevision: Long): Pair<ConfigurationProjection, ByteArray> {
        val issues = blockers(draft)
        require(issues.isEmpty()) { issues.joinToString(". ") }
        val json = JSONObject(template)
        json.put("baseRevision", baseRevision)
        json.put("vehicleProfileId", profileId)
        val pages = json.getJSONArray("pages")
        val selectedDefinition = when (draft.pidId) {
            "rpm" -> "engine.rpm"
            "coolant" -> "engine.coolant"
            "speed" -> "vehicle.speed"
            else -> error("Unsupported primary reading")
        }
        var selectedIndex = -1
        for (index in 0 until pages.length()) {
            val page = pages.getJSONObject(index)
            page.put("renderer", "numeric")
            if (page.getJSONArray("pidIds").getString(0) == selectedDefinition) selectedIndex = index
        }
        require(selectedIndex >= 0) { "Configuration template does not contain the selected reading" }
        if (selectedIndex != 0) {
            val first = pages.getJSONObject(0)
            pages.put(0, pages.getJSONObject(selectedIndex))
            pages.put(selectedIndex, first)
        }
        val alert = json.getJSONArray("alerts").getJSONObject(0)
        alert.put("warning", draft.warning).put("critical", draft.critical)
            .put("hysteresis", draft.hysteresis).put("triggerDwellMs", draft.triggerDwellMs)
            .put("clearDwellMs", draft.clearDwellMs)
        val projection = ConfigurationProjection(
            pages = (0 until pages.length()).map { index ->
                val page = pages.getJSONObject(index)
                ProjectedPage(
                    page.getString("id"),
                    page.optString("label", page.getString("id")),
                    page.getJSONArray("pidIds").getString(0),
                    page.getString("renderer"),
                )
            },
            warning = draft.warning,
            critical = draft.critical,
            hysteresis = draft.hysteresis,
            triggerDwellMs = draft.triggerDwellMs,
            clearDwellMs = draft.clearDwellMs,
        )
        return projection to json.toString().toByteArray(Charsets.UTF_8)
    }
}
