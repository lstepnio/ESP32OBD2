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

/** Combined payload only. Editors and storage retain separate parent/child drafts. */
fun VehicleProfile.combinedDraft(): Draft {
    require(draft.source == "ECM" && primaryAdapter != null)
    val child = requireNotNull(transmission)
    require(child.adapter != null && !samePhysicalAdapter(primaryAdapter, child.adapter))
    val pages = draft.pages + child.draft.pages.map { it.copy(id = "child.${it.id}") }
    val ordered = pageOrder.mapNotNull { id -> pages.firstOrNull { it.id == id } } + pages.filter { it.id !in pageOrder }
    return draft.copy(source = "BOTH", actions = draft.actions + child.draft.actions.map { it.copy(pageId = "child.${it.pageId}") }, pages = ordered)
}

/** One gesture picker across the vehicle; storage keeps the controller owning its target page. */
fun VehicleProfile.withCombinedPageAction(action: PageAction?): VehicleProfile {
    val child = requireNotNull(transmission)
    val combined = combinedDraft()
    ProfileActions.validate(listOfNotNull(action), combined.pages)
    require(combined.pages.map { it.id }.distinct().size == combined.pages.size) { "Page identities must be unique across the vehicle" }
    val childTarget = action != null && draft.pages.none { it.id == action.pageId }
    val primary = draft.copy(actions = if (action != null && !childTarget) listOf(action) else emptyList())
    val secondary = child.draft.copy(actions = if (action != null && childTarget)
        listOf(action.copy(pageId = action.pageId.removePrefix("child."))) else emptyList())
    ProfileActions.validate(primary.actions, primary.pages)
    ProfileActions.validate(secondary.actions, secondary.pages)
    return copy(draft = primary, transmission = child.copy(draft = secondary))
}
