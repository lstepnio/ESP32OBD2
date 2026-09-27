package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject

object ProfileDocumentCodec {
    fun decode(raw: String): ProfileCollection {
        val root = JSONObject(raw)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion in 1..2) { "Profile format is newer than this app" }
        val items = root.getJSONArray("profiles")
        require(items.length() in 1..8) { "Profile count is invalid" }
        val profiles = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = item.getString("id")
            val name = item.getString("name").trim()
            require(id.isNotBlank() && name.length in 1..32) { "Profile identity is invalid" }
            val saved = item.getJSONObject("draft")
            val draft = Draft(
                pidId = saved.getString("pidId").takeIf { pid -> demoCatalog.any { it.id == pid } }
                    ?: error("Profile reading is not supported by this app"),
                layout = GaugeLayout.entries.firstOrNull { it.name == saved.getString("layout") }
                    ?: error("Profile layout is not supported by this app"),
                warning = saved.getInt("warning"),
                critical = saved.getInt("critical"),
                hysteresis = saved.optInt("hysteresis", 3),
                triggerDwellMs = saved.optInt("triggerDwellMs", 1000),
                clearDwellMs = saved.optInt("clearDwellMs", 2000),
                source = saved.getString("source"),
            )
            require(draft.source == "ECM" || draft.source == "TCM") { "Profile source is invalid" }
            require(draft.warning in -40..250 && draft.critical in -40..250 &&
                draft.hysteresis in 0..20 && draft.triggerDwellMs in 0..60000 &&
                draft.clearDwellMs in 0..60000) { "Profile alert settings are invalid" }
            VehicleProfile(
                id,
                name,
                draft,
                secondAdapterEnabled = if (schemaVersion >= 2)
                    item.optBoolean("secondAdapterEnabled", false) else draft.source == "TCM",
            )
        }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Profile IDs are duplicated" }
        val activeId = root.getString("activeId")
        require(profiles.any { it.id == activeId }) { "Active profile is missing" }
        return ProfileCollection(activeId, profiles)
    }

    fun encode(value: ProfileCollection): String {
        require(value.profiles.size in 1..8 && value.profiles.any { it.id == value.activeId })
        val root = JSONObject().put("schemaVersion", 2).put("activeId", value.activeId)
        val items = JSONArray()
        value.profiles.forEach { profile ->
            items.put(JSONObject().put("id", profile.id).put("name", profile.name)
                .put("secondAdapterEnabled", profile.secondAdapterEnabled)
                .put("draft", JSONObject()
                    .put("pidId", profile.draft.pidId)
                    .put("layout", profile.draft.layout.name)
                    .put("warning", profile.draft.warning)
                    .put("critical", profile.draft.critical)
                    .put("hysteresis", profile.draft.hysteresis)
                    .put("triggerDwellMs", profile.draft.triggerDwellMs)
                    .put("clearDwellMs", profile.draft.clearDwellMs)
                    .put("source", profile.draft.source)))
        }
        return root.put("profiles", items).toString()
    }
}

