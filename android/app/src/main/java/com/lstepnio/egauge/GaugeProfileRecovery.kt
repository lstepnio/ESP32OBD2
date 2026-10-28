package com.lstepnio.egauge

import org.json.JSONObject

/** Recover a missing vehicle profile from protected gauge readback, without guessing a parent. */
object GaugeProfileRecovery {
    fun recover(
        collection: ProfileCollection,
        document: GaugeConfigTransferClient.ActiveDocument
    ): ProfileCollection {
        require(collection.profiles.size < 8) {
            "Remove an unused car before recovering this setup"
        }
        require(
            collection.profiles.none {
                it.id == document.vehicleProfileId
            }
        ) { "This car is already saved" }
        val profile = restoredProfile(document)
        val result = collection.copy(
            activeId = profile.id,
            profiles = collection.profiles + profile
        )
        return ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(result))
    }

    fun restore(
        existing: VehicleProfile,
        document: GaugeConfigTransferClient.ActiveDocument
    ): VehicleProfile {
        require(existing.id == document.vehicleProfileId) {
            "Saved setup belongs to another vehicle"
        }
        val restored = restoredProfile(document)
        val gaugeAlerts = restored.dashboardDraft().alerts
        val phoneOnlyAlerts = existing.dashboardDraft().alerts.filter { local ->
            !local.enabled && gaugeAlerts.none { it.id == local.id || it.pidId == local.pidId }
        }
        val retained = restored.copy(name = existing.name, draft = restored.draft.copy(idlePresets = existing.draft.idlePresets))
        if (phoneOnlyAlerts.isEmpty()) return retained
        val merged = retained.dashboardDraft().let { it.copy(alerts = it.alerts + phoneOnlyAlerts) }
        require(merged.alerts.size <= 32) { "The gauge setup and retained disabled alerts exceed the phone alert limit" }
        return if (retained.transmission == null) {
            retained.copy(draft = retained.draft.copy(alerts = merged.alerts))
        } else {
            retained.withDashboard(merged)
        }
    }

    private fun restoredProfile(
        document: GaugeConfigTransferClient.ActiveDocument
    ): VehicleProfile {
        require(document.vehicleProfileId.matches(Regex("[a-z][a-z0-9._-]{0,63}"))) {
            "Saved car identity is invalid"
        }
        val root = JSONObject(document.json)
        require(root.getString("vehicleProfileId") == document.vehicleProfileId)
        val sources = root.getJSONArray("sources")
        require(sources.length() in 1..2) { "Saved adapter count is invalid" }
        val draft =
            requireNotNull(GaugeDraftComparison.savedDraft(document, document.vehicleProfileId)) {
                "Saved readings cannot be recovered safely by this app"
            }
        require(ConfigurationProjector.blockers(draft).isEmpty()) {
            "Saved readings are not supported"
        }
        val source = sources.getJSONObject(0)
        if (sources.length() ==
            2
        ) {
            require(
                source.getString("role") == "ecm" &&
                    sources.getJSONObject(1).getString("role") == "tcm"
            ) {
                "Saved adapter roles are invalid"
            }
        }
        val binding = AdapterBinding.decode(source.getJSONObject("adapter"))
        val child = if (sources.length() == 2) {
            TransmissionConnection(
                AdapterBinding.decode(sources.getJSONObject(1).getJSONObject("adapter")),
                TransmissionSetup.draft().copy(pages = emptyList())
            )
        } else {
            null
        }
        require(child == null || !samePhysicalAdapter(binding, child.adapter)) {
            "Saved adapters must be distinct"
        }
        return VehicleProfile(
            document.vehicleProfileId,
            if (draft.source ==
                "TCM"
            ) {
                "Recovered transmission"
            } else {
                "Recovered car"
            },
            draft,
            binding,
            child
        ).let {
            if (child !=
                null
            ) {
                it.withDashboard(draft)
            } else {
                it
            }
        }
    }
}
