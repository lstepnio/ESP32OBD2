package com.lstepnio.egauge

import android.content.Context
import java.util.UUID

/** Local drafts and adapter selections. Vehicle evidence is stored separately. */
data class VehicleProfile(
    val id: String,
    val name: String,
    val draft: Draft,
    val primaryAdapter: AdapterBinding? = null,
    val transmission: TransmissionConnection? = null,
    val pageOrder: List<String> = emptyList(),
) {
    init {
        require(draft.pages.isNotEmpty() || transmission?.draft?.pages?.isNotEmpty() == true) { "Keep at least one vehicle page" }
        require(pageOrder.size <= 8 && pageOrder.distinct().size == pageOrder.size &&
            pageOrder.all { it.matches(Regex("[a-z][a-z0-9._-]{0,63}")) }) { "Page order is invalid" }
        require(transmission == null || (draft.source == "ECM" && primaryAdapter != null)) {
            "A transmission child needs a primary engine adapter"
        }
        require(transmission?.adapter == null || primaryAdapter == transmission.adapter || !samePhysicalAdapter(primaryAdapter, transmission.adapter)) {
            "The same adapter must keep the same identity for both connections"
        }
    }

    fun draftFor(source: String): Draft? = when (source) {
        draft.source -> draft
        "TCM" -> transmission?.draft
        else -> null
    }
    fun adapterFor(source: String): AdapterBinding? = if (source == draft.source) primaryAdapter
        else if (source == "TCM") transmission?.adapter else if (source == "ECM") primaryAdapter else null
    fun withDraft(value: Draft): VehicleProfile = if (value.source == draft.source) copy(draft = value)
        else copy(transmission = (transmission ?: error("Set up the transmission child first")).copy(draft = value))
    fun withAdapter(source: String, value: AdapterBinding?): VehicleProfile = when (source) {
        draft.source -> copy(primaryAdapter = value)
        "TCM" -> copy(transmission = (transmission ?: error("Set up the transmission child first")).copy(adapter = value))
        else -> error("Vehicle source is invalid")
    }
}

data class ProfileCollection(val activeId: String, val profiles: List<VehicleProfile>) {
    val active: VehicleProfile get() = profiles.first { it.id == activeId }
    fun withoutVehicle(id: String): ProfileCollection {
        require(profiles.any { it.id == id }) { "Vehicle no longer exists" }
        require(profiles.size > 1) { "Keep at least one vehicle" }
        val remaining = profiles.filterNot { it.id == id }
        return copy(activeId = if (activeId == id) remaining.first().id else activeId, profiles = remaining)
    }
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
