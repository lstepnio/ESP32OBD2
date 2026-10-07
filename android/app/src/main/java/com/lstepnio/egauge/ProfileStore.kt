package com.lstepnio.egauge

import android.content.Context
import java.util.UUID

/** Local drafts and adapter selections. Vehicle evidence is stored separately. */
data class VehicleProfile(
    val id: String,
    val name: String,
    val draft: Draft,
    val primaryAdapter: AdapterBinding? = null,
)

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
            ProfileLoad(ProfileDocumentCodec.decode(raw))
        }.getOrElse { error ->
            ProfileLoad(defaultCollection(), error.message ?: "Profile data could not be read")
        }
    }

    fun save(value: ProfileCollection): Boolean {
        require(value.profiles.size in 1..8 && value.profiles.any { it.id == value.activeId })
        return preferences.edit().putString("collection", ProfileDocumentCodec.encode(value)).commit()
    }

    private fun migrateLegacy(): ProfileLoad {
        val layout = runCatching { GaugeLayout.valueOf(legacy.getString("layout", "Arc") ?: "Arc") }
            .getOrDefault(GaugeLayout.Arc)
        val draft = Draft(
            pidId = legacy.getString("pid", "rpm")?.takeIf { id -> demoCatalog.any { it.id == id } } ?: "rpm",
            layout = layout,
            source = legacy.getString("source", "ECM")?.takeIf { it == "ECM" || it == "TCM" } ?: "ECM",
            alerts = listOf(GaugeAlertDraft("alert.coolant", "coolant", AlertDirection.Above,
                legacy.getInt("warning", 105).coerceIn(readingRange("coolant")),
                legacy.getInt("critical", 115).coerceIn(readingRange("coolant")))),
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
