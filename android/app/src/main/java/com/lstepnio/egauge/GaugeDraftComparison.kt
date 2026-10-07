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
        private fun localPid(id: String): String? = when (id) {
            "transmission.temperature.experimental" -> "tcmtemp"
            "transmission.gear" -> "tcmgear"
            "engine.rpm" -> "rpm"
            "engine.coolant" -> "coolant"
            "vehicle.speed" -> "speed"
            "engine.load" -> "load"
            "vehicle.fuel" -> "fuel"
            else -> null
        }

        fun savedDraft(document: GaugeConfigTransferClient.ActiveDocument,
                       profileId: String): Draft? = runCatching {
            require(document.vehicleProfileId == profileId)
            val saved = JSONObject(document.json)
            val sourceItems = saved.getJSONArray("sources")
            require(sourceItems.length() in 1..2)
            val roles = (0 until sourceItems.length()).map { sourceItems.getJSONObject(it).getString("role") }
            require(roles.distinct().size == roles.size && roles.all { it in setOf("ecm", "tcm") })
            if (roles.size == 2) require(roles == listOf("ecm", "tcm"))
            val savedPages = saved.getJSONArray("pages")
            val primary = savedPages.getJSONObject(0)
            val pidId = localPid(primary.getJSONArray("pidIds").getString(0))
                ?: error("Saved primary reading is not available in the phone editor")
            val layout = GaugeLayout.entries.firstOrNull {
                it.name.equals(primary.getString("renderer"), ignoreCase = true)
            } ?: error("Saved renderer is not available in the phone editor")
            val definitions = saved.getJSONArray("definitions")
            val definition = (0 until definitions.length()).map { definitions.getJSONObject(it) }
                .first { it.getString("id") == primary.getJSONArray("pidIds").getString(0) }
            val sourceId = definition.getString("sourceId")
            val source = (0 until saved.getJSONArray("sources").length()).map { saved.getJSONArray("sources").getJSONObject(it) }
                .first { it.getString("id") == sourceId }.getString("role").uppercase(Locale.ROOT)
            require(source == "ECM" || source == "TCM")
            val pages = (0 until savedPages.length()).map { index ->
                val page = savedPages.getJSONObject(index)
                val pageLayout = GaugeLayout.entries.firstOrNull {
                    it.name.equals(page.getString("renderer"), ignoreCase = true)
                } ?: error("Saved renderer is not available in the phone editor")
                val ids = page.getJSONArray("pidIds")
                GaugePageDraft(page.getString("id"), page.getString("name"), pageLayout,
                    (0 until ids.length()).map { localPid(ids.getString(it)) ?: error("Saved reading is not available") })
            }
            val values = saved.getJSONArray("alerts")
            val alerts = (0 until values.length()).map { index ->
                val alert = values.getJSONObject(index)
                val alertPid = localPid(alert.getString("pidId")) ?: error("Saved alert reading is not available")
                GaugeAlertDraft(alert.optString("id").ifBlank { "alert.$alertPid" }, alertPid,
                    if (alert.optString("direction", "above").equals("below", true)) AlertDirection.Below else AlertDirection.Above,
                    alert.getInt("warning"), alert.getInt("critical"), alert.getInt("hysteresis"),
                    alert.getInt("triggerDwellMs"), alert.getInt("clearDwellMs"), alert.optInt("priority", 8))
            }
            Draft(pidId = pidId, layout = layout, source = if (saved.getJSONArray("sources").length() == 2) "ECM" else source, pages = pages, alerts = alerts, actions = ProfileActions.decode(saved.optJSONArray("actions"))).also { ProfileActions.validate(it.actions, it.pages) }
        }.getOrNull()

        fun from(document: GaugeConfigTransferClient.ActiveDocument,
                 profileId: String, draft: Draft, adapter: AdapterBinding? = null): GaugeDraftComparison {
            val saved = JSONObject(document.json)
            val pages = saved.optJSONArray("pages")
            val primaryPid = pages?.optJSONObject(0)?.optJSONArray("pidIds")?.optString(0)?.takeIf { it.isNotBlank() }
            fun field(label: String, phone: String, gauge: String?): GaugeDraftField =
                GaugeDraftField(label, phone, gauge, gauge?.let { it == phone })
            fun pageSettings(local: List<GaugePageDraft>): String = local.mapIndexed { index, page ->
                "${index + 1}. ${page.name} / ${page.layout.label} / ${page.pidIds.joinToString(" + ")}" }.joinToString("\n")
            fun savedPageSettings(): String? = pages?.let { value ->
                (0 until value.length()).map { index ->
                    val page = value.optJSONObject(index) ?: return@let null
                    val renderer = GaugeLayout.entries.firstOrNull { it.name.equals(page.optString("renderer"), true) }?.label ?: return@let null
                    val ids = page.optJSONArray("pidIds") ?: return@let null
                    val mapped = (0 until ids.length()).map { localPid(ids.optString(it)) ?: return@let null }
                    "${index + 1}. ${page.optString("name")} / $renderer / ${mapped.joinToString(" + ")}" }.joinToString("\n")
            }
            fun alertSettings(local: List<GaugeAlertDraft>): String = local.joinToString("\n") { alert ->
                "${alert.id} / ${alert.pidId} / ${alert.direction.name.lowercase()} / ${alert.warning} / ${alert.critical} / ${alert.hysteresis} / ${alert.triggerDwellMs} / ${alert.clearDwellMs}" }
            fun savedAlertSettings(): String? = saved.optJSONArray("alerts")?.let { values ->
                (0 until values.length()).map { index ->
                    val alert = values.optJSONObject(index) ?: return@let null
                    val pid = localPid(alert.optString("pidId")) ?: return@let null
                    "${alert.optString("id").ifBlank { "alert.$pid" }} / $pid / ${alert.optString("direction")} / ${alert.optInt("warning")} / ${alert.optInt("critical")} / ${alert.optInt("hysteresis")} / ${alert.optInt("triggerDwellMs")} / ${alert.optInt("clearDwellMs")}" }.joinToString("\n")
            }
            return GaugeDraftComparison(document.revision, listOf(
                field("Vehicle profile ID", profileId, document.vehicleProfileId),
                field("Vehicle adapter", adapter?.id ?: "None",
                    saved.optJSONArray("sources")?.optJSONObject(0)?.optJSONObject("adapter")?.optString("id") ?: "None"),
                field("Page count", draft.pages.size.toString(), pages?.length()?.toString()),
                field("All page settings", pageSettings(draft.pages), savedPageSettings()),
                field("Primary reading", draft.pages.firstOrNull()?.pidIds?.firstOrNull() ?: "None", primaryPid?.let(::localPid)),
                field("Gesture actions", ProfileActions.summary(draft.actions),
                    runCatching { ProfileActions.summary(ProfileActions.decode(saved.optJSONArray("actions"))) }.getOrNull()),
                field("Alert count", draft.alerts.size.toString(), saved.optJSONArray("alerts")?.length()?.toString()),
                field("All alert settings", alertSettings(draft.alerts), savedAlertSettings()),
            ))
        }
    }
}
