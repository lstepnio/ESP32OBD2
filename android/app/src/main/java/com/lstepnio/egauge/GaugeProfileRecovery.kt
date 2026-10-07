package com.lstepnio.egauge

import org.json.JSONObject

/** Recover a missing single-source profile from protected gauge readback, without guessing a parent. */
object GaugeProfileRecovery {
    fun recover(collection: ProfileCollection, document: GaugeConfigTransferClient.ActiveDocument): ProfileCollection {
        require(collection.profiles.size < 8) { "Remove an unused car before recovering this setup" }
        require(collection.profiles.none { it.id == document.vehicleProfileId }) { "This car is already saved" }
        require(document.vehicleProfileId.matches(Regex("[a-z][a-z0-9._-]{0,63}"))) { "Saved car identity is invalid" }
        val root = JSONObject(document.json)
        require(root.getString("vehicleProfileId") == document.vehicleProfileId)
        val sources = root.getJSONArray("sources")
        require(sources.length() == 1) { "Recover both-adapter setups through separate engine and transmission drafts" }
        val draft = requireNotNull(GaugeDraftComparison.savedDraft(document, document.vehicleProfileId)) {
            "Saved readings cannot be recovered safely by this app"
        }
        require(ConfigurationProjector.blockers(draft).isEmpty()) { "Saved readings are not supported" }
        val source = sources.getJSONObject(0)
        val binding = AdapterBinding.decode(source.getJSONObject("adapter"))
        val profile = VehicleProfile(document.vehicleProfileId,
            if (draft.source == "TCM") "Recovered transmission" else "Recovered car", draft, binding)
        val result = collection.copy(activeId = profile.id, profiles = collection.profiles + profile)
        // Exercise the same validation used for durable local storage before changing anything.
        return ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(result))
    }
}
