package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.*
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.core.designsystem.*

fun readingName(id: String): String = when (id) {
    "rpm" -> "Engine speed"
    "coolant" -> "Coolant temperature"
    "speed" -> "Vehicle speed"
    "load" -> "Engine load"
    "fuel" -> "Fuel level"
    "tcm" -> "Transmission input speed"
    "tcmtemp" -> "Transmission temperature"
    "tcmgear" -> "Gear"
    else -> "Unknown reading"
}

/** Editor cursor fields are not part of the transmitted page/alert payload. */
fun sameSettings(first: Draft?, second: Draft): Boolean = first != null && first.pages == second.pages &&
    first.source == second.source && first.alerts == second.alerts && first.actions == second.actions

fun pageUi(page: GaugePageDraft, system: MeasurementSystem = MeasurementSystem.Metric): PageUi {
    val reading = demoCatalog.first { it.id == page.pidIds.first() }
    val secondary = page.pidIds.getOrNull(1)?.let { id -> demoCatalog.first { it.id == id } }
    return PageUi(page.id, readingName(reading.id), reading.id, readingName(reading.id), MeasurementUnits.label(reading.unit, system), page.layout,
        ReadingPreviewUi(readingName(reading.id), MeasurementUnits.displayValue(reading.demoValue, reading.unit, system), MeasurementUnits.label(reading.unit, system),
            PreviewLayout.valueOf(page.layout.name), secondaryName = secondary?.let { readingName(it.id) },
            secondaryValue = secondary?.let { MeasurementUnits.displayValue(it.demoValue, it.unit, system) },
            secondaryUnit = secondary?.let { MeasurementUnits.label(it.unit, system) }), secondary?.id)
}

fun readingUi(pid: PidExample, system: MeasurementSystem = MeasurementSystem.Metric) = ReadingUi(pid.id, readingName(pid.id), MeasurementUnits.label(pid.unit, system), listOf(
    DetailUi("PID identifier", pid.id), DetailUi("Service / request", pid.request),
    DetailUi("ECU / source", pid.source), DetailUi("Category", pid.category),
    DetailUi("Availability", "Example only. Vehicle support has not been checked."),
))

fun alertUi(alert: GaugeAlertDraft, system: MeasurementSystem = MeasurementSystem.Metric): AlertUi {
    val reading = demoCatalog.first { it.id == alert.pidId }
    return AlertUi(alert.id, alert.pidId, readingName(alert.pidId), MeasurementUnits.label(reading.unit, system),
        if (alert.direction == AlertDirection.Above) "above" else "below",
        MeasurementUnits.value(alert.warning, reading.unit, system), MeasurementUnits.value(alert.critical, reading.unit, system),
        MeasurementUnits.distance(alert.hysteresis, reading.unit, system), alert.triggerDwellMs / 1000f,
        alert.clearDwellMs / 1000f, MeasurementUnits.range(readingRange(alert.pidId), reading.unit, system),
        alert.priority, alert.warning, alert.critical, alert.hysteresis)
}

fun presentationBlockers(draft: Draft, caps: CapabilitySnapshot?): List<String> = buildList {
    ConfigurationProjector.blockers(draft).forEach { raw ->
        add(when {
            "reset margin" in raw -> "Leave more space between warning and critical. Adjust the limits."
            "adapter" in raw -> "This reading cannot be sent from the second adapter. Choose a main-adapter reading."
            "cannot execute" in raw -> "A page uses an unavailable reading. Choose a supported reading."
            "critical limit" in raw -> "Move the critical limit farther than the warning limit."
            "outside its supported range" in raw -> "Choose limits within this reading's supported range."
            "timing" in raw -> "The alert delay is out of range. Choose a delay of 60 seconds or less."
            "action" in raw || "swipe" in raw || "page for" in raw -> raw
            else -> "A page needs a valid reading and layout. Review your pages."
        })
    }
    caps?.let {
        if (draft.actions.isNotEmpty() && it.pageActionsVersion != 1) add("Update the gauge before sending gesture actions")
        if (draft.source == "BOTH" && (!BuildConfig.DEBUG || it.dualAdapterVersion != 1)) add("Update the gauge before using both adapters")
        if (draft.pages.size > it.maxPages) add("Your gauge supports ${it.maxPages} pages. Remove an extra page.")
        val unsupported = draft.pages.map { it.layout }.distinct().filter { layout -> layout !in it.supportedRenderers }
        if (unsupported.isNotEmpty()) add("${unsupported.joinToString { layout -> layout.label }} is preview-only on this gauge. Choose a supported layout.")
        if (!it.experimentalNumericConfig || it.configurationVersion == 0)
            add("This gauge cannot receive dashboard changes. You can keep designing a preview.")
    }
}.distinct()

fun AppViewModel.presentationState(nowElapsedMs: Long): CompanionUiState {
    val prefs = presentationPreferences
    val freshConnection = connection.fresh(nowElapsedMs)
    val disconnected = connection.phase in setOf(ConnectionPhase.Retrying, ConnectionPhase.BluetoothOff, ConnectionPhase.PermissionRequired)
    val found = capabilities != null && !disconnected && connection.phase != ConnectionPhase.Searching
    val androidBonded = capabilities != null && connection.phase !in setOf(
        ConnectionPhase.BluetoothOff, ConnectionPhase.PermissionRequired) && androidBondedForUi()
    val busy = scanning || hostedUpdateBusy || updateInProgress ||
        (operation.stage != OperationStage.IDLE && !operation.terminal)
    val system = displaySettings?.takeIf { it.version >= 2 }?.units ?: prefs.measurementSystem
    val pages = draft.pages.map { pageUi(it, system) }
    val confirmed = !disconnected && ownerAccess == OwnerAccess.AUTHENTICATED && sentProfileId == profileCollection.activeId && sameSettings(sentDraft, transmittedDraft) &&
        isConfirmedSetup(activeConfigRevision, expectedSentDigest, runtimeIdentity)
    val blockers = presentationBlockers(transmittedDraft, capabilities) + (if (bothAdapters && runCatching { profileCollection.active.combinedDraft() }.isFailure) listOf("Choose distinct engine and transmission adapters before sending") else emptyList()) +
        if (runtimeIdentity?.trial == true) listOf("Your gauge is still checking its settings. Wait, then check again.") else emptyList()
    val needsCheck = activeConfigRevision == null || verifiedConfigHash == null || ownerAccess != OwnerAccess.AUTHENTICATED ||
        (configurationNeedsReview && !configurationRecoveryRead) || disconnected
    val details = presentationDetails()
    var op = operationUi(operation, confirmed)
    if (updatePreparation == "downloading") op = op.copy(status = StatusUi("Downloading update",
        "Keep the app open while the update is checked.", StatusTone.Loading), busy = true, visible = true)
    // A subsequent read or navigation must not erase an unresolved configuration outcome.
    if (!busy && configurationNeedsReview && lastConfigurationOutcome != null) {
        op = operationUi(requireNotNull(lastConfigurationOutcome), confirmed).copy(
            status = if (configurationRecoveryRead) StatusUi("Your gauge has been checked",
                "Review its current settings before sending again.", StatusTone.Stale)
            else operationUi(requireNotNull(lastConfigurationOutcome)).status,
            needsCheck = true)
    }
    val updateRecovery = updateRecoveryResult
    if (!busy && updateRecovery != null && updateRecovery.state != UpdateRecoveryState.INSTALLED) {
        op = op.copy(status = updateRecoveryUi(updateRecovery), visible = true, needsCheck = true, update = true)
    }
    val status = when {
        profileError != null -> StatusUi("Your saved profiles need attention", "Editing is paused to protect your settings. Review the problem in Expert > Device data.", StatusTone.Error)
        presentationError != null -> friendlyFailure(presentationError)
        op.visible && (op.busy || op.needsCheck) -> op.status
        runtimeIdentity?.usedPreviousGeneration == true -> StatusUi("Earlier settings are running", "Your gauge restored an earlier setup. Review your pages before sending again.", StatusTone.Stale)
        runtimeIdentity?.trial == true -> StatusUi("Your gauge is still checking these settings", "Keep it powered and check again.", StatusTone.Stale)
        !freshConnection && connection.phase != ConnectionPhase.Idle -> connectionStatus(connection, nowElapsedMs)
        updatePreparation == "held" -> StatusUi("Update needs attention", hostedUpdateMessage, StatusTone.Stale)
        confirmed -> StatusUi("Saved & running on gauge", "Your gauge confirmed these settings.", StatusTone.Success)
        !found -> StatusUi("Your gauge is not connected", "Connect to check its current settings.", StatusTone.Neutral)
        needsCheck -> StatusUi("Check your gauge before sending", "We will read its current settings so newer changes are protected.")
        blockers.isNotEmpty() -> StatusUi("A page needs attention", blockers.first(), StatusTone.Stale)
        else -> StatusUi("Changes ready to send", "${pages.size} pages · ${draft.alerts.size} alerts")
    }
    val homeAction = when {
        connection.phase in setOf(ConnectionPhase.PermissionRequired, ConnectionPhase.BluetoothOff,
            ConnectionPhase.PairRequired, ConnectionPhase.ChooseGauge) -> HomeAction.SetUp
        !freshConnection && connection.phase != ConnectionPhase.Idle -> HomeAction.Customize
        !found -> HomeAction.SetUp; confirmed -> HomeAction.Customize
        needsCheck -> HomeAction.Check; else -> HomeAction.Review }
    val canSend = found && !busy && profileError == null && !needsCheck && blockers.isEmpty() &&
        capabilities?.experimentalNumericConfig == true
    val car = carState(nowElapsedMs, busy, details)
    val updateStatus = when {
        updateInProgress || updatePreparation == "downloading" -> op.status
        updateRecovery != null && updateRecovery.state != UpdateRecoveryState.INSTALLED -> updateRecoveryUi(updateRecovery)
        updatePreparation == "held" -> StatusUi("Update needs attention", hostedUpdateMessage, StatusTone.Stale)
        updatePreparation == "failed" -> friendlyFailure(hostedUpdateMessage, true)
        updateReady -> StatusUi("Update ready to install", "A signed development update is ready. Keep your gauge powered during installation.")
        hostedUpdate != null -> StatusUi("Update available", "A signed development update is available.")
        updateRecovery != null -> updateRecoveryUi(updateRecovery)
        updatePreparation == "current" -> StatusUi("Your gauge is up to date", "No newer signed update is available.", StatusTone.Success)
        else -> StatusUi("Development updates", "Signed test releases are available here.")
    }
    val updateNotice = when {
        !freshConnection || !found -> UpdateNotice.None
        updateRecovery?.state !in setOf(null, UpdateRecoveryState.INSTALLED) || updatePreparation == "held" -> UpdateNotice.NeedsCheck
        hostedUpdate != null && updateReady -> UpdateNotice.Ready
        else -> UpdateNotice.None
    }
    return CompanionUiState(
        HomeUiState(currentGaugeName, when (updateNotice) {
            UpdateNotice.Ready -> "Update ready"
            UpdateNotice.NeedsCheck -> "Update needs check"
            UpdateNotice.None -> if (connection.phase == ConnectionPhase.Idle && found) "Last checked"
                else connectionLabel(connection, nowElapsedMs)
        }, freshConnection, status, transmittedDraft.pages.map { pageUi(it, system) },
            !confirmed, when (homeAction) { HomeAction.SetUp -> when (connection.phase) {
                ConnectionPhase.PermissionRequired -> "Allow nearby devices"
                ConnectionPhase.BluetoothOff -> "Turn on Bluetooth"
                ConnectionPhase.PairRequired -> "Pair gauge"
                ConnectionPhase.ChooseGauge -> "Choose gauge"
                else -> if (rememberedGaugeId != null) "Connect gauge" else "Set up gauge"
            }; HomeAction.Check -> "Check gauge"
                HomeAction.Review -> "Review and send"; HomeAction.Customize -> "Customize" }, homeAction, busy, details,
            updateNotice),
        CustomizeUiState(pages, editingPageIndex, demoCatalog.filter { it.id in ConfigurationProjector.pagePidIds(draft.source) }.map { readingUi(it, system) },
            draft.alerts.map { alertUi(it, system) }, blockers, canSend, needsCheck, found, busy, profileError == null && !busy, prefs.advanced,
            capabilities?.supportedRenderers.orEmpty(), details, system, transmittedDraft.pages.map { pageUi(it, system) },
            actionSummary = transmittedDraft.actions.takeIf { it.isNotEmpty() }?.joinToString("\n") { action ->
                val name = transmittedDraft.pages.firstOrNull { it.id == action.pageId }?.name ?: "Unavailable page"
                "Jump to $name · ${action.count} swipes up within ${action.windowMs / 1000} seconds"
            }),
        car,
        SettingsUiState(currentGaugeName, found, displaySettings?.rotation ?: savedGauge?.rotation,
            found && ((capabilities?.displaySettingsVersion ?: 0) >= 1 || capabilities?.displayRotationWrite == true),
            busy, prefs.advanced, prefs.dynamicColor, bootIdentity?.version ?: "Not checked", details,
            capabilities?.displaySettingsVersion ?: 0, displaySettings?.brightness, system, displaySettings?.cycleSeconds,
            settingsReadCurrent(settingsObservedAtElapsedMs, nowElapsedMs, settingsCheckFailed) && freshConnection,
            knownGauges.map { GaugeUi(it.id, it.name, profileCollection.profiles.firstOrNull { p -> p.id == it.vehicleId }?.name,
                it.source, it.vehicleId != null && GaugeAssociations(it.id, listOf(it)).context(it.id, profileCollection) == null, it.bothAdapters)
            }, rememberedGaugeId),
        ExpertUiState(found && !busy, found && capabilities?.hardwareCapacityVersion == 1 && !busy,
            canAdoptGaugeDraft, details, profileCollection.active.name, profileCollection.active.draft.source,
            profileCollection.active.primaryAdapter != null, profileCollection.active.transmission != null,
            draft.source, !busy && profileError == null,
            selectedVehicleAdapter?.address?.takeLast(5), adapterCandidates,
            capabilities?.adapterRegistryVersion == 1 && freshConnection && !busy,
            adapterMessage, if (profileCollection.active.draft.source == "TCM") profileCollection.profiles.filter {
                it.draft.source == "ECM" && it.primaryAdapter != null && it.transmission == null &&
                    !samePhysicalAdapter(it.primaryAdapter, profileCollection.active.primaryAdapter)
            }.map { VehicleUi(it.id, it.name) } else emptyList(), deviceMessage, bothAdapters,
            !busy && capabilities?.dualAdapterVersion == 1 && runCatching { profileCollection.active.combinedDraft() }.isSuccess),
        SetupUiState(found || androidBonded, ownerAccess, busy || connection.phase in setOf(ConnectionPhase.Searching, ConnectionPhase.Checking), gaugeCandidates.mapIndexed { index, candidate ->
            val shortId = candidate.id.filter(Char::isLetterOrDigit).takeLast(6).uppercase()
            val visibleName = candidate.name.removeSuffix("-$shortId").ifBlank { "Gauge ${index + 1}" }
            CandidateUi(candidate.id, visibleName, shortId, listOf(
                DetailUi("Gauge identifier", candidate.id), DetailUi("Signal", "${candidate.signalDbm} dBm")))
        }, if (presentationError != null) friendlyFailure(presentationError) else if (op.needsCheck || op.busy) op.status
            else if (connection.phase != ConnectionPhase.Idle) connectionStatus(connection, nowElapsedMs)
            else StatusUi(if (ownerAccess == OwnerAccess.AUTHENTICATED) "Your phone is paired" else if (found) "Gauge found" else "Ready to find your gauge",
                if (ownerAccess == OwnerAccess.AUTHENTICATED) "Your gauge confirmed access." else "Keep your gauge powered and nearby."), details,
            pairingWindowForUi(nowElapsedMs), androidBonded),
        UpdatesUiState(updateStatus, hostedUpdate?.release?.version, bootIdentity?.version ?: "Not checked",
            found && !busy, updateReady && found && !busy && capabilities?.experimentalNumericConfig == true &&
                updateRecovery?.state !in setOf(UpdateRecoveryState.CHECK_REQUIRED, UpdateRecoveryState.WAITING_FOR_CONFIRMATION),
            updateReady, busy,
            updateRecovery?.state in setOf(UpdateRecoveryState.CHECK_REQUIRED, UpdateRecoveryState.WAITING_FOR_CONFIRMATION), details,
            updatePreparation == "held",
            updatePreparation == "failed" && hostedUpdateMessage == HOSTED_RELEASE_FEED_UNAVAILABLE),
        op,
        if (gaugeAssociationError != null) StatusUi("Your saved gauges need attention",
            "Connection is paused to protect your saved associations. Review in Expert.", StatusTone.Error)
        else if (profileError != null) StatusUi("Your saved profiles need attention",
            "Editing is paused to protect your settings. Review the problem in Expert > Device data.", StatusTone.Error)
        else presentationError?.let { friendlyFailure(it) }
            ?: if (disconnected) connectionStatus(connection, nowElapsedMs) else null,
        connection,
    )
}

private fun AppViewModel.carState(now: Long, busy: Boolean, details: List<DetailUi>): CarUiState {
    val data = diagnostics?.takeIf { it.source == draft.source &&
        (it.revision == null || (activeDocument?.vehicleProfileId == profileCollection.activeId &&
            it.revision == activeDocument?.revision)) }
    val elapsed = diagnosticsObservedAtElapsedMs?.let { now - it } ?: Long.MAX_VALUE
    val sources = vehicleSources.flatMap { source ->
        val snapshot = diagnosticsBySource[source] ?: data?.takeIf { it.source == source }
        val age = diagnosticsCheckedAt[source]?.let { now-it } ?: elapsed
        diagnosticSources(snapshot?.takeIf { it.revision == activeDocument?.revision || it.revision == null }, age, source)
            .filter { it.title == if (source == "TCM") "Transmission" else "Engine" }
    }
    val links = vehicleSources.map { source ->
        val adapter = profileCollection.active.adapterFor(source)
        ConnectionLinkUi("${profileCollection.activeId}:$source", "${if (source == "TCM") "Transmission" else "Engine"} adapter",
            vehicleConnectionStatus(adapterStatuses[source] ?: adapterSourceStatus?.takeIf { it.sourceId.equals(source, true) },
                (adapterCheckedAt[source] ?: adapterStatusCheckedAt)?.let { now-it }, profileCollection.activeId,
                configuredSourceId(activeDocument, source) ?: "", adapter != null,
                vehicleSetupMatches(activeDocument, profileCollection.activeId, source, adapter), source in vehicleFailures))
    }
    val status = sources.first { it.title == if (draft.source == "TCM") "Transmission" else "Engine" }.status
    val faults = sources.flatMap { it.categories.flatMap { category -> category.faults } }
    return CarUiState(profileCollection.active.name, profileCollection.profiles.map { VehicleUi(it.id, it.name) },
        profileCollection.activeId, status, faults, !busy && capabilities?.experimentalNumericConfig == true,
        (diagnosticsBySource.values.toList() + listOfNotNull(data)).any { it.categories == null || it.categories.any { category -> category.truncated } }, profileError != null, details,
        faultSources = sources, connectionLinks = links,
        connectionStatus = links.firstOrNull { it.status.tone != StatusTone.Success }?.status ?: links.first().status,
        setupNeeded = !sameSettings(sentDraft, transmittedDraft) || vehicleSources.any { !vehicleSetupMatches(activeDocument, profileCollection.activeId, it, profileCollection.active.adapterFor(it)) },
        adapterAvailable = capabilities?.adapterRegistryVersion == 1 && !busy && connection.fresh(now),
        adapterSelected = selectedVehicleAdapter?.let { "${if (it.driver == "elm-bench-v1") "Bench simulator" else if (draft.source == "TCM") "Transmission adapter" else "Engine adapter"} · ${it.address.takeLast(5)}" },
        adapterMessage = if (adapterSourceStatus != null && (adapterStatusCheckedAt == null ||
            now - adapterStatusCheckedAt!! > 15_000)) "Adapter status is out of date. Check again."
        else adapterMessage ?: if (activeDocument?.vehicleProfileId != profileCollection.activeId)
            "This car's setup has not been confirmed on the gauge."
        else runCatching {
            val savedSources = org.json.JSONObject(activeDocument!!.json).getJSONArray("sources")
            val saved = (0 until savedSources.length()).map { savedSources.getJSONObject(it) }
                .singleOrNull { it.getString("role").equals(draft.source, true) }?.optJSONObject("adapter")
            if (saved == null && selectedVehicleAdapter == null)
                "No adapter selected on the gauge. Find your adapter when it is powered."
            else if (saved?.optString("id") == selectedVehicleAdapter?.id && saved != null)
                "Adapter selection saved on the gauge. Check its connection below."
            else "Adapter selection differs from the gauge. Send setup to use this selection."
        }.getOrDefault("Refresh gauge settings to check the saved adapter."),
        adapterCandidates = adapterCandidates,
        canSendAdapter = !busy && connection.fresh(now) && profileError == null && capabilities?.adapterRegistryVersion == 1 &&
            activeConfigRevision != null && verifiedConfigHash != null && ConfigurationProjector.blockers(transmittedDraft).isEmpty() && (transmittedDraft.actions.isEmpty() || capabilities?.pageActionsVersion == 1) && (!bothAdapters || capabilities?.dualAdapterVersion == 1),
        transmissionChild = draft.source == "TCM" && profileCollection.active.transmission != null,
        actionPages = draft.pages, actions = draft.actions, canEditActions = !busy && profileError == null,
        actionsSupported = capabilities?.pageActionsVersion == 1)
}

fun faultDescription(code: String): String = when (code) {
    "P0300" -> "Engine misfire detected in one or more cylinders"
    "P0301", "P0302", "P0303", "P0304", "P0305", "P0306", "P0307", "P0308" -> "Engine misfire in cylinder ${code.last()}"
    "P0171" -> "The engine is running too lean on bank 1"
    "P0174" -> "The engine is running too lean on bank 2"
    "P0420" -> "Catalyst efficiency is below the expected range on bank 1"
    "P0128" -> "Coolant is taking longer than expected to reach operating temperature"
    else -> "An explanation is not available for this code. Check your vehicle's service information."
}
