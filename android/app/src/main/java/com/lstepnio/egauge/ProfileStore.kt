package com.lstepnio.egauge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local design drafts only. No adapter identity or vehicle capability claim is stored here. */
data class VehicleProfile(val id: String, val name: String, val draft: Draft)

data class ProfileCollection(val activeId: String, val profiles: List<VehicleProfile>) {
    val active: VehicleProfile get() = profiles.first { it.id == activeId }
}

data class ProfileLoad(val collection: ProfileCollection, val error: String? = null)

class ProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("profiles-v1", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("draft-v1", Context.MODE_PRIVATE)

    fun load(): ProfileLoad {
        val raw = preferences.getString("collection", null) ?: return migrateLegacy()
        return runCatching {
            val root = JSONObject(raw)
            require(root.getInt("schemaVersion") == 1) { "Profile format is newer than this app" }
            val items = root.getJSONArray("profiles")
            require(items.length() in 1..8) { "Profile count is invalid" }
            val profiles = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val id = item.getString("id")
                val name = item.getString("name").trim()
                require(id.isNotBlank() && name.length in 1..32) { "Profile identity is invalid" }
                val saved = item.getJSONObject("draft")
                VehicleProfile(id, name, Draft(
                    pidId = saved.getString("pidId").takeIf { pid -> demoCatalog.any { it.id == pid } } ?: "rpm",
                    layout = runCatching { GaugeLayout.valueOf(saved.getString("layout")) }
                        .getOrDefault(GaugeLayout.Arc),
                    warning = saved.getInt("warning").coerceIn(-40, 250),
                    critical = saved.getInt("critical").coerceIn(-40, 250),
                    hysteresis = saved.optInt("hysteresis", 3).coerceIn(0, 20),
                    triggerDwellMs = saved.optInt("triggerDwellMs", 1000).coerceIn(0, 60000),
                    clearDwellMs = saved.optInt("clearDwellMs", 2000).coerceIn(0, 60000),
                    source = saved.getString("source").takeIf { it == "ECM" || it == "TCM" } ?: "ECM",
                ))
            }
            require(profiles.map { it.id }.distinct().size == profiles.size) { "Profile IDs are duplicated" }
            val activeId = root.getString("activeId")
            require(profiles.any { it.id == activeId }) { "Active profile is missing" }
            ProfileLoad(ProfileCollection(activeId, profiles))
        }.getOrElse { error ->
            ProfileLoad(defaultCollection(), error.message ?: "Profile data could not be read")
        }
    }

    fun save(value: ProfileCollection) {
        require(value.profiles.size in 1..8 && value.profiles.any { it.id == value.activeId })
        val root = JSONObject().put("schemaVersion", 1).put("activeId", value.activeId)
        val items = JSONArray()
        value.profiles.forEach { profile ->
            items.put(JSONObject().put("id", profile.id).put("name", profile.name)
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
        preferences.edit().putString("collection", root.put("profiles", items).toString()).apply()
    }

    private fun migrateLegacy(): ProfileLoad {
        val layout = runCatching { GaugeLayout.valueOf(legacy.getString("layout", "Arc") ?: "Arc") }
            .getOrDefault(GaugeLayout.Arc)
        val draft = Draft(
            pidId = legacy.getString("pid", "rpm")?.takeIf { id -> demoCatalog.any { it.id == id } } ?: "rpm",
            layout = layout,
            warning = legacy.getInt("warning", 105).coerceIn(-40, 250),
            critical = legacy.getInt("critical", 115).coerceIn(-40, 250),
            source = legacy.getString("source", "ECM")?.takeIf { it == "ECM" || it == "TCM" } ?: "ECM",
        )
        val collection = ProfileCollection("default", listOf(VehicleProfile("default", "My vehicle", draft)))
        save(collection)
        return ProfileLoad(collection)
    }

    companion object {
        // Compatible with the future device configuration ID pattern.
        fun newId(): String = "vehicle-${UUID.randomUUID()}"
        private fun defaultCollection() = ProfileCollection(
            "default", listOf(VehicleProfile("default", "My vehicle", Draft())),
        )
    }
}
