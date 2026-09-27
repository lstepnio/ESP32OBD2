package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ProjectedPage(
    val id: String,
    val label: String,
    val pidId: String,
    val renderer: String,
    val secondaryPidId: String? = null,
)

data class ConfigurationProjection(
    val pages: List<ProjectedPage>,
    val warning: Int,
    val critical: Int,
    val hysteresis: Int,
    val triggerDwellMs: Int,
    val clearDwellMs: Int,
) {
    fun reviewLines(): List<String> = pages.mapIndexed { index, page ->
        val values = listOfNotNull(page.pidId, page.secondaryPidId).joinToString(" + ")
        "${index + 1}. ${page.label} • ${page.renderer} • $values"
    } + listOf(
        "Coolant warning at $warning °C; critical at $critical °C",
        "Alert after ${triggerDwellMs / 1000.0} s; clear after ${clearDwellMs / 1000.0} s; hysteresis $hysteresis °C",
    )
}

object ConfigurationProjector {
    val supportedPidIds = setOf("rpm", "coolant", "speed", "load", "fuel")
    private val definitionIds = mapOf(
        "rpm" to "engine.rpm",
        "coolant" to "engine.coolant",
        "speed" to "vehicle.speed",
        "load" to "engine.load",
        "fuel" to "vehicle.fuel",
    )

    fun blockers(draft: Draft): List<String> = buildList {
        if (draft.source != "ECM") add("The current firmware can send only the primary vehicle adapter")
        if (draft.pages.size !in 1..8) add("Choose between one and eight gauge pages")
        if (draft.pages.map { it.id }.distinct().size != draft.pages.size)
            add("Every page needs a unique identity")
        draft.pages.forEachIndexed { index, page ->
            val expected = if (page.layout == GaugeLayout.Dual) 2 else 1
            if (page.pidIds.size != expected || page.pidIds.distinct().size != page.pidIds.size)
                add("Page ${index + 1} needs $expected distinct reading${if (expected == 1) "" else "s"}")
            if (page.pidIds.any { it !in supportedPidIds })
                add("Page ${index + 1} contains a reading this firmware cannot execute")
            if (page.name.isBlank() || page.name.length > 32)
                add("Page ${index + 1} needs a name of at most 32 characters")
        }
        if (draft.warning !in -40..215 || draft.critical !in -40..215)
            add("Coolant limits must be between -40 and 215 °C")
        if (draft.warning >= draft.critical) add("Critical temperature must be above warning")
        if (draft.hysteresis !in 0..20 || draft.warning - draft.hysteresis < -40 ||
            draft.warning + draft.hysteresis >= draft.critical)
            add("Coolant hysteresis does not fit the selected limits")
        if (draft.triggerDwellMs !in 0..60000 || draft.clearDwellMs !in 0..60000)
            add("Alert timing is outside the supported range")
    }

    fun project(template: String, draft: Draft, profileId: String,
                baseRevision: Long): Pair<ConfigurationProjection, ByteArray> {
        val issues = blockers(draft)
        require(issues.isEmpty()) { issues.joinToString(". ") }
        val json = JSONObject(template)
        json.put("baseRevision", baseRevision)
        json.put("vehicleProfileId", profileId)
        val pages = JSONArray()
        val requiredDefinitions = draft.pages.flatMap { it.pidIds }
            .map { definitionIds.getValue(it) }.toMutableSet().apply { add("engine.coolant") }
        val availableDefinitions = json.getJSONArray("definitions")
        val definitions = JSONArray()
        for (index in 0 until availableDefinitions.length()) {
            val definition = availableDefinitions.getJSONObject(index)
            if (definition.getString("id") in requiredDefinitions) definitions.put(definition)
        }
        require(definitions.length() == requiredDefinitions.size) {
            "Configuration template is missing a required PID definition"
        }
        json.put("definitions", definitions)
        draft.pages.forEach { page ->
            pages.put(JSONObject()
                .put("id", page.id)
                .put("name", page.name)
                .put("renderer", page.layout.name.lowercase(Locale.ROOT))
                .put("pidIds", JSONArray(page.pidIds.map { definitionIds.getValue(it) })))
        }
        json.put("pages", pages)
        val alert = json.getJSONArray("alerts").getJSONObject(0)
        alert.put("warning", draft.warning).put("critical", draft.critical)
            .put("hysteresis", draft.hysteresis).put("triggerDwellMs", draft.triggerDwellMs)
            .put("clearDwellMs", draft.clearDwellMs)
        val projection = ConfigurationProjection(
            pages = draft.pages.map { page ->
                ProjectedPage(page.id, page.name, definitionIds.getValue(page.pidIds[0]),
                    page.layout.name.lowercase(Locale.ROOT),
                    page.pidIds.getOrNull(1)?.let { definitionIds.getValue(it) })
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
