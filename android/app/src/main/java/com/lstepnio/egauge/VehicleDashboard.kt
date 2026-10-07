package com.lstepnio.egauge

/** One editable dashboard; controller ownership is derived from the selected readings. */
fun VehicleProfile.withDashboard(value: Draft): VehicleProfile {
    require(value.source == "BOTH" && ConfigurationProjector.blockers(value).isEmpty()) {
        "Choose supported readings and keep at least one page for each connected adapter"
    }
    val child = requireNotNull(transmission)
    val primaryPages = value.pages.filter { page -> page.pidIds.all { it in ConfigurationProjector.supportedPidIds } }
    val childPages = value.pages.filter { page -> page.pidIds.all { it in ConfigurationProjector.transmissionPidIds } }
    require(primaryPages.isNotEmpty() && childPages.isNotEmpty()) { "Keep at least one page for each connected adapter" }
    val mappedIds = value.pages.associate { page ->
        page.id to if (page in childPages && !page.id.startsWith("child.")) "child.${page.id}" else page.id
    }
    val primary = draft.copy(pages = primaryPages, pidId = primaryPages.first().pidIds.first(),
        layout = primaryPages.first().layout, alerts = value.alerts,
        actions = value.actions.filter { action -> primaryPages.any { it.id == action.pageId } })
    val secondary = child.draft.copy(pages = childPages.map { it.copy(id = it.id.removePrefix("child.")) },
        pidId = childPages.first().pidIds.first(), layout = childPages.first().layout,
        actions = value.actions.filter { action -> childPages.any { it.id == action.pageId } }
            .map { it.copy(pageId = mappedIds.getValue(it.pageId).removePrefix("child.")) })
    val result = copy(draft = primary, transmission = child.copy(draft = secondary),
        pageOrder = value.pages.map { mappedIds.getValue(it.id) })
    val combined = result.combinedDraft()
    require(ConfigurationProjector.blockers(combined).isEmpty()) { "Page identities must be unique across the vehicle" }
    ProfileActions.validate(combined.actions, combined.pages)
    return result
}
