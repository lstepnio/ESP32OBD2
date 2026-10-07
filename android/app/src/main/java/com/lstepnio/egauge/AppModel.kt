package com.lstepnio.egauge

import android.app.Application
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import com.lstepnio.egauge.connection.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.lstepnio.egauge.ui.state.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout

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
    PidExample("tcmtemp", "TCM temp (experimental)", "TCM", "22 04FE", "°C", "45", "Experimental interpretation; sensor meaning unvalidated", "Transmission", "TCM TEMP (TEST)"),
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

enum class AlertDirection { Above, Below }

data class GaugeAlertDraft(
    val id: String,
    val pidId: String,
    val direction: AlertDirection = AlertDirection.Above,
    val warning: Int,
    val critical: Int,
    val hysteresis: Int = 3,
    val triggerDwellMs: Int = 1000,
    val clearDwellMs: Int = 2000,
    val priority: Int = 8,
)

fun readingRange(id: String): IntRange = when (id) {
    "rpm" -> 0..16383 // Largest whole-number threshold within the decoder's 16383.75 maximum.
    "coolant" -> -40..215
    "tcmtemp" -> 0..180
    "speed" -> 0..255
    "load", "fuel" -> 0..100
    else -> 0..100
}

fun defaultAlert(pidId: String = "coolant"): GaugeAlertDraft {
    val range = readingRange(pidId)
    val span = range.last - range.first
    val warning = when (pidId) {
        "coolant" -> 105
        "rpm" -> 4000
        "speed" -> 120
        "load", "fuel" -> 80
        else -> range.first + (span * 3 / 4)
    }.coerceIn(range)
    val critical = when (pidId) {
        "coolant" -> 115
        "rpm" -> 5000
        "speed" -> 140
        "load", "fuel" -> 90
        else -> warning + (span / 10).coerceAtLeast(1)
    }.coerceIn(range)
    return GaugeAlertDraft("alert.$pidId", pidId, AlertDirection.Above, warning,
        if (critical > warning) critical else warning - 1, hysteresis = (span / 50).coerceIn(1, 20))
}

fun defaultGaugePages(primary: String = "rpm", layout: GaugeLayout = GaugeLayout.Numeric): List<GaugePageDraft> {
    val ordered = listOf(primary, "rpm", "coolant", "speed").distinct().take(3)
    return ordered.mapIndexed { index, pidId ->
        val definition = demoCatalog.first { it.id == pidId }
        GaugePageDraft("page.${index + 1}.${pidId}", definition.gaugeLabel, if (index == 0) layout else GaugeLayout.Numeric,
            listOf(pidId))
    }
}

enum class OwnerAccess { UNKNOWN, DISCOVERED, AUTHENTICATED }
enum class PairingWindowState { CLOSED, READY, CODE_DISPLAYED, OWNER_PRESENT }
enum class PairingProgress { WAITING_FOR_ANDROID, CHECKING_GAUGE_ACCESS }
data class GaugePairingWindow(val state: PairingWindowState, val secondsRemaining: Int)

data class Draft(
    val pidId: String = "rpm",
    val layout: GaugeLayout = GaugeLayout.Numeric,
    val source: String = "ECM",
    val pages: List<GaugePageDraft> = defaultGaugePages(pidId, layout),
    val alerts: List<GaugeAlertDraft> = listOf(defaultAlert()),
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
    val displaySettingsVersion: Int = 0,
    val ota: Boolean,
    val wifiBulk: String?,
    val hardwareCapacityVersion: Int?,
    val configurationVersion: Int = 0,
    val adapterRegistryVersion: Int = 0,
    val maxPages: Int = 0,
    val supportedRenderers: Set<GaugeLayout> = emptySet(),
)

data class GaugeSavedSnapshot(val readingIndex: Int, val rotation: Int, val revision: Long)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val bleClient = BleCapabilityClient(application)
    private val operationCoordinator = OperationCoordinator()
    var connection by mutableStateOf(ConnectionState())
        private set
    private var currentGaugeId: String? = null
    private var rediscoverGauge = true
    private val foregroundConnection by lazy {
        ForegroundConnectionController(viewModelScope, operationCoordinator, ::automaticConnectionAttempt)
    }
    private val automaticUpdateHold = AutomaticUpdateHoldStore(application)
    private var connectionForeground = false
    private var automaticUpdateJob: Job? = null
    private var nextAutomaticUpdateCheckAtElapsedMs = 0L

    fun setConnectionForeground(value: Boolean) {
        connectionForeground = value
        foregroundConnection.setForeground(value)
        if (!value) {
            automaticUpdateJob?.cancel()
            nextAutomaticUpdateCheckAtElapsedMs = 0L
        }
    }
    fun retryConnection() = foregroundConnection.retrySoon()

    @SuppressLint("MissingPermission")
    private suspend fun automaticConnectionAttempt(): Long {
        val app = getApplication<Application>()
        val permissions = if (Build.VERSION.SDK_INT >= 31)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.any { app.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            connection = ConnectionState(ConnectionPhase.PermissionRequired)
            rediscoverGauge = true
            ownerAccess = OwnerAccess.UNKNOWN
            return 15_000
        }
        if (app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled != true) {
            connection = ConnectionState(ConnectionPhase.BluetoothOff)
            rediscoverGauge = true
            ownerAccess = OwnerAccess.UNKNOWN
            return 5_000
        }
        try {
            return withTimeout(35_000) {
                if (rediscoverGauge || capabilities == null || currentGaugeId == null) {
                    connection = connection.copy(phase = ConnectionPhase.Searching, checkedAtElapsedMs = null)
                    val candidates = bleClient.scanNearbyCandidates()
                    val id = automaticCandidateId(candidates.map { it.id }, rememberedGaugeId)
                    if (id == null) {
                        gaugeCandidates = candidates
                        connection = ConnectionState(if (rememberedGaugeId == null)
                            ConnectionPhase.ChooseGauge else ConnectionPhase.Retrying,
                            attempts = connection.attempts + 1)
                        return@withTimeout reconnectDelayMs(connection.attempts)
                    }
                    connection = connection.copy(phase = ConnectionPhase.Checking)
                    connectDiscoveredCandidate(candidates.single { it.id == id })
                }
                val device = bleClient.selectedGauge()
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    ownerAccess = OwnerAccess.DISCOVERED
                    connection = ConnectionState(ConnectionPhase.PairRequired)
                    return@withTimeout 5_000L
                }
                connection = connection.copy(phase = ConnectionPhase.Checking)
                val client = GaugeConfigTransferClient(app)
                if (pendingUpdateRecovery != null || updateJournal.read() != null) {
                    bootIdentityRead(client.readBootIdentity(device))
                    if (updateRecoveryResult?.state == UpdateRecoveryState.INSTALLED && operation.kind == OperationKind.UPDATE) {
                        operation = operation.copy(stage = OperationStage.ACTIVE, title = "Update confirmed on gauge",
                            detail = "The expected firmware is healthy and running", progressPercent = 100, terminal = true)
                        updatePreparation = "current"
                    }
                }
                // Public discovery never establishes ownership. Only read an already bonded target.
                if (capabilities?.experimentalNumericConfig == true) {
                    val runtime = client.readRuntimeIdentity(device)
                    if (activeDocument == null || activeDocument?.revision != runtime.storedRevision ||
                        activeDocument?.sha256 != runtime.sha256 || ownerAccess != OwnerAccess.AUTHENTICATED)
                        activeDocumentRead(client.readActiveDocument(device))
                    runtimeIdentityRead(runtime)
                    if (configurationNeedsReview) configurationRecoveryRead = true
                } else if (capabilities?.savedStateRead == true) {
                    snapshotRead(bleClient.readSavedSnapshot())
                } else {
                    connection = ConnectionState(ConnectionPhase.Unavailable)
                    return@withTimeout 30_000L
                }
                connectionChecked()
                rediscoverGauge = false
                maybeCheckForFirmware(client, device)
                // Clear transient read failures only. Never erase a send/update recovery outcome.
                if (operation.terminal && operation.kind in setOf(OperationKind.READ, OperationKind.DISCOVERY) &&
                    operation.stage != OperationStage.RECOVERED) operation = OperationState.Idle
                20_000L
            }
        } catch (error: TimeoutCancellationException) {
            return connectionRetry("Gauge connection check timed out")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return connectionRetry(error.message ?: "Could not check the gauge")
        }
    }

    /** Discovery stays a foreground concern. GitHub work is quiet and never starts an OTA. */
    @SuppressLint("MissingPermission")
    private suspend fun maybeCheckForFirmware(client: GaugeConfigTransferClient, device: BluetoothDevice) {
        val caps = capabilities ?: return
        val gaugeId = rememberedGaugeId ?: return
        if (!connectionForeground || ownerAccess != OwnerAccess.AUTHENTICATED ||
            caps.board != "ESP32-S3-Touch-LCD-1.28" || !caps.experimentalNumericConfig ||
            updateInProgress || hostedUpdateBusy || automaticUpdateJob?.isActive == true ||
            updateRecoveryResult?.state !in setOf(null, UpdateRecoveryState.INSTALLED) ||
            (selectedUpdate != null && hostedUpdate == null)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < nextAutomaticUpdateCheckAtElapsedMs) return
        nextAutomaticUpdateCheckAtElapsedMs = now + 15 * 60_000L
        val running = try {
            client.readBootIdentity(device)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        bootIdentity = running
        if (running.otaState != 2) {
            updatePreparation = "held"
            hostedUpdateMessage = "Your gauge is still checking its current update. Check its installed version before trying again."
            return
        }
        automaticUpdateJob = viewModelScope.launch {
            try {
                val source = GitHubFirmwareSource(getApplication())
                val candidate = source.check(caps)
                currentCoroutineContext().ensureActive()
                if (!connectionForeground || rememberedGaugeId != gaugeId || updateInProgress) return@launch
                when (automaticUpdateDecision(running.version, running.otaState,
                    candidate.release.version, candidate.release.bundleSha256,
                    automaticUpdateHold.digestFor(gaugeId),
                    updateRecoveryResult?.state !in setOf(null, UpdateRecoveryState.INSTALLED))) {
                    AutomaticUpdateDecision.CURRENT -> {
                        hostedUpdate = null
                        selectedUpdate = null
                        updatePreparation = "current"
                    }
                    AutomaticUpdateDecision.HELD, AutomaticUpdateDecision.WAIT_FOR_GAUGE -> {
                        hostedUpdate = null
                        selectedUpdate = null
                        updatePreparation = "held"
                        hostedUpdateMessage = "This update did not finish last time. Check the installed version before trying again."
                    }
                    AutomaticUpdateDecision.READY -> {
                        if (hostedUpdate?.release?.bundleSha256 != candidate.release.bundleSha256 || selectedUpdate == null) {
                            hostedUpdate = null
                            selectedUpdate = null
                            val downloaded = source.download(candidate)
                            currentCoroutineContext().ensureActive()
                            if (!connectionForeground || rememberedGaugeId != gaugeId || updateInProgress) return@launch
                            // The source verifies the signed catalog and complete bundle before either becomes visible.
                            hostedUpdate = downloaded
                            selectedUpdate = requireNotNull(downloaded.bundle)
                            updatePackageMessage = "Signed development update ${downloaded.release.version} is ready"
                        }
                        updatePreparation = "ready"
                    }
                }
                nextAutomaticUpdateCheckAtElapsedMs = android.os.SystemClock.elapsedRealtime() + 6 * 60 * 60_000L
            } catch (cancelled: CancellationException) {
                nextAutomaticUpdateCheckAtElapsedMs = 0L
                throw cancelled
            } catch (_: Exception) {
                // A background network failure does not change the gauge's connection or show a false update.
                nextAutomaticUpdateCheckAtElapsedMs = android.os.SystemClock.elapsedRealtime() + 15 * 60_000L
            }
        }
    }

    private fun connectionRetry(message: String): Long {
        rediscoverGauge = true
        ownerAccess = OwnerAccess.UNKNOWN
        connection = ConnectionState(ConnectionPhase.Retrying, attempts = connection.attempts + 1, detail = message)
        return reconnectDelayMs(connection.attempts)
    }

    private fun updatePairingWindow(value: GaugePairingWindow?) {
        pairingWindow = value
        pairingWindowReadAtElapsedMs = if (value == null) 0L else android.os.SystemClock.elapsedRealtime()
    }

    fun pairingWindowForUi(nowElapsedMs: Long): GaugePairingWindow? {
        val window = pairingWindow ?: return null
        val elapsedSeconds = if (pairingWindowReadAtElapsedMs == 0L) 0 else
            ((nowElapsedMs - pairingWindowReadAtElapsedMs).coerceAtLeast(0) / 1_000).toInt()
        val remaining = (window.secondsRemaining - elapsedSeconds).coerceAtLeast(0)
        val state = if (remaining == 0 && window.state in setOf(
                PairingWindowState.READY, PairingWindowState.CODE_DISPLAYED)) PairingWindowState.CLOSED
            else window.state
        return window.copy(state = state, secondsRemaining = remaining)
    }

    @SuppressLint("MissingPermission")
    fun androidBondedForUi(): Boolean = try {
        bleClient.selectedGauge().bondState == BluetoothDevice.BOND_BONDED
    } catch (_: IllegalStateException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun connectionChecked() {
        connection = ConnectionState(ConnectionPhase.Ready, android.os.SystemClock.elapsedRealtime())
    }

    private suspend fun connectDiscoveredCandidate(candidate: GaugeCandidate) {
        val sameGauge = currentGaugeId == candidate.id
        val value = bleClient.readCandidate(candidate)
        connected(value, preserveSent = sameGauge)
        currentGaugeId = candidate.id
        associationStore.remember(candidate.id)
        rediscoverGauge = false
    }
    private val profileStore = ProfileStore(application)
    private var debugOtaPauseBeforeActivationMs = 0L

    fun setDebugOtaPauseBeforeActivationMs(value: Long) {
        val debuggable = getApplication<Application>().applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        debugOtaPauseBeforeActivationMs = if (debuggable) value.coerceIn(0, 60_000) else 0
    }
    private val presentationStore = PresentationPreferenceStore(application)
    var presentationPreferences by mutableStateOf(presentationStore.read())
        private set
    var presentationError by mutableStateOf<String?>(null)
        private set
    private data class PendingConfiguration(val gaugeId: String?, val profileId: String,
        val draft: Draft, val revision: Long, val bytes: ByteArray)
    private var pendingConfiguration: PendingConfiguration? = null
    var configurationNeedsReview by mutableStateOf(false)
        private set
    var configurationRecoveryRead by mutableStateOf(false)
        private set
    var lastConfigurationOutcome by mutableStateOf<OperationState?>(null)
        private set
    var updatePreparation by mutableStateOf("idle")
        private set
    val expectedSentDigest: String? get() = sentDigest
    val rememberedGaugeId: String? get() = associationStore.rememberedId()

    fun setAdvancedTools(value: Boolean) = savePresentation(presentationPreferences.copy(advanced = value))
    fun setDynamicColor(value: Boolean) = savePresentation(presentationPreferences.copy(dynamicColor = value))
    fun renameGauge(value: String) {
        val name = value.trim().take(32)
        if (name.isNotEmpty()) savePresentation(presentationPreferences.copy(gaugeName = name))
    }
    private fun savePresentation(value: PresentationPreferences) {
        if (presentationStore.write(value)) { presentationPreferences = value; presentationError = null }
        else presentationError = "Could not save appearance settings. Try again."
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
    var pairingWindow by mutableStateOf<GaugePairingWindow?>(null)
        private set
    private var pairingWindowReadAtElapsedMs = 0L
    var savedGauge by mutableStateOf<GaugeSavedSnapshot?>(null)
    var displaySettings by mutableStateOf<GaugeConfigTransferClient.DisplaySettings?>(null)
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
        get() = activeDocument?.let { GaugeDraftComparison.from(it, profileCollection.activeId, draft, profileCollection.active.primaryAdapter) }
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
            addAll(ConfigurationProjector.blockers(draft))
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
    var operation by mutableStateOf(OperationState.Idle)
        private set

    fun navigate(value: Destination) { destination = value }
    fun editProfileName(value: String) { profileNameInput = value.take(32) }
    var adapterCandidates by mutableStateOf<List<AdapterCandidate>>(emptyList())
        private set
    var adapterSourceStatus by mutableStateOf<AdapterSourceStatus?>(null)
        private set
    var adapterStatusCheckedAt by mutableStateOf<Long?>(null)
        private set
    fun checkVehicleAdapter() = launchGaugeOperation(OperationKind.READ, "Checking vehicle adapter") { id ->
        require(capabilities?.adapterRegistryVersion == 1) { "Update the gauge before checking its adapter" }
        val status = GaugeConfigTransferClient(getApplication()).readAdapterStatus(bleClient.selectedGauge())
        adapterSourceStatus = status
        adapterStatusCheckedAt = android.os.SystemClock.elapsedRealtime()
        adapterMessage = if (status.vehicleId != profileCollection.activeId)
            "The gauge is using a different car setup. Send this car's setup before checking readings."
        else status.message
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Adapter checked", adapterMessage, terminal = true)
    }

    var adapterMessage by mutableStateOf<String?>(null)
        private set

    fun findVehicleAdapters() = launchGaugeOperation(OperationKind.READ, "Finding vehicle adapter") { id ->
        require(capabilities?.adapterRegistryVersion == 1) { "Update the gauge before setting up an adapter" }
        adapterCandidates = emptyList()
        val found = GaugeConfigTransferClient(getApplication()).findAdapters(bleClient.selectedGauge())
        adapterCandidates = found
        adapterMessage = if (found.isEmpty()) "No adapter found. Plug it into the vehicle and try again."
            else "Choose the adapter plugged into this vehicle."
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Adapter search finished", adapterMessage, terminal = true)
    }

    fun chooseVehicleAdapter(value: AdapterBinding?) {
        if (profileError != null) return
        val updated = profileCollection.copy(profiles = profileCollection.profiles.map {
            if (it.id == profileCollection.activeId) it.copy(primaryAdapter = value) else it
        })
        if (!profileStore.save(updated)) { profileError = "Could not save the adapter selection"; return }
        profileCollection = updated
        sentDraft = null
        sentProfileId = null
        sentDigest = null
        diagnostics = null
        diagnosticsObservedAtElapsedMs = null
        adapterSourceStatus = null
        adapterStatusCheckedAt = null
        activeVehicleSessionId = null
        adapterMessage = "Saved on this phone. Send setup to use it on the gauge."
    }

    fun selectProfile(id: String) {
        if (profileError != null || profileCollection.profiles.none { it.id == id }) return
        diagnostics = null
        diagnosticsObservedAtElapsedMs = null
        adapterCandidates = emptyList()
        adapterMessage = null
        adapterSourceStatus = null
        adapterStatusCheckedAt = null
        activeVehicleSessionId = null
        profileCollection = profileCollection.copy(activeId = id)
        draft = profileCollection.active.draft
        editingPageIndex = 0
        if (!profileStore.save(profileCollection)) profileError = "Could not save the selected vehicle profile"
    }
    fun createExperimentalTcmProfile() {
        if (!BuildConfig.DEBUG || profileError != null) return
        profileCollection.profiles.firstOrNull { it.name == "JSS TCM temperature test" }?.let {
            selectProfile(it.id); return
        }
        if (profileCollection.profiles.size >= 8) { deviceMessage = "Remove an unused profile before adding the TCM test"; return }
        val testDraft = Draft(pidId = "tcmtemp", source = "TCM", pages = listOf(
            GaugePageDraft("page.tcm.temperature", "TCM temp (test)", GaugeLayout.Numeric, listOf("tcmtemp"))), alerts = emptyList())
        val profile = VehicleProfile(ProfileStore.newId(), "JSS TCM temperature test", testDraft,
            profileCollection.active.primaryAdapter)
        val updated = profileCollection.copy(activeId = profile.id, profiles = profileCollection.profiles + profile)
        if (!profileStore.save(updated)) { profileError = "Could not save the TCM test profile"; return }
        profileCollection = updated
        draft = testDraft
        editingPageIndex = 0
        deviceMessage = "Experimental TCM temperature profile ready. Keep the adapter in the TCM connector and send setup."
    }

    fun createProfile() {
        val name = profileNameInput.trim()
        if (profileError != null || name.isEmpty() || profileCollection.profiles.size >= 8 ||
            profileCollection.profiles.any { it.name.equals(name, ignoreCase = true) }) return
        val profile = VehicleProfile(ProfileStore.newId(), name, Draft())
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
        presentationError = message
        scanning = false
        deviceMessage = message
        capabilities = null
        gaugeCandidates = emptyList()
        ownerAccess = OwnerAccess.UNKNOWN
        savedGauge = null
        displaySettings = null
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
    fun connected(value: CapabilitySnapshot, preserveSent: Boolean = false) {
        configurationRecoveryRead = false
        scanning = false
        capabilities = value
        gaugeCandidates = emptyList()
        ownerAccess = OwnerAccess.DISCOVERED
        if (!preserveSent) {
            savedGauge = null
            displaySettings = null
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
        }
        deviceMessage = if (value.experimentalNumericConfig)
            "Gauge found. Pair this phone as owner to send a supported dashboard."
        else if (value.quickSelect)
            "Gauge identified. Built-in reading selection is available after pairing."
        else "Gauge identified. Discovery link closed; protocol ${value.protocolMajor} is read only."
    }
    fun snapshotRead(value: GaugeSavedSnapshot) {
        scanning = false
        savedGauge = value
        ownerAccess = OwnerAccess.AUTHENTICATED
        deviceMessage = "Gauge saved state read at revision ${value.revision}."
    }
    fun configApplied(value: GaugeConfigTransferClient.Applied, profileId: String, appliedDraft: Draft) {
        pendingConfiguration = null
        configurationNeedsReview = false
        configurationRecoveryRead = false
        lastConfigurationOutcome = null
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
        connectionChecked()
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
        connectionChecked()
        val pending = pendingConfiguration
        val document = activeDocument
        if (pending != null && pending.gaugeId == rememberedGaugeId && document != null &&
            document.revision == pending.revision && document.sha256 == verifiedConfigHash &&
            matchesRunningPayload(pending.bytes, pending.revision, document.sha256, value)) {
            configApplied(GaugeConfigTransferClient.Applied(pending.revision, document.sha256, value),
                pending.profileId, pending.draft)
            operation = operation.copy(stage = OperationStage.ACTIVE, title = "Setup confirmed on gauge",
                detail = "The exact sent settings are healthy and running", progressPercent = 100, terminal = true)
        }
        recognizeRunningPhoneSettings()
        deviceMessage = when {
            !value.running -> "Gauge is running built-in readings; no custom configuration is active."
            value.usedPreviousGeneration ->
                "Gauge stored revision ${value.storedRevision}, but recovered with executable revision ${value.revision}, SHA-256 ${value.sha256.take(12)}…."
            value.trial ->
                "Gauge is validating configuration revision ${value.revision}, SHA-256 ${value.sha256.take(12)}…."
            else -> "Gauge stored and running configuration revision ${value.revision}, SHA-256 ${value.sha256.take(12)}…."
        }
    }
    /** Reopening the app need not prompt another send when every expected byte is already running. */
    private fun recognizeRunningPhoneSettings() {
        if (configurationNeedsReview || profileError != null) return
        val document = activeDocument ?: return
        if (document.sha256 != verifiedConfigHash) return
        val expected = runCatching {
            val template = getApplication<Application>().assets.open("numeric_config_template.json")
                .bufferedReader().use { it.readText() }
            ConfigurationProjector.project(template, draft, profileCollection.activeId, document.revision - 1,
                profileCollection.active.primaryAdapter, org.json.JSONObject(document.json).getInt("schemaVersion")).second
        }.getOrNull()
        if (matchesRunningPayload(expected, document.revision, document.sha256, runtimeIdentity)) {
            sentDraft = draft
            sentProfileId = profileCollection.activeId
            sentDigest = document.sha256
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
        automaticUpdateJob?.cancel()
        if (presentationError == updatePackageMessage) presentationError = null
        hostedUpdate = null
        selectedUpdate = value
        updatePreparation = "ready"
        hostedUpdateMessage = "Signed development package ready to install"
        val hash = value.sha256.joinToString("") { "%02x".format(it) }
        updatePackageMessage = "Development signature valid • ${value.image.size} bytes • SHA-256 ${hash.take(12)}…"
    }
    fun updateStarted() {
        updateInProgress = true
        updateMayHaveChangedGauge = false
        scanning = true
        val bundle = updateBundle()
        val hosted = hostedUpdate
        val gaugeId = rememberedGaugeId
        if (hosted?.bundle?.sha256?.contentEquals(bundle.sha256) == true && gaugeId != null)
            automaticUpdateHold.hold(gaugeId, hosted.release.bundleSha256)
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
            FirmwareUpdateStage.CONNECTING_WIFI -> operation.copy(stage = OperationStage.PREPARING,
                title = "Joining gauge Wi-Fi", detail = "Android is connecting to the gauge's temporary network",
                progressPercent = null)
            FirmwareUpdateStage.PREPARING_FLASH -> operation.copy(stage = OperationStage.PREPARING,
                title = "Preparing gauge flash", detail = "Sending the signed image details and clearing space",
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
            FirmwareUpdateStage.CONNECTING_WIFI -> "Joining the temporary gauge Wi-Fi network…"
            FirmwareUpdateStage.PREPARING_FLASH -> "Preparing the gauge to receive the image…"
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
        // The rebooted image may advertise new controls. Re-read its public capabilities
        // after the update lease closes instead of keeping the previous image's snapshot.
        capabilities = null
        displaySettings = null
        rediscoverGauge = true
        foregroundConnection.retrySoon()
        bootIdentity = value.running
        hostedUpdate = null
        selectedUpdate = null
        updatePreparation = "current"
        rememberedGaugeId?.let(automaticUpdateHold::clear)
        nextAutomaticUpdateCheckAtElapsedMs = android.os.SystemClock.elapsedRealtime() + 6 * 60 * 60_000L
        updateJournal.clear()
        pendingUpdateRecovery = null
        updateRecoveryResult = UpdateRecoveryResult(UpdateRecoveryState.INSTALLED,
            "The new firmware is confirmed healthy.", true)
        updatePackageMessage = "Gauge confirmed new image at 0x${value.running.partitionAddress.toString(16)} • ELF SHA-256 ${value.running.elfSha256.take(12)}…"
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
        if (hostedUpdate != null) {
            hostedUpdate = null
            selectedUpdate = null
            updatePreparation = "held"
        }
        updatePackageMessage = message
    }
    fun installSelectedUpdate() {
        if (updateInProgress) return
        val bundle = updateBundle()
        viewModelScope.launch {
            try {
                foregroundConnection.runUserOperation(OperationKind.UPDATE) { id ->
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
        val selected = automaticCandidateId(candidates.map { it.id }, rememberedGaugeId)
        when {
            selected != null -> connectCandidate(id, candidates.single { it.id == selected })
            else -> {
                gaugeCandidates = candidates
                connection = ConnectionState(ConnectionPhase.ChooseGauge)
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
        connectDiscoveredCandidate(candidate)
        updatePairingWindow(try { bleClient.readPairingWindowStatus() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null })
        connection = ConnectionState(ConnectionPhase.PairRequired)
        operation = OperationState(operationId, OperationKind.DISCOVERY, OperationStage.ACTIVE,
            "Gauge found", "Ready for owner access and supported settings", terminal = true)
    }

    fun rotateGauge(rotation: Int) = launchGaugeOperation(OperationKind.CONFIGURATION, "Rotating display") { id ->
        require(capabilities?.displayRotationWrite == true) { "Gauge does not offer display rotation control" }
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.SENDING,
            "Rotating display", "Waiting for saved-state confirmation")
        snapshotRead(bleClient.rotateNearby(rotation))
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
            "Display rotation saved", "The gauge confirmed ${rotation * 90}°", terminal = true)
    }

    fun readDisplaySettings() = launchGaugeOperation(OperationKind.READ, "Checking display settings") { id ->
        require((capabilities?.displaySettingsVersion ?: 0) >= 1) { "Gauge does not offer display settings" }
        displaySettings = GaugeConfigTransferClient(getApplication()).readDisplaySettings(bleClient.selectedGauge())
        if ((displaySettings?.version ?: 0) >= 2)
            savePresentation(presentationPreferences.copy(measurementSystem = requireNotNull(displaySettings).units))
        ownerAccess = OwnerAccess.AUTHENTICATED
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Display settings checked", "Saved on the gauge", terminal = true)
    }

    fun saveDisplaySettings(rotation: Int, brightness: Int) =
        launchGaugeOperation(OperationKind.CONFIGURATION, "Saving display settings") { id ->
            require((capabilities?.displaySettingsVersion ?: 0) >= 1) { "Gauge does not offer display settings" }
            operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.SENDING,
                "Saving display settings", "Waiting for gauge confirmation")
            displaySettings = GaugeConfigTransferClient(getApplication()).saveDisplaySettings(
                bleClient.selectedGauge(), rotation, brightness)
            if ((displaySettings?.version ?: 0) >= 2)
                savePresentation(presentationPreferences.copy(measurementSystem = requireNotNull(displaySettings).units))
            ownerAccess = OwnerAccess.AUTHENTICATED
            operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
                "Display settings saved", "The gauge confirmed ${rotation * 90}° and $brightness% brightness", terminal = true)
        }

    fun saveMeasurementSystem(system: MeasurementSystem) =
        launchGaugeOperation(OperationKind.CONFIGURATION, "Saving measurement units") { id ->
            require((capabilities?.displaySettingsVersion ?: 0) >= 2) { "Gauge does not offer measurement units" }
            operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.SENDING,
                "Saving measurement units", "Waiting for gauge confirmation")
            val client = GaugeConfigTransferClient(getApplication())
            val current = client.readDisplaySettings(bleClient.selectedGauge())
            displaySettings = client.saveDisplaySettings(bleClient.selectedGauge(),
                current.rotation, current.brightness, system)
            savePresentation(presentationPreferences.copy(measurementSystem = requireNotNull(displaySettings).units))
            ownerAccess = OwnerAccess.AUTHENTICATED
            operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
                "Measurement units saved", "The gauge confirmed ${system.name.lowercase()} units", terminal = true)
        }

    fun savePageCycleSeconds(seconds: Int) =
        launchGaugeOperation(OperationKind.CONFIGURATION, "Saving page cycle interval") { id ->
            require((capabilities?.displaySettingsVersion ?: 0) >= 3) { "Gauge does not offer page cycling" }
            require(seconds in setOf(0, 5, 10, 15, 30, 60)) { "Invalid page cycle interval" }
            operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.SENDING,
                "Saving page cycle interval", "Waiting for gauge confirmation")
            val client = GaugeConfigTransferClient(getApplication())
            val current = client.readDisplaySettings(bleClient.selectedGauge())
            displaySettings = client.saveDisplaySettings(bleClient.selectedGauge(),
                current.rotation, current.brightness, current.units, seconds)
            ownerAccess = OwnerAccess.AUTHENTICATED
            operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
                "Page cycling saved", if (seconds == 0) "Automatic cycling is off" else "The gauge will cycle pages every $seconds seconds",
                terminal = true)
        }

    fun readSavedGauge() = launchGaugeOperation(OperationKind.READ, "Checking gauge settings") { id ->
        snapshotRead(bleClient.readSavedSnapshot())
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Gauge settings checked", "Saved revision ${savedGauge?.revision ?: 0}", terminal = true)
    }

    fun pairGauge() = launchGaugeOperation(OperationKind.READ, "Pairing your gauge") { id ->
        require(Build.VERSION.SDK_INT < 31 || getApplication<Application>().checkSelfPermission(
            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { "Bluetooth permission is required" }
        val device = bleClient.selectedGauge()
        if (device.bondState == BluetoothDevice.BOND_BONDING) error("pairing_in_progress")
        val currentWindow = pairingWindowForUi(android.os.SystemClock.elapsedRealtime())
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            when (currentWindow?.state) {
                PairingWindowState.CLOSED -> error("pairing_window_closed")
                PairingWindowState.CODE_DISPLAYED -> error("pairing_in_progress")
                PairingWindowState.OWNER_PRESENT -> error("gauge_already_owned")
                else -> Unit
            }
        }
        operation = if (device.bondState == BluetoothDevice.BOND_BONDED)
            OperationState(id, OperationKind.READ, OperationStage.PREPARING,
                "Checking gauge access", "Confirming the saved owner and phone bond")
        else OperationState(id, OperationKind.READ, OperationStage.PREPARING,
            "Waiting for Android", "Enter the code shown on the gauge in Android's pairing prompt")
        try {
            snapshotRead(bleClient.pairOwner { progress ->
                operation = when (progress) {
                    PairingProgress.WAITING_FOR_ANDROID -> OperationState(id, OperationKind.READ,
                        OperationStage.PREPARING, "Waiting for Android", "Enter the code shown on the gauge")
                    PairingProgress.CHECKING_GAUGE_ACCESS -> OperationState(id, OperationKind.READ,
                        OperationStage.PREPARING, "Checking gauge access", "Confirming the saved owner and phone bond")
                }
            })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error.message == "owner_verification_pending" &&
                device.bondState == BluetoothDevice.BOND_BONDED) {
                updatePairingWindow(null)
                operation = OperationState(id, OperationKind.READ, OperationStage.OUTCOME_UNKNOWN,
                    "Android paired", "The app is still checking gauge owner access", terminal = true)
                foregroundConnection.retrySoon()
                return@launchGaugeOperation
            }
            if (error.message?.contains("pairing_cancelled") == true ||
                error.message?.contains("pairing_failed") == true ||
                error.message?.contains("pairing_timeout") == true) {
                val freshStatus = try { bleClient.readPairingWindowStatus() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                updatePairingWindow(freshStatus)
            }
            throw error
        }
        updatePairingWindow(null)
        connectionChecked()
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Phone paired", "The gauge confirmed owner access.", terminal = true)
    }

    fun refreshPairingStatus() = launchGaugeOperation(OperationKind.READ, "Checking pairing status") { id ->
        require(Build.VERSION.SDK_INT < 31 || getApplication<Application>().checkSelfPermission(
            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { "Bluetooth permission is required" }
        val device = bleClient.selectedGauge()
        if (device.bondState == BluetoothDevice.BOND_BONDING) {
            updatePairingWindow(pairingWindow?.copy(state = PairingWindowState.CODE_DISPLAYED)
                ?: GaugePairingWindow(PairingWindowState.CODE_DISPLAYED, 120))
        } else updatePairingWindow(bleClient.readPairingWindowStatus())
        operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
            "Pairing status checked", when (pairingWindow?.state) {
                PairingWindowState.READY -> "The gauge is ready for pairing"
                PairingWindowState.CODE_DISPLAYED -> "Enter the code shown on the gauge in Android"
                PairingWindowState.OWNER_PRESENT -> "The gauge still has an owner"
                else -> "The pairing window is closed"
            }, terminal = true)
    }

    fun sendNumericConfiguration() = launchGaugeOperation(OperationKind.CONFIGURATION, "Sending gauge setup") { id ->
        require((capabilities?.configurationVersion ?: 0) > 0) {
            "Gauge does not offer dashboard configuration"
        }
        val profileId = profileCollection.activeId
        val capturedDraft = draft
        val adapter = profileCollection.active.primaryAdapter
        val schemaVersion = if ((capabilities?.configurationVersion ?: 0) >= 3) 2 else 1
        require(adapter == null || schemaVersion == 2) { "Update the gauge before sending adapter settings" }
        val baseRevision = activeConfigRevision ?: error("Refresh gauge settings before sending")
        val baseHash = verifiedConfigHash ?: error("Refresh gauge settings before sending")
        operation = OperationState(id, OperationKind.CONFIGURATION, OperationStage.PREPARING,
            "Sending gauge setup", "Checking the current saved revision")
        val template = getApplication<Application>().assets.open("numeric_config_template.json")
            .bufferedReader().use { it.readText() }
        val expectedBytes = ConfigurationProjector.project(template, capturedDraft, profileId,
            baseRevision, adapter, schemaVersion).second
        pendingConfiguration = PendingConfiguration(rememberedGaugeId, profileId, capturedDraft,
            baseRevision + 1, expectedBytes)
        val applied = GaugeConfigTransferClient(getApplication()).apply(
            bleClient.selectedGauge(), capturedDraft, profileId, baseRevision, baseHash, adapter, schemaVersion,
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

    fun readGaugeDiagnostics() = launchGaugeOperation(OperationKind.READ, "Checking vehicle faults") { id ->
        if (capabilities?.adapterRegistryVersion == 1) {
            val source = GaugeConfigTransferClient(getApplication()).readAdapterStatus(bleClient.selectedGauge())
            require(source.vehicleId == profileCollection.activeId && source.phase == 4 && !source.simulated) {
                "Select this car's adapter and send its setup to the gauge before checking vehicle faults"
            }
        }
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

    private fun launchGaugeOperation(kind: OperationKind, title: String, block: suspend (Long) -> Unit) {
        viewModelScope.launch {
            try {
                foregroundConnection.runUserOperation(kind) { id ->
                    presentationError = null
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
        if (operation.kind == OperationKind.CONFIGURATION) {
            configurationNeedsReview = true
            configurationRecoveryRead = false
            lastConfigurationOutcome = operation
        }
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
        presentationError = message
        selectedUpdate = null
        updatePackageMessage = message
    }

    fun checkHostedFirmware() {
        if (hostedUpdateBusy) return
        automaticUpdateJob?.cancel()
        selectedUpdate = null
        val caps = capabilities ?: run {
            hostedUpdateMessage = "Find the gauge before checking firmware compatibility"
            return
        }
        viewModelScope.launch {
            hostedUpdateBusy = true
            updatePreparation = "checking"
            hostedUpdate = null
            try {
                foregroundConnection.runUserOperation(OperationKind.READ) { id ->
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
                        updatePreparation = "available"
                        hostedUpdateMessage =
                            "Compatible development firmware ${update.release.version} is available"
                    } else {
                        updatePreparation = "current"
                        hostedUpdateMessage = "Installed ${running.version} is up to date"
                    }
                    operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
                        "Firmware check complete", hostedUpdateMessage, terminal = true)
                }
            } catch (_: OperationBusyException) {
                updatePreparation = "failed"
                hostedUpdateMessage = "Finish the current gauge operation before checking for firmware"
            } catch (error: CancellationException) {
                hostedUpdateMessage = "Hosted update check was interrupted"
                updatePreparation = "failed"
                operationFailed(hostedUpdateMessage)
                throw error
            } catch (error: Exception) {
                updatePreparation = "failed"
                hostedUpdateMessage = error.message ?: "Could not check hosted firmware"
                operationFailed(hostedUpdateMessage)
            } finally {
                hostedUpdateBusy = false
            }
        }
    }

    fun downloadHostedFirmware(installWhenReady: Boolean = false) {
        if (hostedUpdateBusy) return
        val available = hostedUpdate ?: return
        viewModelScope.launch {
            hostedUpdateBusy = true
            updatePreparation = "downloading"
            hostedUpdateMessage = "Downloading and verifying ${available.release.version}"
            var downloadedSuccessfully = false
            try {
                foregroundConnection.runUserOperation(OperationKind.UPDATE) { id ->
                    operation = OperationState(id, OperationKind.UPDATE, OperationStage.PREPARING,
                        "Downloading update", "Verifying the signed package")
                    val downloaded = GitHubFirmwareSource(getApplication()).download(available)
                    hostedUpdate = downloaded
                    selectedUpdate = requireNotNull(downloaded.bundle)
                    updatePackageMessage = "GitHub development firmware ${downloaded.release.version} verified and ready"
                    hostedUpdateMessage = "Download verified. Review and install when the gauge has stable power."
                    updatePreparation = "ready"
                    downloadedSuccessfully = true
                    operation = OperationState(id, OperationKind.READ, OperationStage.ACTIVE,
                        "Update ready", terminal = true)
                }
            } catch (_: OperationBusyException) {
                updatePreparation = "failed"
                hostedUpdateMessage = "Finish the current gauge operation before downloading an update"
            } catch (error: CancellationException) {
                hostedUpdateMessage = "Firmware download was interrupted"
                updatePreparation = "failed"
                operationFailed(hostedUpdateMessage)
                throw error
            } catch (error: Exception) {
                hostedUpdateMessage = error.message ?: "Hosted firmware download failed"
                updatePreparation = "failed"
                operationFailed(hostedUpdateMessage)
            } finally {
                hostedUpdateBusy = false
            }
            if (downloadedSuccessfully && installWhenReady) installSelectedUpdate()
        }
    }
    fun selectionError(message: String) {
        presentationError = message
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
        save(draft.copy(pidId = page.pidIds[0], layout = page.layout, source = demoCatalog.first { it.id == page.pidIds[0] }.source))
    }

    fun addPage(pidId: String = "rpm") {
        if (draft.pages.size >= 8 || pidId !in ConfigurationProjector.supportedPidIds) return
        val used = draft.pages.map { it.id }.toSet()
        val sequence = (1..99).first { "page.custom.$it" !in used }
        val page = GaugePageDraft("page.custom.$sequence", demoCatalog.first { it.id == pidId }.gaugeLabel,
            GaugeLayout.Numeric, listOf(pidId))
        editingPageIndex = draft.pages.size
        save(draft.copy(pidId = pidId, layout = GaugeLayout.Numeric, source = "ECM",
            pages = draft.pages + page))
    }

    fun removePage(index: Int) {
        if (draft.pages.size <= 1 || index !in draft.pages.indices) return
        val selectedId = draft.pages.getOrNull(editingPageIndex)?.id
        val pages = draft.pages.toMutableList().also { it.removeAt(index) }
        editingPageIndex = pages.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
            ?: index.coerceAtMost(pages.lastIndex)
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
    /** Commit a completed form once. Opening or cancelling the editor never creates an alert. */
    fun saveAlert(alert: GaugeAlertDraft) {
        if (alert.pidId !in ConfigurationProjector.supportedPidIds) return
        val previous = draft.alerts.firstOrNull { it.pidId == alert.pidId }
        if (previous != null && previous.id != alert.id) return
        val alerts = if (previous == null) draft.alerts + alert else draft.alerts.map {
            if (it.id == previous.id) alert else it
        }
        if (alerts.size > 32 || alerts.map { it.id }.distinct().size != alerts.size ||
            ConfigurationProjector.blockers(Draft(alerts = listOf(alert))).isNotEmpty()) return
        save(draft.copy(alerts = alerts))
    }
    fun removeAlert(id: String) = save(draft.copy(alerts = draft.alerts.filterNot { it.id == id }))
    fun setSource(value: String) = save(draft.copy(source = value))
    private fun save(value: Draft) {
        if (profileError != null) return
        draft = value
        profileCollection = profileCollection.copy(profiles = profileCollection.profiles.map { profile ->
            if (profile.id == profileCollection.activeId) profile.copy(draft = value) else profile
        })
        if (!profileStore.save(profileCollection)) profileError = "Could not save changes on this phone"
    }
    fun checkGaugeForReview() = launchGaugeOperation(OperationKind.READ, "Checking gauge settings") { id ->
        val client = GaugeConfigTransferClient(getApplication())
        if (ownerAccess != OwnerAccess.AUTHENTICATED && capabilities?.savedStateRead == true)
            snapshotRead(bleClient.readSavedSnapshot())
        operation = OperationState(id, OperationKind.READ, OperationStage.CHECKING_RUNNING,
            "Checking gauge settings")
        activeDocumentRead(client.readActiveDocument(bleClient.selectedGauge()))
        runtimeIdentityRead(client.readRuntimeIdentity(bleClient.selectedGauge()))
        configurationRecoveryRead = true
        operation = OperationState(id, OperationKind.READ,
            if (runtimeIdentity?.usedPreviousGeneration == true) OperationStage.RECOVERED else OperationStage.ACTIVE,
            "Gauge settings checked", terminal = true)
    }

    /** Changes to existing snapshot state are observed without moving protocol ownership into the UI. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CompanionUiState> = flow {
        while (true) {
            emit(android.os.SystemClock.elapsedRealtime())
            delay(1_000)
        }
    }.flatMapLatest { snapshotFlow { presentationState(android.os.SystemClock.elapsedRealtime()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            presentationState(android.os.SystemClock.elapsedRealtime()))

}
