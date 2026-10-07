package com.lstepnio.egauge

/** One editable dashboard; controller ownership is derived from the selected readings. */
fun VehicleProfile.withDashboard(value: Draft): VehicleProfile {
    require(value.source in setOf("ECM", "BOTH", "TCM") && ConfigurationProjector.blockers(value).isEmpty()) {
        "Choose supported readings and keep at least one vehicle page"
    }
    val child = transmission ?: return copy(draft = value.copy(source = "ECM"))
    val childPages = value.pages.filter { page -> page.pidIds.all { it in ConfigurationProjector.transmissionPidIds } }
    val primaryPages = value.pages.filterNot { it in childPages }
    val mappedIds = value.pages.associate { page ->
        page.id to if (page in childPages && !page.id.startsWith("child.")) "child.${page.id}" else page.id
    }
    val primary = draft.copy(pages = primaryPages, pidId = primaryPages.firstOrNull()?.pidIds?.first() ?: draft.pidId,
        layout = primaryPages.firstOrNull()?.layout ?: draft.layout, alerts = value.alerts,
        actions = value.actions.filter { action -> primaryPages.any { it.id == action.pageId } })
    val secondary = child.draft.copy(pages = childPages.map { it.copy(id = it.id.removePrefix("child.")) },
        pidId = childPages.firstOrNull()?.pidIds?.first() ?: child.draft.pidId, layout = childPages.firstOrNull()?.layout ?: child.draft.layout,
        actions = value.actions.filter { action -> childPages.any { it.id == action.pageId } }
            .map { it.copy(pageId = mappedIds.getValue(it.pageId).removePrefix("child.")) })
    val result = copy(draft = primary, transmission = child.copy(draft = secondary),
        pageOrder = value.pages.map { mappedIds.getValue(it.id) })
    val combined = result.dashboardDraft()
    require(ConfigurationProjector.blockers(combined).isEmpty()) { "Page identities must be unique across the vehicle" }
    ProfileActions.validate(combined.actions, combined.pages)
    return result
}

/** The older runtime ties request service to an entire adapter instead of each reading. */
fun requiresVehicleDashboardFirmware(draft: Draft, secondAdapter: Boolean): Boolean =
    secondAdapter || draft.pages.any { page -> page.pidIds.any { it in ConfigurationProjector.transmissionPidIds } }
