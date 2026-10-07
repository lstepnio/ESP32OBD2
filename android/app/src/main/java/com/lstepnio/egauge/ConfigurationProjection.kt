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
    val warning: Double,
    val critical: Double,
    val hysteresis: Double,
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
    val supportedPidIds = readingCatalog.filter { it.source == "ECM" }.map { it.id }.toSet()
    val transmissionPidIds = readingCatalog.filter { it.source == "TCM" }.map { it.id }.toSet()
    fun pagePidIds(source: String) = when {
        source in setOf("BOTH", "ECM") && BuildConfig.DEBUG -> supportedPidIds + transmissionPidIds
        source == "TCM" && BuildConfig.DEBUG -> transmissionPidIds
        else -> supportedPidIds
    }
    private val definitionIds = readingCatalog.associate { it.id to it.definitionId }

    fun blockers(draft: Draft, allowEmptyPages: Boolean = false): List<String> {
        val actionIssues = runCatching { ProfileActions.validate(draft.actions, draft.pages) }.exceptionOrNull()?.let { listOf(it.message ?: "Invalid actions") } ?: emptyList()
        return actionIssues + buildList {
            if (draft.source == "TCM") {
                if (!BuildConfig.DEBUG)
                    add("The TCM setup requires a development build")
            } else if (draft.source !in setOf("ECM", "BOTH"))
                add("Vehicle source is invalid")
            if (draft.pages.size !in (if (allowEmptyPages) 0..8 else 1..8)) add("Add a page for the selected adapter before sending")
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
            if ((draft.pages.flatMap { it.pidIds } + draft.alerts.map { it.pidId }).distinct().size > 32)
                add("Use up to 32 different readings across pages and alerts")
            if (draft.alerts.size > 32) add("Choose up to 32 alerts")
            if (draft.alerts.map { it.id }.distinct().size != draft.alerts.size) add("Every alert needs a unique identity")
            if (draft.alerts.map { it.pidId }.distinct().size != draft.alerts.size) add("Choose each reading only once for alerts")
            draft.alerts.forEach { alert ->
                val range = readingCatalog.firstOrNull { it.id == alert.pidId }?.let { it.minimum..it.maximum } ?: 0.0..100.0
                if (alert.pidId !in pagePidIds(draft.source)) add("An alert uses a reading this firmware cannot execute")
                if (alert.warning !in range || alert.critical !in range) add("${alert.pidId} alert limits are outside its supported range")
                val gear = readingCatalog.firstOrNull { it.id == alert.pidId }?.alertKind == "gear"
                if (gear || alert.direction == AlertDirection.Equals) {
                    if (!gear || alert.direction != AlertDirection.Equals ||
                        alert.warning !in gearPositions || alert.critical !in gearPositions || alert.hysteresis != 0.0)
                        add("${alert.pidId} alert needs valid gear positions and no reset margin")
                } else {
                    val ordered = if (alert.direction == AlertDirection.Above) alert.warning < alert.critical else alert.warning > alert.critical
                    if (!ordered) add("${alert.pidId} critical limit must be beyond its warning limit")
                    if (!alert.hysteresis.isFinite() || alert.hysteresis < 0 || alert.hysteresis >= range.endInclusive - range.start)
                        add("${alert.pidId} alert reset margin is outside the supported range")
                    val marginFits = if (alert.direction == AlertDirection.Above)
                        alert.warning + alert.hysteresis < alert.critical && alert.warning - alert.hysteresis >= range.start
                    else alert.warning - alert.hysteresis > alert.critical && alert.warning + alert.hysteresis <= range.endInclusive
                    if (!marginFits) add("${alert.pidId} alert reset margin needs more space between warning and critical")
                }
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
            if (definition.getString("id") in requiredDefinitions) {
                definition.put("sourceId", source.getString("id"))
                definitions.put(definition)
            }
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
    /** All vehicle pages are sent together; only transport routing changes in Expert. */
    fun projectVehicle(template: String, vehicle: VehicleProfile, baseRevision: Long,
                       useChildAdapter: Boolean, schemaVersion: Int = 2): Pair<ConfigurationProjection, ByteArray> {
        val draft = vehicle.dashboardDraft().copy(source = "ECM")
        val result = project(template, draft, vehicle.id, baseRevision, vehicle.primaryAdapter, schemaVersion)
        if (!useChildAdapter) return result
        vehicle.combinedDraft() // Distinct bindings, never two workers claiming the same radio.
        val child = requireNotNull(vehicle.transmission)
        val root = JSONObject(result.second.toString(Charsets.UTF_8))
        root.getJSONArray("sources").put(JSONObject().put("id", "tcm").put("role", "tcm")
            .put("label", "Transmission adapter").put("adapter", requireNotNull(child.adapter).json()))
        val definitions = root.getJSONArray("definitions")
        for (index in 0 until definitions.length()) {
            val definition = definitions.getJSONObject(index)
            if (definition.getJSONObject("request").getString("service") == "22") definition.put("sourceId", "tcm")
        }
        return result.first to root.toString().toByteArray(Charsets.UTF_8)
    }

    fun projectCombined(template: String, vehicle: VehicleProfile, baseRevision: Long): Pair<ConfigurationProjection, ByteArray> =
        projectVehicle(template, vehicle, baseRevision, true)
}
