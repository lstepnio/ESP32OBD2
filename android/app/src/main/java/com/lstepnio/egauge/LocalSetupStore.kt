package com.lstepnio.egauge

import android.content.Context

data class LocalSetup(val profiles: ProfileCollection, val associations: GaugeAssociations) {
    fun select(id: String): LocalSetup {
        require(profiles.profiles.any { it.id == id })
        val selected = profiles.copy(activeId = id)
        return copy(profiles = selected, associations = associations.selectedId?.let {
            associations.assign(it, id, selected.active.draft.source)
        } ?: associations)
    }

    fun create(profile: VehicleProfile): LocalSetup {
        require(profiles.profiles.size < 8 && profiles.profiles.none {
            it.id == profile.id || it.name.equals(profile.name, ignoreCase = true)
        })
        return copy(profiles = profiles.copy(profiles = profiles.profiles + profile)).select(profile.id)
    }

    fun attachTransmission(parentId: String): LocalSetup {
        val legacyId = profiles.activeId
        return copy(profiles = profiles.attachTransmission(legacyId, parentId),
            associations = associations.copy(gauges = associations.gauges.map {
                if (it.vehicleId == legacyId) it.copy(vehicleId = parentId, source = "TCM") else it
            }))
    }
}

/** Profile document and gauge assignments share one atomic SharedPreferences commit.
 * Legacy files stay read-only migration inputs. Schema 12 stops older Apps reading stale assignments.
 */
class LocalSetupStore(context: Context) {
    private val preferences = context.getSharedPreferences("profiles-v1", Context.MODE_PRIVATE)
    fun load(): LocalSetup {
        val profiles = ProfileStore(context).load()
        check(profiles.error == null) { profiles.error ?: "Saved profiles need attention" }
        return LocalSetup(profiles.collection, GaugeAssociationStore(context).load())
    }
    private val context = context.applicationContext
    fun save(value: LocalSetup): Boolean {
        val profiles = ProfileDocumentCodec.encode(value.profiles)
        val associations = GaugeAssociationDocumentCodec.encode(value.associations)
        // Validate the whole candidate before any preference editor mutates its in-memory map.
        ProfileDocumentCodec.decode(profiles)
        GaugeAssociationDocumentCodec.decode(associations)
        val previousProfiles = preferences.getString("collection", null)
        val previousAssociations = preferences.getString(ASSOCIATIONS, null)
        if (profiles == previousProfiles && associations == previousAssociations) return true
        val committed = preferences.edit().putString("collection", profiles)
            .putString(ASSOCIATIONS, associations).commit()
        if (!committed) {
            // Android updates its memory map even if disk commit fails. Restore the previous
            // map conservatively; the caller stays blocked until the saved setup is reviewed.
            preferences.edit().also { editor ->
                if (previousProfiles == null) editor.remove("collection") else editor.putString("collection", previousProfiles)
                if (previousAssociations == null) editor.remove(ASSOCIATIONS) else editor.putString(ASSOCIATIONS, previousAssociations)
            }.commit()
        }
        return committed
    }
    companion object { const val ASSOCIATIONS = "gauge-association-collection" }
}
