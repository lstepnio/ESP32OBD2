package com.lstepnio.egauge

/** Optional swap-specific child owned by the vehicle's primary ECM connection. */
data class TransmissionConnection(val adapter: AdapterBinding? = null,
                                  val draft: Draft = TransmissionSetup.draft()) {
    init {
        require(draft.source == "TCM" && draft.alerts.isEmpty() &&
            draft.pages.flatMap { it.pidIds }.all { it in ConfigurationProjector.transmissionPidIds }) {
            "Transmission child must use supported TCM pages without alerts"
        }
    }
}

fun samePhysicalAdapter(first: AdapterBinding?, second: AdapterBinding?): Boolean =
    first != null && second != null && (first.address == second.address || first.id == second.id)

/** Explicit migration only: a legacy TCM profile never guesses its parent vehicle. */
fun ProfileCollection.attachTransmission(legacyId: String, parentId: String): ProfileCollection {
    val legacy = profiles.single { it.id == legacyId }
    val parent = profiles.single { it.id == parentId }
    require(legacy.draft.source == "TCM" && parent.draft.source == "ECM" && parent.transmission == null)
    val attached = parent.copy(transmission = TransmissionConnection(legacy.primaryAdapter, legacy.draft))
    return copy(activeId = parent.id, profiles = profiles.filter { it.id != legacy.id }.map {
        if (it.id == parent.id) attached else it
    })
}
