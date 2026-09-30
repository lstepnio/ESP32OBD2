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
    else -> "Unknown reading"
}

/** Editor cursor fields are not part of the transmitted page/alert payload. */
fun sameSettings(first: Draft?, second: Draft): Boolean = first != null && first.pages == second.pages &&
    first.source == second.source && first.alerts == second.alerts

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
            else -> "A page needs a valid reading and layout. Review your pages."
        })
    }
    caps?.let {
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
    val busy = scanning || hostedUpdateBusy || updateInProgress ||
        (operation.stage != OperationStage.IDLE && !operation.terminal)
    val system = displaySettings?.takeIf { it.version == 2 }?.units ?: prefs.measurementSystem
    val pages = draft.pages.map { pageUi(it, system) }
    val confirmed = !disconnected && ownerAccess == OwnerAccess.AUTHENTICATED && sentProfileId == profileCollection.activeId && sameSettings(sentDraft, draft) &&
        isConfirmedSetup(activeConfigRevision, expectedSentDigest, runtimeIdentity)
    val blockers = presentationBlockers(draft, capabilities) +
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
    val selected = demoCatalog.first { it.id == draft.pidId }
    val decoded = when (val result = decodeExample(selected, labInput)) {
        is DecodeResult.Value -> StatusUi("Example result: ${result.display}", "Nothing was sent to the car.")
        is DecodeResult.Error -> StatusUi("Could not decode this response", "${result.message} Check the response bytes.", StatusTone.Error)
    }
    val request = when (val result = previewReadRequest(customRequestInput)) {
        is ReadRequestPreview.Valid -> StatusUi("Read request is valid", "${result.description}. Expected prefix ${result.responsePrefix}. Example only; nothing was sent.")
        is ReadRequestPreview.Invalid -> StatusUi("Check the read request", "${result.reason} Edit the bytes and try again.", StatusTone.Error)
    }
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
        HomeUiState(prefs.gaugeName, when (updateNotice) {
            UpdateNotice.Ready -> "Update ready"
            UpdateNotice.NeedsCheck -> "Update needs check"
            UpdateNotice.None -> if (connection.phase == ConnectionPhase.Idle && found) "Last checked"
                else connectionLabel(connection, nowElapsedMs)
        }, freshConnection, status, pages,
            !confirmed, when (homeAction) { HomeAction.SetUp -> when (connection.phase) {
                ConnectionPhase.PermissionRequired -> "Allow nearby devices"
                ConnectionPhase.BluetoothOff -> "Turn on Bluetooth"
                ConnectionPhase.PairRequired -> "Pair gauge"
                ConnectionPhase.ChooseGauge -> "Choose gauge"
                else -> if (rememberedGaugeId != null) "Connect gauge" else "Set up gauge"
            }; HomeAction.Check -> "Check gauge"
                HomeAction.Review -> "Review and send"; HomeAction.Customize -> "Customize" }, homeAction, busy, details,
            updateNotice),
        CustomizeUiState(pages, editingPageIndex, demoCatalog.filter { it.id in ConfigurationProjector.supportedPidIds }.map { readingUi(it, system) },
            draft.alerts.map { alertUi(it, system) }, blockers, canSend, needsCheck, found, busy, profileError == null && !busy, prefs.advanced,
            capabilities?.supportedRenderers.orEmpty(), details, system),
        car,
        SettingsUiState(prefs.gaugeName, found, displaySettings?.rotation ?: savedGauge?.rotation,
            found && ((capabilities?.displaySettingsVersion ?: 0) >= 1 || capabilities?.displayRotationWrite == true),
            busy, prefs.advanced, prefs.dynamicColor, bootIdentity?.version ?: "Not checked", details,
            capabilities?.displaySettingsVersion ?: 0, displaySettings?.brightness, system),
        ExpertUiState(demoCatalog.filter { pid -> (sourceFilter == "All" || pid.source == sourceFilter) &&
            (query.isBlank() || "${pid.name} ${pid.request} ${pid.category} ${pid.source}".contains(query, true)) }.map { readingUi(it, system) },
            found && !busy, found && capabilities?.hardwareCapacityVersion == 1 && !busy,
            found && capabilities?.wifiBulk == "experimental-softap-aead-v2" && !busy,
            profileCollection.active.secondAdapterEnabled, canAdoptGaugeDraft, details, query, sourceFilter, draft.pidId, labInput,
            decoded, customRequestInput, customSource, request, wifiSecurityMessage, busy),
        SetupUiState(found, ownerAccess, busy || connection.phase in setOf(ConnectionPhase.Searching, ConnectionPhase.Checking), gaugeCandidates.mapIndexed { index, candidate ->
            CandidateUi(candidate.id, candidate.name.ifBlank { "Gauge ${index + 1}" }, listOf(
                DetailUi("Gauge identifier", candidate.id), DetailUi("Signal", "${candidate.signalDbm} dBm")))
        }, if (presentationError != null) friendlyFailure(presentationError) else if (op.needsCheck || op.busy) op.status
            else if (connection.phase != ConnectionPhase.Idle) connectionStatus(connection, nowElapsedMs)
            else StatusUi(if (ownerAccess == OwnerAccess.AUTHENTICATED) "Your phone is paired" else if (found) "Gauge found" else "Ready to find your gauge",
                if (ownerAccess == OwnerAccess.AUTHENTICATED) "Your gauge confirmed access." else "Keep your gauge powered and nearby."), details),
        UpdatesUiState(updateStatus, hostedUpdate?.release?.version, bootIdentity?.version ?: "Not checked",
            found && !busy, updateReady && found && !busy && capabilities?.experimentalNumericConfig == true &&
                updateRecovery?.state !in setOf(UpdateRecoveryState.CHECK_REQUIRED, UpdateRecoveryState.WAITING_FOR_CONFIRMATION),
            updateReady, busy,
            updateRecovery?.state in setOf(UpdateRecoveryState.CHECK_REQUIRED, UpdateRecoveryState.WAITING_FOR_CONFIRMATION), details,
            updatePreparation == "held",
            updatePreparation == "failed" && hostedUpdateMessage == HOSTED_RELEASE_FEED_UNAVAILABLE),
        op,
        if (profileError != null) StatusUi("Your saved profiles need attention",
            "Editing is paused to protect your settings. Review the problem in Expert > Device data.", StatusTone.Error)
        else presentationError?.let { friendlyFailure(it) }
            ?: if (disconnected) connectionStatus(connection, nowElapsedMs) else null,
        connection,
    )
}

private fun AppViewModel.carState(now: Long, busy: Boolean, details: List<DetailUi>): CarUiState {
    val data = diagnostics
    val current = diagnosticsCurrent(now)
    val status = when {
        data == null -> StatusUi("No car readings yet", "Adapter setup is not available in this app yet. You can still customize your gauge.", StatusTone.Disabled)
        !current -> StatusUi("Car readings are out of date",
            if (data.milOn) "The check-engine light was on at the last check. Check again before relying on this status."
            else "The last check is more than 30 seconds old. Check again before relying on it.", StatusTone.Stale)
        !data.milFresh -> StatusUi("Check-engine status is unavailable", "Your gauge has no recent response from the car. Check the adapter connection.", StatusTone.Stale)
        data.milOn -> StatusUi("Check-engine light is on", "${data.reportedCount} fault codes reported. Review the available codes.", StatusTone.Critical)
        else -> StatusUi("Check-engine light is off", "This is the last checked status, not a live reading.", StatusTone.Success)
    }
    val faults = if (data != null) listOfNotNull(
        data.confirmedFirst?.let { FaultUi(it, faultDescription(it), if (current && data.confirmedFresh) "Confirmed" else "Last checked · Confirmed") },
        data.pendingFirst?.let { FaultUi(it, faultDescription(it), if (current && data.pendingFresh) "Pending" else "Last checked · Pending") },
        data.permanentFirst?.let { FaultUi(it, faultDescription(it), if (current && data.permanentFresh) "Permanent" else "Last checked · Permanent") },
    ) else emptyList()
    return CarUiState(profileCollection.active.name, profileCollection.profiles.map { VehicleUi(it.id, it.name) },
        profileCollection.activeId, status, faults, !busy && capabilities?.experimentalNumericConfig == true,
        data != null, profileError != null, details)
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
