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

data class ProjectedAlert(
    val id: String,
    val pidId: String,
    val direction: AlertDirection,
    val warning: Int,
    val critical: Int,
    val hysteresis: Int,
    val triggerDwellMs: Int,
    val clearDwellMs: Int,
)

data class ConfigurationProjection(
    val pages: List<ProjectedPage>,
    val alerts: List<ProjectedAlert>,
    val actions: List<PageAction> = emptyList(),
) {
    fun reviewLines(): List<String> = pages.mapIndexed { index, page ->
        val values = listOfNotNull(page.pidId, page.secondaryPidId).joinToString(" + ")
        "${index + 1}. ${page.label} • ${page.renderer} • $values"
    } + alerts.map { alert ->
        "${alert.pidId}: warn ${alert.direction.name.lowercase()} ${alert.warning}; critical ${alert.direction.name.lowercase()} ${alert.critical}"
    } + actions.map { "Jump to ${pages.firstOrNull { page -> page.id == it.pageId }?.label ?: "Unavailable page"}: ${it.count} swipes up in ${it.windowMs / 1000}s" }
}

object ConfigurationProjector {
    val supportedPidIds = setOf("rpm", "coolant", "speed", "load", "fuel")
    val transmissionPidIds = setOf("tcmtemp", "tcmgear")
    fun pagePidIds(source: String) = when {
        source == "BOTH" && BuildConfig.DEBUG -> supportedPidIds + transmissionPidIds
        source == "TCM" && BuildConfig.DEBUG -> transmissionPidIds
        else -> supportedPidIds
    }
    private val definitionIds = mapOf(
        "tcmtemp" to "transmission.temperature.experimental",
        "tcmgear" to "transmission.gear",
        "rpm" to "engine.rpm",
        "coolant" to "engine.coolant",
        "speed" to "vehicle.speed",
        "load" to "engine.load",
        "fuel" to "vehicle.fuel",
    )

    fun blockers(draft: Draft): List<String> {
        val actionIssues = runCatching { ProfileActions.validate(draft.actions, draft.pages) }.exceptionOrNull()?.let { listOf(it.message ?: "Invalid actions") } ?: emptyList()
        if (draft.source == "BOTH") {
            val engine = draft.copy(source = "ECM", actions = emptyList(), pages = draft.pages.filter { it.pidIds.all { id -> id in supportedPidIds } })
            val transmission = draft.copy(source = "TCM", actions = emptyList(), pages = draft.pages.filter { it.pidIds.all { id -> id in transmissionPidIds } }, alerts = emptyList())
            return actionIssues + blockers(engine) + blockers(transmission) + buildList {
                if (draft.pages.size > 8) add("Choose up to eight pages across engine and transmission")
                if (engine.pages.size + transmission.pages.size != draft.pages.size) add("Each page must use one adapter source")
                if (draft.pages.map { it.id }.distinct().size != draft.pages.size || draft.pages.any { it.id.length > 64 })
                    add("Every page needs a unique identity of at most 64 characters")
            }
        }
        return actionIssues + buildList {
            if (draft.source == "TCM") {
                if (!BuildConfig.DEBUG || draft.alerts.isNotEmpty())
                    add("The TCM setup supports temperature and gear pages without alerts")
            } else if (draft.source != "ECM" || draft.pages.any { page -> page.pidIds.any { it in transmissionPidIds } } || draft.alerts.any { it.pidId in transmissionPidIds })
                add("Engine and transmission readings need separate adapter profiles")
            if (draft.pages.size !in 1..8) add("Choose between one and eight gauge pages")
            if (draft.pages.map { it.id }.distinct().size != draft.pages.size)
                add("Every page needs a unique identity")
            draft.pages.forEachIndexed { index, page ->
                val expected = if (page.layout == GaugeLayout.Dual) 2 else 1
                if (page.pidIds.size != expected || page.pidIds.distinct().size != page.pidIds.size)
                    add("Page ${index + 1} needs $expected distinct reading${if (expected == 1) "" else "s"}")
                if (page.pidIds.any { it !in pagePidIds(draft.source) })
                    add("Page ${index + 1} contains a reading this firmware cannot execute")
                if ("tcmgear" in page.pidIds && page.layout !in setOf(GaugeLayout.Numeric, GaugeLayout.Dual))
                    add("Gear uses Numeric or Dual layout")
                if (page.name.isBlank() || page.name.length > 32)
                    add("Page ${index + 1} needs a name of at most 32 characters")
            }
            if (draft.alerts.size > 32) add("Choose up to 32 alerts")
            if (draft.alerts.map { it.id }.distinct().size != draft.alerts.size) add("Every alert needs a unique identity")
            if (draft.alerts.map { it.pidId }.distinct().size != draft.alerts.size) add("Choose each reading only once for alerts")
            draft.alerts.forEach { alert ->
                val range = readingRange(alert.pidId)
                if (alert.pidId !in supportedPidIds) add("An alert uses a reading this firmware cannot execute")
                if (alert.warning !in range || alert.critical !in range) add("${alert.pidId} alert limits are outside its supported range")
                val ordered = if (alert.direction == AlertDirection.Above) alert.warning < alert.critical else alert.warning > alert.critical
                if (!ordered) add("${alert.pidId} critical limit must be beyond its warning limit")
                if (alert.hysteresis !in 0..20 || alert.hysteresis >= range.last - range.first)
                    add("${alert.pidId} alert reset margin is outside the supported range")
                val marginFits = if (alert.direction == AlertDirection.Above)
                    alert.warning + alert.hysteresis < alert.critical
                else alert.warning - alert.hysteresis > alert.critical
                if (!marginFits) add("${alert.pidId} alert reset margin needs more space between warning and critical")
                if (alert.triggerDwellMs !in 0..60000 || alert.clearDwellMs !in 0..60000)
                    add("${alert.pidId} alert timing is outside the supported range")
            }
        }
    }

    fun project(template: String, draft: Draft, profileId: String,
                baseRevision: Long, adapter: AdapterBinding? = null, schemaVersion: Int = 1): Pair<ConfigurationProjection, ByteArray> {
        val issues = blockers(draft)
        require(issues.isEmpty()) { issues.joinToString(". ") }
        val json = JSONObject(template)
        require(schemaVersion in 1..2 && (adapter == null || schemaVersion == 2))
        require(draft.actions.isEmpty() || schemaVersion == 2) { "Gesture actions require updated gauge configuration" }
        json.remove("actions")
        if (draft.actions.isNotEmpty()) json.put("actions", ProfileActions.json(draft.actions))
        json.put("schemaVersion", schemaVersion)
        val source = json.getJSONArray("sources").getJSONObject(0)
        if (draft.source == "TCM") {
            require(schemaVersion == 2 && adapter != null) { "Select the TCM adapter before sending its experimental setup" }
            source.put("id", "tcm").put("role", "tcm").put("label", "Transmission adapter")
        }
        if (adapter != null) source.put("adapter", adapter.json()) else source.remove("adapter")
        json.put("baseRevision", baseRevision)
        json.put("vehicleProfileId", profileId)
        val pages = JSONArray()
        val requiredDefinitions = (draft.pages.flatMap { it.pidIds } + draft.alerts.map { it.pidId })
            .map { definitionIds.getValue(it) }.toMutableSet()
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
        json.put("alerts", JSONArray().also { alerts ->
            draft.alerts.forEach { alert ->
                alerts.put(JSONObject()
                    .put("id", alert.id)
                    .put("pidId", definitionIds.getValue(alert.pidId))
                    .put("direction", alert.direction.name.lowercase(Locale.ROOT))
                    .put("warning", alert.warning)
                    .put("critical", alert.critical)
                    .put("hysteresis", alert.hysteresis)
                    .put("triggerDwellMs", alert.triggerDwellMs)
                    .put("clearDwellMs", alert.clearDwellMs)
                    .put("snoozeMs", 0)
                    .put("priority", alert.priority))
            }
        })
        val projection = ConfigurationProjection(
            actions = draft.actions,
            pages = draft.pages.map { page ->
                ProjectedPage(page.id, page.name, definitionIds.getValue(page.pidIds[0]),
                    page.layout.name.lowercase(Locale.ROOT),
                    page.pidIds.getOrNull(1)?.let { definitionIds.getValue(it) })
            },
            alerts = draft.alerts.map { alert -> ProjectedAlert(alert.id, definitionIds.getValue(alert.pidId),
                alert.direction, alert.warning, alert.critical, alert.hysteresis, alert.triggerDwellMs, alert.clearDwellMs) },
        )
        return projection to json.toString().toByteArray(Charsets.UTF_8)
    }
    fun projectCombined(template: String, vehicle: VehicleProfile, baseRevision: Long): Pair<ConfigurationProjection, ByteArray> {
        val combined = vehicle.combinedDraft()
        require(blockers(combined).isEmpty()) { blockers(combined).joinToString(". ") }
        val engine = project(template, vehicle.draft, vehicle.id, baseRevision, vehicle.primaryAdapter, 2)
        val child = requireNotNull(vehicle.transmission)
        val tcmDraft = child.draft.copy(pages = child.draft.pages.map { it.copy(id = "child.${it.id}") },
            actions = child.draft.actions.map { it.copy(pageId = "child.${it.pageId}") })
        val transmission = project(template, tcmDraft, vehicle.id, baseRevision, child.adapter, 2)
        val root = JSONObject(engine.second.toString(Charsets.UTF_8))
        val tcm = JSONObject(transmission.second.toString(Charsets.UTF_8))
        listOf("sources", "definitions", "pages").forEach { key ->
            val target = root.getJSONArray(key)
            val extra = tcm.getJSONArray(key)
            for (index in 0 until extra.length()) target.put(extra.getJSONObject(index))
        }
        val mergedPages = root.getJSONArray("pages")
        val byId = (0 until mergedPages.length()).associate { index ->
            mergedPages.getJSONObject(index).let { it.getString("id") to it }
        }
        root.put("pages", JSONArray(combined.pages.map { byId.getValue(it.id) }))
        val projectedById = (engine.first.pages + transmission.first.pages).associateBy { it.id }
        if (combined.actions.isNotEmpty()) root.put("actions", ProfileActions.json(combined.actions))
        return ConfigurationProjection(combined.pages.map { projectedById.getValue(it.id) }, engine.first.alerts, combined.actions) to
            root.toString().toByteArray(Charsets.UTF_8)
    }

}
