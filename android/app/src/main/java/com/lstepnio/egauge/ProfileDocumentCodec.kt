package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject

object ProfileDocumentCodec {
    fun decode(raw: String): ProfileCollection {
        val root = JSONObject(raw)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion in 1..4) { "Profile format is newer than this app" }
        val items = root.getJSONArray("profiles")
        require(items.length() in 1..8) { "Profile count is invalid" }
        val profiles = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = item.getString("id")
            val name = item.getString("name").trim()
            require(id.isNotBlank() && name.length in 1..32) { "Profile identity is invalid" }
            val saved = item.getJSONObject("draft")
            val pidId = saved.getString("pidId").takeIf { pid -> demoCatalog.any { it.id == pid } }
                    ?: error("Profile reading is not supported by this app")
            val layout = GaugeLayout.entries.firstOrNull { it.name == saved.getString("layout") }
                    ?: error("Profile layout is not supported by this app")
            val pages = if (schemaVersion >= 3) {
                val pageItems = saved.getJSONArray("pages")
                require(pageItems.length() in 1..8) { "Profile page count is invalid" }
                (0 until pageItems.length()).map { pageIndex ->
                    val page = pageItems.getJSONObject(pageIndex)
                    val pageLayout = GaugeLayout.entries.firstOrNull { it.name == page.getString("layout") }
                        ?: error("Profile page layout is not supported")
                    val ids = page.getJSONArray("pidIds")
                    require(ids.length() == if (pageLayout == GaugeLayout.Dual) 2 else 1)
                    val pagePids = (0 until ids.length()).map { ids.getString(it) }
                    require(pagePids.distinct().size == pagePids.size &&
                        pagePids.all { value -> demoCatalog.any { it.id == value } })
                    val pageId = page.getString("id")
                    val pageName = page.getString("name")
                    require(pageId.matches(Regex("[a-z][a-z0-9._-]{0,63}")) &&
                        pageName.length in 1..32) { "Profile page identity is invalid" }
                    GaugePageDraft(pageId, pageName, pageLayout, pagePids)
                }
            } else defaultGaugePages(pidId, layout)
            require(pages.map { it.id }.distinct().size == pages.size) { "Profile page IDs are duplicated" }
            val alerts = if (schemaVersion >= 4) {
                val values = saved.getJSONArray("alerts")
                require(values.length() <= 32) { "Profile alert count is invalid" }
                (0 until values.length()).map { alertIndex ->
                    val alert = values.getJSONObject(alertIndex)
                    val alertId = alert.getString("id")
                    val alertPid = alert.getString("pidId")
                    require(alertId.matches(Regex("[a-z][a-z0-9._-]{0,63}")) &&
                        demoCatalog.any { it.id == alertPid }) { "Profile alert identity is invalid" }
                    GaugeAlertDraft(alertId, alertPid,
                        AlertDirection.valueOf(alert.optString("direction", "above").replaceFirstChar(Char::uppercase)),
                        alert.getInt("warning"), alert.getInt("critical"), alert.optInt("hysteresis", 3),
                        alert.optInt("triggerDwellMs", 1000), alert.optInt("clearDwellMs", 2000), alert.optInt("priority", 8))
                }
            } else listOf(GaugeAlertDraft("alert.coolant", "coolant", AlertDirection.Above,
                saved.getInt("warning"), saved.getInt("critical"), saved.optInt("hysteresis", 3),
                saved.optInt("triggerDwellMs", 1000), saved.optInt("clearDwellMs", 2000)))
            val draft = Draft(
                pidId = pidId,
                layout = layout,
                source = saved.getString("source"),
                pages = pages,
                alerts = alerts,
            )
            require(draft.source == "ECM" || draft.source == "TCM") { "Profile source is invalid" }
            require(draft.alerts.map { it.id }.distinct().size == draft.alerts.size &&
                draft.alerts.all { alert ->
                    // Older apps accepted 16384. Keep those profiles readable so the editor can fix the limit.
                    val storedRange = if (alert.pidId == "rpm") 0..16384 else readingRange(alert.pidId)
                    alert.warning in storedRange && alert.critical in storedRange && alert.hysteresis in 0..20 &&
                    alert.triggerDwellMs in 0..60000 && alert.clearDwellMs in 0..60000 }) {
                "Profile alert settings are invalid"
            }
            VehicleProfile(
                id,
                name,
                draft,
            )
        }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Profile IDs are duplicated" }
        val activeId = root.getString("activeId")
        require(profiles.any { it.id == activeId }) { "Active profile is missing" }
        return ProfileCollection(activeId, profiles)
    }

    fun encode(value: ProfileCollection): String {
        require(value.profiles.size in 1..8 && value.profiles.any { it.id == value.activeId })
        val root = JSONObject().put("schemaVersion", 4).put("activeId", value.activeId)
        val items = JSONArray()
        value.profiles.forEach { profile ->
            items.put(JSONObject().put("id", profile.id).put("name", profile.name)
                .put("draft", JSONObject()
                    .put("pidId", profile.draft.pidId)
                    .put("layout", profile.draft.layout.name)
                    .put("source", profile.draft.source)
                    .put("pages", JSONArray().also { pages ->
                        profile.draft.pages.forEach { page ->
                            pages.put(JSONObject().put("id", page.id).put("name", page.name)
                                .put("layout", page.layout.name)
                                .put("pidIds", JSONArray(page.pidIds)))
                        }
                    })
                    .put("alerts", JSONArray().also { alerts ->
                        profile.draft.alerts.forEach { alert ->
                            alerts.put(JSONObject().put("id", alert.id).put("pidId", alert.pidId)
                                .put("direction", alert.direction.name.lowercase())
                                .put("warning", alert.warning).put("critical", alert.critical)
                                .put("hysteresis", alert.hysteresis).put("triggerDwellMs", alert.triggerDwellMs)
                                .put("clearDwellMs", alert.clearDwellMs).put("priority", alert.priority))
                        }
                    })))
        }
        return root.put("profiles", items).toString()
    }
}
