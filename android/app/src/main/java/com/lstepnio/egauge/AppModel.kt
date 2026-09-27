package com.lstepnio.egauge

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** All catalog rows below are examples, never vehicle capability evidence. */
data class PidExample(
    val id: String,
    val name: String,
    val source: String,
    val request: String,
    val unit: String,
    val demoValue: String,
    val exampleKind: String,
    val category: String,
    val gaugeLabel: String,
)

/** Populated only from an adapter session with a known vehicle and ECU source. */
enum class VehiclePidStatus { Responding, NoResponse }

data class VehiclePidObservation(
    val profileId: String,
    val sessionId: String,
    val adapterId: String,
    val ecuId: String,
    val pidId: String,
    val source: String,
    val status: VehiclePidStatus,
    val observedAtMillis: Long,
)

val demoCatalog = listOf(
    PidExample("rpm", "Engine RPM", "ECM", "01 0C", "rpm", "2,840", "Example response", "Engine", "ENGINE RPM"),
    PidExample("coolant", "Coolant temperature", "ECM", "01 05", "°C", "92", "Example response", "Thermal", "COOLANT"),
    PidExample("speed", "Vehicle speed", "ECM", "01 0D", "km/h", "64", "Example response", "Driving", "SPEED"),
    PidExample("load", "Calculated load", "ECM", "01 04", "%", "38", "Example response", "Engine", "ENGINE LOAD"),
    PidExample("fuel", "Fuel level", "ECM", "01 2F", "%", "73", "Example response", "Fuel", "FUEL LEVEL"),
    PidExample("tcm", "Transmission input speed", "TCM", "Vehicle specific", "rpm", "2,120", "Synthetic only", "Transmission", "INPUT SPEED"),
)

enum class Destination(val label: String, val glyph: String) {
    Gauge("Gauge", "◉"), Readings("Readings", "≡"), Vehicle("Vehicle", "⌂"), Settings("Settings", "⚙")
}

enum class GaugeLayout(val label: String) {
    Numeric("Numeric"), Arc("Arc"), Bar("Bar"), Trend("Trend"), Dual("Dual")
}

data class GaugePageDraft(
    val id: String,
    val name: String,
    val layout: GaugeLayout,
    val pidIds: List<String>,
)

fun defaultGaugePages(primary: String = "rpm", layout: GaugeLayout = GaugeLayout.Numeric): List<GaugePageDraft> {
    val ordered = listOf(primary, "rpm", "coolant", "speed").distinct().take(3)
    return ordered.mapIndexed { index, pidId ->
        val definition = demoCatalog.first { it.id == pidId }
        GaugePageDraft("page.${index + 1}.${pidId}", definition.gaugeLabel, if (index == 0) layout else GaugeLayout.Numeric,
            listOf(pidId))
    }
}

enum class OwnerAccess { UNKNOWN, DISCOVERED, AUTHENTICATED }

data class Draft(
    val pidId: String = "rpm",
    val layout: GaugeLayout = GaugeLayout.Numeric,
    val warning: Int = 105,
    val critical: Int = 115,
    val hysteresis: Int = 3,
    val triggerDwellMs: Int = 1000,
    val clearDwellMs: Int = 2000,
    val source: String = "ECM",
    val pages: List<GaugePageDraft> = defaultGaugePages(pidId, layout),
)

data class CapabilitySnapshot(
    val board: String,
    val protocolMajor: Int,
    val maxAdapterLinks: Int,
    val simultaneousVerified: Boolean,
    val configWrite: Boolean,
    val experimentalNumericConfig: Boolean,
    val savedStateRead: Boolean,
    val quickSelect: Boolean,
    val displayRotationWrite: Boolean,
    val ota: Boolean,
    val wifiBulk: String?,
    val hardwareCapacityVersion: Int?,
    val configurationVersion: Int = 0,
    val maxPages: Int = 0,
    val supportedRenderers: Set<GaugeLayout> = emptySet(),
)

data class GaugeSavedSnapshot(val readingIndex: Int, val rotation: Int, val revision: Long)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val bleClient = BleCapabilityClient(application)
    private val operationCoordinator = OperationCoordinator()
    private val profileStore = ProfileStore(application)
    private var debugOtaPauseBeforeActivationMs = 0L

    fun setDebugOtaPauseBeforeActivationMs(value: Long) {
        val debuggable = getApplication<Application>().applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        debugOtaPauseBeforeActivationMs = if (debuggable) value.coerceIn(0, 60_000) else 0
    }
    private val associationStore = GaugeAssociationStore(application)
    private val updateJournal = UpdateRecoveryJournal(application)
    private val loadedProfiles = profileStore.load()

    var destination by mutableStateOf(Destination.Gauge)
        private set
    var profileCollection by mutableStateOf(loadedProfiles.collection)
        private set
    var profileError by mutableStateOf(loadedProfiles.error)
        private set
    var profileNameInput by mutableStateOf("")
        private set
    var draft by mutableStateOf(profileCollection.active.draft)
        private set
    var editingPageIndex by mutableStateOf(0)
        private set
    var query by mutableStateOf("")
        private set
    var sourceFilter by mutableStateOf("All")
        private set
    // Capability reads from the gauge do not populate vehicle PID observations.
    var vehicleObservations by mutableStateOf<List<VehiclePidObservation>>(emptyList())
        private set
    var activeVehicleSessionId by mutableStateOf<String?>(null)
        private set
    var deviceMessage by mutableStateOf("No gauge connected")
        private set
    var capabilities by mutableStateOf<CapabilitySnapshot?>(null)
        private set
    var gaugeCandidates by mutableStateOf<List<GaugeCandidate>>(emptyList())
        private set
    var ownerAccess by mutableStateOf(OwnerAccess.UNKNOWN)
        private set
    var savedGauge by mutableStateOf<GaugeSavedSnapshot?>(null)
        private set
    var diagnostics by mutableStateOf<GaugeConfigTransferClient.Diagnostics?>(null)
        private set
    var diagnosticsObservedAtElapsedMs by mutableStateOf<Long?>(null)
        private set
    var bootIdentity by mutableStateOf<GaugeConfigTransferClient.BootIdentity?>(null)
        private set
    var runtimeIdentity by mutableStateOf<GaugeConfigTransferClient.RuntimeIdentity?>(null)
        private set
    var hardwareSnapshot by mutableStateOf<GaugeConfigTransferClient.HardwareSnapshot?>(null)
        private set
    private var selectedUpdate: DevUpdateBundle? = null
    private var updateMayHaveChangedGauge = false
    val updateReady: Boolean get() = selectedUpdate != null
    fun updateBundle(): DevUpdateBundle = selectedUpdate ?: error("Select a signed update package first")
    var updateInProgress by mutableStateOf(false)
        private set
    private var pendingUpdateRecovery = updateJournal.read()
    var updateRecoveryResult by mutableStateOf(pendingUpdateRecovery?.let {
        UpdateRecoveryResult(UpdateRecoveryState.CHECK_REQUIRED,
            "A previous update was interrupted. Check installed firmware before retrying.", false)
    })
        private set
    var updatePackageMessage by mutableStateOf(pendingUpdateRecovery?.let {
        "A previous update was interrupted. Reconnect and check running firmware before retrying."
    } ?: "No update package selected")
        private set
    var hostedUpdate by mutableStateOf<HostedUpdate?>(null)
        private set
    var hostedUpdateMessage by mutableStateOf("Hosted development updates have not been checked")
        private set
    var hostedUpdateBusy by mutableStateOf(false)
        private set
    var wifiSecurityMessage by mutableStateOf("Not run on this connection")
        private set
    var activeConfigRevision by mutableStateOf<Long?>(null)
        private set
    var activeDocument by mutableStateOf<GaugeConfigTransferClient.ActiveDocument?>(null)
        private set
    var verifiedConfigHash by mutableStateOf<String?>(null)
        private set
    var documentMessage by mutableStateOf<String?>(null)
        private set
    var documentReadFailed by mutableStateOf(false)
        private set
    val draftComparison: GaugeDraftComparison?
        get() = activeDocument?.let { GaugeDraftComparison.from(it, profileCollection.activeId, draft) }
    val canAdoptGaugeDraft: Boolean
        get() = activeDocument?.let {
            GaugeDraftComparison.savedDraft(it, profileCollection.activeId) != null
        } == true
    var sentDraft by mutableStateOf<Draft?>(null)
        private set
    var sentProfileId by mutableStateOf<String?>(null)
        private set
    private var sentDigest: String? = null
    val configurationBlockers: List<String>
        get() = buildList {
            if (profileError != null) add("Local profile data needs recovery before applying")
            if (draft.warning >= draft.critical) add("Coolant critical limit must exceed warning")
            if (draft.warning !in -40..215 || draft.critical !in -40..215)
                add("Coolant limits must be within -40 to 215 °C")
            if (draft.warning - draft.hysteresis < -40 ||
                draft.hysteresis >= draft.critical - draft.warning)
                add("Coolant hysteresis does not fit the selected limits")
            if (capabilities == null) add("Read gauge capabilities first")
            else if (capabilities?.configWrite != true) add("Gauge does not offer full configuration writes")
            val observed = activeVehicleSessionId != null && vehicleObservations.any { observation ->
                observation.profileId == profileCollection.activeId &&
                    observation.sessionId == activeVehicleSessionId &&
                    observation.adapterId.isNotBlank() && observation.ecuId.isNotBlank() &&
                    observation.pidId == draft.pidId && observation.source == draft.source &&
                    observation.status == VehiclePidStatus.Responding
            }
            if (activeVehicleSessionId == null) add("No vehicle discovery session is active")
            else if (!observed) add("Selected PID has no responding evidence for this profile and ECU")
            add("General configuration and vehicle evidence are not implemented")
        }
    var scanning by mutableStateOf(false)
        private set
    var discoveryPreview by mutableStateOf(false)
        private set
    var labOpen by mutableStateOf(false)
        private set
    var labInput by mutableStateOf("41 0C 2C 60")
        private set
    var customLabOpen by mutableStateOf(false)
        private set
    var customRequestInput by mutableStateOf("22 F1 90")
        private set
    var customSource by mutableStateOf("ECM")
        private set
    var advancedReadingsOpen by mutableStateOf(false)
        private set
    var technicalDetailsOpen by mutableStateOf(false)
        private set
    var advancedConnectionsOpen by mutableStateOf(false)
        private set
    var operation by mutableStateOf(OperationState.Idle)
        private set

    fun navigate(value: Destination) { destination = value }
    fun search(value: String) { query = value }
    fun filter(value: String) { sourceFilter = value }
    fun showDiscoveryPreview(value: Boolean) { discoveryPreview = value }
    fun showLab(value: Boolean) { labOpen = value }
    fun editLabInput(value: String) { labInput = value.take(128) }
    fun showCustomLab(value: Boolean) { customLabOpen = value }
    fun showAdvancedReadings(value: Boolean) { advancedReadingsOpen = value }
    fun showTechnicalDetails(value: Boolean) {
        technicalDetailsOpen = value
        if (value && capabilities?.hardwareCapacityVersion == 1 &&
            hardwareSnapshot == null && !scanning)
            readHardwareCapacity()
    }
    fun showAdvancedConnections(value: Boolean) { advancedConnectionsOpen = value }
    fun editCustomRequest(value: String) { customRequestInput = value.take(32) }
    fun selectCustomSource(value: String) { if (value == "ECM" || value == "TCM") customSource = value }
    fun editProfileName(value: String) { profileNameInput = value.take(32) }
    fun selectProfile(id: String) {
        if (profileError != null || profileCollection.profiles.none { it.id == id }) return
        profileCollection = profileCollection.copy(activeId = id)
        draft = profileCollection.active.draft
        editingPageIndex = 0
        if (!profileStore.save(profileCollection)) profileError = "Could not save the selected vehicle profile"
    }
    fun createProfile() {
        val name = profileNameInput.trim()
        if (profileError != null || name.isEmpty() || profileCollection.profiles.size >= 8 ||
            profileCollection.profiles.any { it.name.equals(name, ignoreCase = true) }) return
        val profile = VehicleProfile(ProfileStore.newId(), name, Draft(), secondAdapterEnabled = false)
        profileCollection = profileCollection.copy(
            activeId = profile.id,
            profiles = profileCollection.profiles + profile,
        )
        draft = profile.draft
        editingPageIndex = 0
        profileNameInput = ""
        if (!profileStore.save(profileCollection)) profileError = "Could not save the new vehicle profile"
    }
    fun markScanning(value: Boolean) { scanning = value }
    fun connectionError(message: String) {
        scanning = false
        deviceMessage = message
        capabilities = null
        gaugeCandidates = emptyList()
        ownerAccess = OwnerAccess.UNKNOWN
        savedGauge = null
        diagnostics = null
        diagnosticsObservedAtElapsedMs = null
        bootIdentity = null
        runtimeIdentity = null
        hardwareSnapshot = null
        activeConfigRevision = null
        activeDocument = null
        verifiedConfigHash = null
        documentMessage = null
        documentReadFailed = false
    }
    fun connected(value: CapabilitySnapshot) {
        scanning = false
        capabilities = value
        gaugeCandidates = emptyList()
        ownerAccess = OwnerAccess.DISCOVERED
        savedGauge = null
        diagnostics = null
        diagnosticsObservedAtElapsedMs = null
        bootIdentity = null
        runtimeIdentity = null
        hardwareSnapshot = null
        activeConfigRevision = null
        activeDocument = null
        verifiedConfigHash = null
        documentMessage = null
        documentReadFailed = false
        sentDraft = null
        sentProfileId = null
        sentDigest = null
        deviceMessage = if (value.experimentalNumericConfig)
            "Gauge found. Pair this phone as owner to send a supported dashboard."
        else if (value.quickSelect)
            "Gauge identified. Built-in reading selection is available after pairing."
        else "Gauge identified. Discovery link closed; protocol ${value.protocolMajor} is read only."
    }
    fun selectionApplied(index: Int) {
        scanning = false
        savedGauge = null
        ownerAccess = OwnerAccess.AUTHENTICATED
        deviceMessage = "Gauge confirmed built-in reading ${index + 1} of 5."
    }
    fun snapshotRead(value: GaugeSavedSnapshot) {
        scanning = false
        savedGauge = value
        ownerAccess = OwnerAccess.AUTHENTICATED
        deviceMessage = "Gauge saved state read at revision ${value.revision}."
    }
    fun configApplied(value: GaugeConfigTransferClient.Applied, profileId: String, appliedDraft: Draft) {
        scanning = false
        activeConfigRevision = value.revision
        activeDocument = null
        verifiedConfigHash = null
        documentMessage = null
        documentReadFailed = false
        sentDraft = appliedDraft
        sentProfileId = profileId
        sentDigest = value.sha256
        savedGauge = null
        runtimeIdentity = value.runtime
        ownerAccess = OwnerAccess.AUTHENTICATED
        deviceMessage = "Configuration revision ${value.revision} is healthy and running on the gauge."
    }
    fun configStatusRead(value: GaugeConfigTransferClient.ActiveStatus) {
        scanning = false
        if (activeDocument?.revision != value.revision || activeDocument?.sha256 != value.sha256) {
            activeDocument = null
            verifiedConfigHash = null
        }
        if (activeDocument == null) {
            documentMessage = null
            documentReadFailed = false
        }
        if (value.revision != activeConfigRevision || value.sha256 != sentDigest) {
            sentDraft = null
            sentProfileId = null
            sentDigest = null
        }
        activeConfigRevision = value.revision.takeIf { it > 0 }
        ownerAccess = OwnerAccess.AUTHENTICATED
        deviceMessage = if (value.revision == 0L)
            "Gauge is using built-in readings. Transfer phase ${value.transferPhase}."
        else "Gauge active config revision ${value.revision}, SHA-256 ${value.sha256.take(12)}…; " +
            "transfer phase ${value.transferPhase}, last result ${value.lastResult}."
    }
    fun runtimeIdentityRead(value: GaugeConfigTransferClient.RuntimeIdentity) {
        scanning = false
        runtimeIdentity = value
        ownerAccess = OwnerAccess.AUTHENTICATED
        deviceMessage = when {
            !value.running -> "Gauge is running built-in readings; no custom configuration is active."
            value.usedPreviousGeneration ->
                "Gauge stored revision ${value.storedRevision}, but recovered with executable revision ${value.revision}, SHA-256 ${value.sha256.take(12)}…."
            value.trial ->
                "Gauge is validating configuration revision ${value.revision}, SHA-256 ${value.sha256.take(12)}…."
            else -> "Gauge stored and running configuration revision ${value.revision}, SHA-256 ${value.sha256.take(12)}…."
        }
    }
    fun activeDocumentRead(value: GaugeConfigTransferClient.ActiveDocument?) {
        scanning = false
        activeDocument = value
        ownerAccess = OwnerAccess.AUTHENTICATED
        activeConfigRevision = value?.revision ?: 0L
        verifiedConfigHash = value?.sha256 ?: "0".repeat(64)
        if (sentDigest != value?.sha256) {
            sentDraft = null
            sentProfileId = null
            sentDigest = null
        }
        deviceMessage = if (value == null) "Gauge is using built-in readings. No custom document is saved."
        else "Saved configuration revision ${value.revision} verified against SHA-256 ${value.sha256.take(12)}…"
        documentMessage = if (value == null) "No custom document is saved on the gauge."
        else "Verified saved revision ${value.revision}. Compare it with this phone draft below."
        documentReadFailed = false
    }
    fun adoptGaugeDraft() {
        val document = activeDocument ?: return
        val imported = GaugeDraftComparison.savedDraft(document, profileCollection.activeId) ?: run {
            documentMessage = "Saved settings cannot be mapped safely into this phone profile."
            documentReadFailed = true
            return
        }
        editingPageIndex = 0
        save(imported)
        documentMessage = "Saved revision ${document.revision} copied into the phone draft. The gauge was not changed."
        documentReadFailed = false
    }
    fun activeDocumentError(message: String) {
        scanning = false
        documentMessage = message
        documentReadFailed = true
        deviceMessage = message
    }
    fun diagnosticsRead(value: GaugeConfigTransferClient.Diagnostics) {
        scanning = false
        diagnostics = value
        ownerAccess = OwnerAccess.AUTHENTICATED
        diagnosticsObservedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        deviceMessage = "Protected diagnostic snapshot read. Vehicle evidence is shown below."
    }

    fun diagnosticsCurrent(nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime()): Boolean {
        val observed = diagnosticsObservedAtElapsedMs ?: return false
        return nowElapsedMs >= observed && nowElapsedMs - observed <= 30_000
    }
    fun bootIdentityRead(value: GaugeConfigTransferClient.BootIdentity) {
        scanning = false
        bootIdentity = value
        ownerAccess = OwnerAccess.AUTHENTICATED
        val pending = pendingUpdateRecovery ?: updateJournal.read()
        val recovery = pending?.let {
            reconcilePendingUpdate(it, associationStore.rememberedId(), value.elfSha256, value.otaState)
        }
        updateRecoveryResult = recovery
        if (recovery != null) updatePackageMessage = recovery.message
        if (recovery?.terminal == true) {
            updateJournal.clear()
            pendingUpdateRecovery = null
        }
        deviceMessage = recovery?.message ?: "Protected running firmware identity read."
    }
    fun updatePackageLoaded(value: DevUpdateBundle) {
        selectedUpdate = value
        val hash = value.sha256.joinToString("") { "%02x".format(it) }
        updatePackageMessage = "Development signature valid • ${value.image.size} bytes • SHA-256 ${hash.take(12)}…"
    }
    fun updateStarted() {
        updateInProgress = true
        updateMayHaveChangedGauge = false
        scanning = true
        val bundle = updateBundle()
        updateJournal.write(
            associationStore.rememberedId() ?: "unknown",
            bundle.sha256.joinToString("") { "%02x".format(it) },
            bundle.elfSha256.joinToString("") { "%02x".format(it) },
            "preparing",
        )
        pendingUpdateRecovery = updateJournal.read()
        updateRecoveryResult = UpdateRecoveryResult(UpdateRecoveryState.CHECK_REQUIRED,
            "Update started. Running identity will be checked if the operation is interrupted.", false)
        updatePackageMessage = "Connecting to gauge for signed update…"
    }
    fun updateProgress(percent: Int) {
        updateMayHaveChangedGauge = true
        if (percent == 0) {
            val bundle = updateBundle()
            updateJournal.write(
                associationStore.rememberedId() ?: "unknown",
                bundle.sha256.joinToString("") { "%02x".format(it) },
                bundle.elfSha256.joinToString("") { "%02x".format(it) },
                "transferring",
            )
            pendingUpdateRecovery = updateJournal.read()
        }
        updatePackageMessage = "Sending signed image to gauge • $percent%"
    }
    fun updateStageChanged(stage: FirmwareUpdateStage) {
        operation = when (stage) {
            FirmwareUpdateStage.PREPARING -> operation.copy(stage = OperationStage.PREPARING,
                title = "Preparing firmware update", detail = "Authenticating the gauge and opening the transfer path",
                progressPercent = null)
            FirmwareUpdateStage.TRANSFERRING -> operation.copy(stage = OperationStage.SENDING,
                title = "Sending firmware", detail = if (capabilities?.wifiBulk != null)
                    "Sending the signed image over private gauge Wi-Fi" else
                    "Sending the signed image over Bluetooth")
            FirmwareUpdateStage.VERIFYING -> operation.copy(stage = OperationStage.VERIFYING,
                title = "Verifying firmware", detail = "The gauge is checking the complete signed image",
                progressPercent = 100)
            FirmwareUpdateStage.READY_TO_ACTIVATE -> operation.copy(stage = OperationStage.VERIFYING,
                title = "Firmware verified", detail = if (debugOtaPauseBeforeActivationMs > 0)
                    "Debug activation pause active" else "Preparing the alternate image for boot",
                progressPercent = 100)
            FirmwareUpdateStage.RESTARTING -> operation.copy(stage = OperationStage.RESTARTING,
                title = "Restarting gauge", detail = "Booting the verified alternate image",
                progressPercent = 100)
            FirmwareUpdateStage.CONFIRMING -> operation.copy(stage = OperationStage.CHECKING_RUNNING,
                title = "Confirming firmware", detail = "Waiting for the gauge health check and authenticated identity",
                progressPercent = 100)
        }
        updatePackageMessage = when (stage) {
            FirmwareUpdateStage.PREPARING -> "Preparing the authenticated update path…"
            FirmwareUpdateStage.TRANSFERRING -> "Sending the signed image to the gauge…"
            FirmwareUpdateStage.VERIFYING -> "Transfer complete. Verifying the signed image on the gauge…"
            FirmwareUpdateStage.READY_TO_ACTIVATE -> if (debugOtaPauseBeforeActivationMs > 0)
                "Firmware verified. Debug activation pause active."
                else "Firmware verified. Preparing activation…"
            FirmwareUpdateStage.RESTARTING -> "Firmware activated. The gauge is restarting…"
            FirmwareUpdateStage.CONFIRMING -> "Gauge restarted. Confirming the healthy running image…"
        }
    }
    fun updateSucceeded(value: GaugeConfigTransferClient.UpdateResult) {
        updateInProgress = false
        updateMayHaveChangedGauge = false
        scanning = false
        updateJournal.clear()
        pendingUpdateRecovery = null
        updateRecoveryResult = UpdateRecoveryResult(UpdateRecoveryState.INSTALLED,
            "The new firmware is confirmed healthy.", true)
        updatePackageMessage = "Gauge confirmed new image at 0x${value.partitionAddress.toString(16)} • ELF SHA-256 ${value.elfSha256.take(12)}…"
    }
    fun updateFailed(message: String) {
        updateInProgress = false
        scanning = false
        if (updateMayHaveChangedGauge) selectedUpdate?.let { bundle ->
            updateJournal.write(
                associationStore.rememberedId() ?: "unknown",
                bundle.sha256.joinToString("") { "%02x".format(it) },
                bundle.elfSha256.joinToString("") { "%02x".format(it) },
                "needs-reconciliation",
            )
            pendingUpdateRecovery = updateJournal.read()
            updateRecoveryResult = UpdateRecoveryResult(UpdateRecoveryState.CHECK_REQUIRED,
                "The update outcome is unknown. Check installed firmware before retrying.", false)
        } else {
            updateJournal.clear()
            pendingUpdateRecovery = null
            updateRecoveryResult = null
        }
        updateMayHaveChangedGauge = false
        updatePackageMessage = message
    }
    fun installSelectedUpdate() {
        if (updateInProgress) return
        val bundle = updateBundle()
        viewModelScope.launch {
            try {
                operationCoordinator.run(OperationKind.UPDATE) { id ->
                    updateStarted()
                    operation = OperationState(id, OperationKind.UPDATE, OperationStage.CONNECTING,
                        "Installing firmware", if (capabilities?.wifiBulk != null)
                            "Preparing a private gauge Wi-Fi connection" else "Connecting to the gauge")
                    val client = GaugeConfigTransferClient(getApplication())
                    val reportProgress: (Int) -> Unit = { percent ->
                            updateProgress(percent)
                            operation = operation.copy(stage = OperationStage.SENDING,
                                detail = if (capabilities?.wifiBulk != null)
                                    "Sending firmware over private gauge Wi-Fi" else
                                    "Sending firmware over Bluetooth", progressPercent = percent)
                        }
                    val wifiVersion = capabilities?.wifiBulk
                    val result = if (wifiVersion == "experimental-softap-aead-v1" ||
                        wifiVersion == "experimental-softap-aead-v2")
                        client.installUpdateWifi(bleClient.selectedGauge(), bundle,
                            wifiVersion == "experimental-softap-aead-v2", reportProgress,
                            ::updateStageChanged, debugOtaPauseBeforeActivationMs)
                    else client.installUpdate(bleClient.selectedGauge(), bundle, reportProgress,
                        ::updateStageChanged, debugOtaPauseBeforeActivationMs)
                    updateSucceeded(result)
                    operation = OperationState(id, OperationKind.UPDATE, OperationStage.ACTIVE,
                        "Firmware installed", "The new image is healthy and running", 100, true)
                }
            } catch (_: OperationBusyException) {
                deviceMessage = "Finish the current gauge operation before installing firmware"
            } catch (error: TimeoutCancellationException) {
                updateFailed("Gauge update timed out. Reconnect and read running firmware before retrying.")
                operationFailed("Firmware update timed out", outcomeUnknown = true)
            } catch (error: CancellationException) {
                updateFailed("Update interrupted. Reconnect and read the running firmware before retrying.")
                operationFailed("Firmware update was interrupted", outcomeUnknown = true)
                throw error
            } catch (error: GaugeLinkException) {
                updateFailed("Gauge connection interrupted during update. Reconnect and read running firmware before retrying. ${error.message}")
                operationFailed("Gauge connection was interrupted", outcomeUnknown = true)
            } catch (error: Exception) {
                updateFailed(error.message ?: "Signed update did not complete")
                operationFailed(error.message ?: "Signed update did not complete")
            }
        }
    }

    fun discoverGauge() = launchGaugeOperation(OperationKind.DISCOVERY, "Finding your gauge") { id ->
        operation = OperationState(id, OperationKind.DISCOVERY, OperationStage.FINDING_GAUGE,
            "Finding your gauge", "Keep the gauge powered and nearby")
        val candidates = bleClient.scanNearbyCandidates()
        val remembered = associationStore.rememberedId()?.let { id ->
            candidates.singleOrNull { it.id == id }
        }
        when {
            remembered != null -> connectCandidate(id, remembered)
            candidates.size == 1 -> connectCandidate(id, candidates.single())
            else -> {
                gaugeCandidates = candidates
                scanning = false
                operation = OperationState(id, OperationKind.DISCOVERY, OperationStage.ACTIVE,
                    "Choose your gauge", "${candidates.size} compatible gauges are nearby", terminal = true)
            }
        }
    }

    fun selectGaugeCandidate(candidate: GaugeCandidate) =
        launchGaugeOperation(OperationKind.DISCOVERY, "Connecting to your gauge") { id ->
            connectCandidate(id, candidate)
        }

    private suspend fun connectCandidate(operationId: Long, candidate: GaugeCandidate) {
        operation = OperationState(operationId, OperationKind.DISCOVERY, OperationStage.CONNECTING,
            "Connecting to your gauge", candidate.name)
        connected(bleClient.readCandidate(candidate))
        associationStore.remember(candidate.id)
        operation = OperationState(operationId, OperationKind.DISCOVERY, OperationStage.ACTIVE,
            "Gauge found", "Ready for owner access and supported settings", terminal = true)
    }

    fun selectReadingOnGauge() = launchGaugeOperation(OperationKind.CONFIGURATION, "Changing gauge reading") { id ->
        val index = when (draft.pidId) {
            "rpm" -> 0; "speed" -> 1; "load" -> 2; "coolant" -> 3; "fuel" -> 4
            else -> error("This reading is not available in the built-in gauge set")
        }
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.SENDING,
            "Changing gauge reading", "Waiting for authenticated readback")
        selectionApplied(bleClient.selectNearby(index))
        if (capabilities?.savedStateRead == true) {
            kotlinx.coroutines.delay(350)
            snapshotRead(bleClient.readSavedSnapshot())
        }
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
            "Reading changed", "The gauge confirmed its saved selection", terminal = true)
    }

    fun rotateGauge(rotation: Int) = launchGaugeOperation(OperationKind.CONFIGURATION, "Rotating display") { id ->
        require(capabilities?.displayRotationWrite == true) { "Gauge does not offer display rotation control" }
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.SENDING,
            "Rotating display", "Waiting for saved-state confirmation")
        snapshotRead(bleClient.rotateNearby(rotation))
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
            "Display rotation saved", "The gauge confirmed ${rotation * 90}°", terminal = true)
    }

    fun readSavedGauge() = launchGaugeOperation(OperationKind.READ, "Checking gauge settings") { id ->
        snapshotRead(bleClient.readSavedSnapshot())
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Gauge settings checked", "Saved revision ${savedGauge?.revision ?: 0}", terminal = true)
    }

    fun sendNumericConfiguration() = launchGaugeOperation(OperationKind.CONFIGURATION, "Sending gauge setup") { id ->
        require((capabilities?.configurationVersion ?: 0) > 0) {
            "Gauge does not offer dashboard configuration"
        }
        val profileId = profileCollection.activeId
        val capturedDraft = draft
        val baseRevision = activeConfigRevision ?: error("Refresh gauge settings before sending")
        val baseHash = verifiedConfigHash ?: error("Refresh gauge settings before sending")
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.PREPARING,
            "Sending gauge setup", "Checking the current saved revision")
        val applied = GaugeConfigTransferClient(getApplication()).apply(
            bleClient.selectedGauge(), capturedDraft, profileId, baseRevision, baseHash,
        ) { stage -> operation = operation.copy(stage = stage, detail = operationDetail(stage)) }
        configApplied(applied, profileId, capturedDraft)
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
            "Setup running on gauge", "Revision ${applied.revision} is healthy and active", 100, true)
    }

    fun readConfiguration() = launchGaugeOperation(OperationKind.READ, "Checking running setup") { id ->
        val client = GaugeConfigTransferClient(getApplication())
        configStatusRead(client.readActive(bleClient.selectedGauge()))
        operation = OperationState(id, OperationKind.READ, OperationStage.CHECKING_RUNNING,
            "Checking running setup", "Comparing saved and running revisions")
        runtimeIdentityRead(client.readRuntimeIdentity(bleClient.selectedGauge()))
        operation = OperationState(id, OperationKind.READ,
            if (runtimeIdentity?.usedPreviousGeneration == true) OperationStage.RECOVERED else OperationStage.ACTIVE,
            if (runtimeIdentity?.usedPreviousGeneration == true) "Gauge recovered an earlier setup" else "Running setup checked",
            deviceMessage, terminal = true)
    }

    fun readConfigurationDocument() = launchGaugeOperation(OperationKind.READ, "Reading saved setup") { id ->
        activeDocumentRead(GaugeConfigTransferClient(getApplication())
            .readActiveDocument(bleClient.selectedGauge()))
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Saved setup checked", documentMessage, terminal = true)
    }

    fun readGaugeDiagnostics() = launchGaugeOperation(OperationKind.READ, "Checking vehicle faults") { id ->
        diagnosticsRead(GaugeConfigTransferClient(getApplication()).readDiagnostics(bleClient.selectedGauge()))
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Vehicle fault snapshot checked", "Fresh when read from the gauge", terminal = true)
    }

    fun readRunningFirmware() = launchGaugeOperation(OperationKind.READ, "Checking firmware") { id ->
        bootIdentityRead(GaugeConfigTransferClient(getApplication()).readBootIdentity(bleClient.selectedGauge()))
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Firmware checked", bootIdentity?.version, terminal = true)
    }

    fun readHardwareCapacity() = launchGaugeOperation(OperationKind.READ, "Checking hardware") { id ->
        require(capabilities?.hardwareCapacityVersion == 1) {
            "Gauge firmware does not offer hardware capacity polling"
        }
        hardwareSnapshot = GaugeConfigTransferClient(getApplication())
            .readHardwareSnapshot(bleClient.selectedGauge())
        ownerAccess = OwnerAccess.AUTHENTICATED
        deviceMessage = "Protected hardware capacity snapshot read."
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Hardware checked", "Live memory, flash, processor, and subsystem state", terminal = true)
    }

    fun runWifiTransportSecurityCheck() = launchGaugeOperation(OperationKind.READ,
        "Checking Wi-Fi transport security") { id ->
        try {
            require(capabilities?.wifiBulk == "experimental-softap-aead-v2") {
                "Gauge does not offer the authenticated Wi-Fi transport"
            }
            val device = bleClient.selectedGauge()
            val client = GaugeConfigTransferClient(getApplication())
            operation = OperationState(id, OperationKind.READ, OperationStage.PREPARING,
                "Checking Wi-Fi transport security", "Opening an owner-authenticated temporary session")
            wifiSecurityMessage = "Running wrong-session, wrong-key, and replay checks"
            val session = client.openWifiBulk(device)
            val result = try {
                operation = operation.copy(stage = OperationStage.VERIFYING,
                    detail = "Confirming rejected frames cannot reach a protected command")
                WifiBulkClient(getApplication(), session).use { it.securitySelfCheck() }
            } finally {
                withTimeoutOrNull(8_000) { runCatching { client.closeWifiBulk(device) } }
            }
            check(result.passed) { "Gauge did not reject every negative Wi-Fi transport probe" }
            ownerAccess = OwnerAccess.AUTHENTICATED
            wifiSecurityMessage = "Passed: wrong session, wrong key, and replay were rejected"
            operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
                "Wi-Fi security check passed", wifiSecurityMessage, terminal = true)
        } catch (error: Exception) {
            wifiSecurityMessage = "Failed: ${error.message ?: "security check did not complete"}"
            throw error
        }
    }

    private fun launchGaugeOperation(kind: OperationKind, title: String, block: suspend (Long) -> Unit) {
        viewModelScope.launch {
            try {
                operationCoordinator.run(kind) { id ->
                    scanning = true
                    operation = OperationState(id, kind, OperationStage.CONNECTING, title)
                    block(id)
                    scanning = false
                }
            } catch (_: OperationBusyException) {
                deviceMessage = "Finish the current gauge operation before starting another"
            } catch (error: CancellationException) {
                scanning = false
                operationFailed("$title was interrupted", outcomeUnknown = kind != OperationKind.READ)
                throw error
            } catch (error: Exception) {
                scanning = false
                val message = error.message ?: "$title did not complete"
                deviceMessage = message
                operationFailed(message, outcomeUnknown = kind == OperationKind.CONFIGURATION)
            }
        }
    }

    private fun operationFailed(message: String, outcomeUnknown: Boolean = false) {
        operation = operation.copy(
            stage = if (outcomeUnknown) OperationStage.OUTCOME_UNKNOWN else OperationStage.FAILED,
            detail = message,
            terminal = true,
        )
    }

    private fun operationDetail(stage: OperationStage): String = when (stage) {
        OperationStage.PREPARING -> "Checking the current saved revision"
        OperationStage.SENDING -> "Sending the exact reviewed pages and alert"
        OperationStage.VERIFYING -> "Verifying the complete setup"
        OperationStage.SAVED -> "Setup saved; waiting for restart"
        OperationStage.RESTARTING -> "Gauge is restarting"
        OperationStage.CHECKING_RUNNING -> "Checking that the new setup is healthy and running"
        else -> stage.name.lowercase().replace('_', ' ')
    }
    fun updatePackageError(message: String) {
        selectedUpdate = null
        updatePackageMessage = message
    }

    fun checkHostedFirmware() {
        if (hostedUpdateBusy) return
        val caps = capabilities ?: run {
            hostedUpdateMessage = "Find the gauge before checking firmware compatibility"
            return
        }
        viewModelScope.launch {
            hostedUpdateBusy = true
            hostedUpdate = null
            try {
                operationCoordinator.run(OperationKind.READ) { id ->
                    operation = OperationState(id, OperationKind.READ, OperationStage.CONNECTING,
                        "Checking for firmware", "Reading the installed gauge version")
                    val running = GaugeConfigTransferClient(getApplication())
                        .readBootIdentity(bleClient.selectedGauge())
                    bootIdentityRead(running)
                    operation = operation.copy(stage = OperationStage.PREPARING,
                        detail = "Checking signed GitHub releases")
                    val update = GitHubFirmwareSource(getApplication()).check(caps)
                    if (isFirmwareNewer(update.release.version, running.version)) {
                        hostedUpdate = update
                        hostedUpdateMessage =
                            "Compatible development firmware ${update.release.version} is available"
                    } else {
                        hostedUpdateMessage = "Installed ${running.version} is up to date"
                    }
                    operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
                        "Firmware check complete", hostedUpdateMessage, terminal = true)
                }
            } catch (_: OperationBusyException) {
                hostedUpdateMessage = "Finish the current gauge operation before checking for firmware"
            } catch (error: CancellationException) {
                hostedUpdateMessage = "Hosted update check was interrupted"
                throw error
            } catch (error: Exception) {
                hostedUpdateMessage = error.message ?: "Could not check hosted firmware"
            } finally {
                hostedUpdateBusy = false
            }
        }
    }

    fun downloadHostedFirmware() {
        if (hostedUpdateBusy) return
        val available = hostedUpdate ?: return
        viewModelScope.launch {
            hostedUpdateBusy = true
            hostedUpdateMessage = "Downloading and verifying ${available.release.version}"
            try {
                val downloaded = GitHubFirmwareSource(getApplication()).download(available)
                hostedUpdate = downloaded
                selectedUpdate = requireNotNull(downloaded.bundle)
                updatePackageMessage = "GitHub development firmware ${downloaded.release.version} verified and ready"
                hostedUpdateMessage = "Download verified. Review and install when the gauge has stable power."
            } catch (error: CancellationException) {
                hostedUpdateMessage = "Firmware download was interrupted"
                throw error
            } catch (error: Exception) {
                hostedUpdateMessage = error.message ?: "Hosted firmware download failed"
            } finally {
                hostedUpdateBusy = false
            }
        }
    }
    fun selectionError(message: String) {
        scanning = false
        deviceMessage = message
    }
    fun snapshotError(message: String) {
        scanning = false
        savedGauge = null
        deviceMessage = message
    }
    fun selectPage(index: Int) {
        if (index !in draft.pages.indices) return
        editingPageIndex = index
        val page = draft.pages[index]
        save(draft.copy(pidId = page.pidIds[0], layout = page.layout, source = "ECM"))
    }

    fun addPage() {
        if (draft.pages.size >= 8) return
        val used = draft.pages.map { it.id }.toSet()
        val sequence = (1..99).first { "page.custom.$it" !in used }
        val page = GaugePageDraft("page.custom.$sequence", "ENGINE RPM", GaugeLayout.Numeric,
            listOf("rpm"))
        editingPageIndex = draft.pages.size
        save(draft.copy(pidId = "rpm", layout = GaugeLayout.Numeric, source = "ECM",
            pages = draft.pages + page))
    }

    fun removePage(index: Int) {
        if (draft.pages.size <= 1 || index !in draft.pages.indices) return
        val pages = draft.pages.toMutableList().also { it.removeAt(index) }
        editingPageIndex = editingPageIndex.coerceAtMost(pages.lastIndex)
        val selected = pages[editingPageIndex]
        save(draft.copy(pidId = selected.pidIds[0], layout = selected.layout, pages = pages))
    }

    fun movePage(index: Int, delta: Int) {
        val destination = index + delta
        if (index !in draft.pages.indices || destination !in draft.pages.indices) return
        val pages = draft.pages.toMutableList()
        val page = pages.removeAt(index)
        pages.add(destination, page)
        editingPageIndex = destination
        save(draft.copy(pages = pages))
    }

    fun selectPid(pid: PidExample) {
        if (editingPageIndex !in draft.pages.indices) return
        val pages = draft.pages.toMutableList()
        val current = pages[editingPageIndex]
        val ids = if (current.layout == GaugeLayout.Dual) {
            val second = current.pidIds.getOrNull(1)?.takeIf { it != pid.id }
                ?: ConfigurationProjector.supportedPidIds.first { it != pid.id }
            listOf(pid.id, second)
        } else listOf(pid.id)
        pages[editingPageIndex] = current.copy(name = pid.gaugeLabel, pidIds = ids)
        save(draft.copy(pidId = pid.id, source = pid.source, pages = pages))
    }

    fun selectSecondaryPid(pid: PidExample) {
        if (editingPageIndex !in draft.pages.indices || pid.id !in ConfigurationProjector.supportedPidIds) return
        val pages = draft.pages.toMutableList()
        val current = pages[editingPageIndex]
        if (current.layout != GaugeLayout.Dual || current.pidIds.first() == pid.id) return
        pages[editingPageIndex] = current.copy(pidIds = listOf(current.pidIds.first(), pid.id))
        save(draft.copy(pages = pages))
    }

    fun selectLayout(layout: GaugeLayout) {
        if (editingPageIndex !in draft.pages.indices) return
        val pages = draft.pages.toMutableList()
        val current = pages[editingPageIndex]
        val ids = if (layout == GaugeLayout.Dual) {
            val secondary = current.pidIds.getOrNull(1)
                ?: ConfigurationProjector.supportedPidIds.first { it != current.pidIds.first() }
            listOf(current.pidIds.first(), secondary)
        } else listOf(current.pidIds.first())
        pages[editingPageIndex] = current.copy(layout = layout, pidIds = ids)
        save(draft.copy(layout = layout, pages = pages))
    }
    fun setWarning(value: Int) = save(draft.copy(warning = value))
    fun setCritical(value: Int) = save(draft.copy(critical = value))
    fun setHysteresis(value: Int) = save(draft.copy(hysteresis = value.coerceIn(0, 20)))
    fun setTriggerDwell(value: Int) = save(draft.copy(triggerDwellMs = value.coerceIn(0, 60000)))
    fun setClearDwell(value: Int) = save(draft.copy(clearDwellMs = value.coerceIn(0, 60000)))
    fun setSource(value: String) = save(draft.copy(source = value))
    fun setSecondAdapterEnabled(value: Boolean) {
        if (profileError != null) return
        val active = profileCollection.active
        if (!value && active.draft.source == "TCM") {
            profileError = "Move the selected transmission reading to the primary adapter before disabling the second adapter"
            return
        }
        profileCollection = profileCollection.copy(profiles = profileCollection.profiles.map { profile ->
            if (profile.id == active.id) profile.copy(secondAdapterEnabled = value) else profile
        })
        if (!profileStore.save(profileCollection)) profileError = "Could not save advanced connection settings"
    }

    private fun save(value: Draft) {
        if (profileError != null) return
        draft = value
        profileCollection = profileCollection.copy(profiles = profileCollection.profiles.map { profile ->
            if (profile.id == profileCollection.activeId) profile.copy(draft = value) else profile
        })
        if (!profileStore.save(profileCollection)) profileError = "Could not save changes on this phone"
    }
}
