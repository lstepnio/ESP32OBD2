package com.lstepnio.egauge.ui.state

import androidx.compose.runtime.Immutable
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.GaugePairingWindow
import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.OwnerAccess
import com.lstepnio.egauge.connection.ConnectionState
import com.lstepnio.egauge.core.designsystem.*

/** Immutable screen contracts. Feature composables do not read transport or repository objects. */
@Immutable
data class PageUi(val id: String, val name: String, val readingId: String, val readingName: String,
                  val unit: String, val layout: GaugeLayout, val preview: ReadingPreviewUi,
                  val secondaryId: String? = null)
@Immutable
data class ReadingUi(val id: String, val name: String, val unit: String, val details: List<DetailUi>, val source: String = "ECM")
@Immutable
data class AlertUi(val id: String, val readingId: String, val readingName: String, val unit: String,
                   val direction: String, val warning: Int, val critical: Int, val resetMargin: Int,
                   val triggerSeconds: Float, val clearSeconds: Float, val range: IntRange,
                   val priority: Int = 8, val canonicalWarning: Int = warning,
                   val canonicalCritical: Int = critical, val canonicalResetMargin: Int = resetMargin)
@Immutable
enum class UpdateNotice { None, Ready, NeedsCheck }
@Immutable
data class HomeUiState(val gaugeName: String, val connection: String, val connectionVerified: Boolean,
    val status: StatusUi, val pages: List<PageUi>, val pendingChanges: Boolean,
    val primaryLabel: String, val primaryAction: HomeAction, val busy: Boolean, val details: List<DetailUi>,
    val updateNotice: UpdateNotice = UpdateNotice.None)
enum class HomeAction { SetUp, Check, Review, Customize }
@Immutable
data class CustomizeUiState(val pages: List<PageUi>, val editingPage: Int, val readings: List<ReadingUi>,
    val alerts: List<AlertUi>, val blockers: List<String>, val canSend: Boolean,
    val needsCheck: Boolean, val found: Boolean, val busy: Boolean, val editingEnabled: Boolean,
    val advanced: Boolean, val supportedLayouts: Set<GaugeLayout>, val details: List<DetailUi>,
    val measurementSystem: MeasurementSystem = MeasurementSystem.Metric, val reviewPages: List<PageUi> = emptyList(), val actionSummary: String? = null, val removablePageIds: Set<String>? = null, val editingIssue: String? = null)
@Immutable
data class VehicleUi(val id: String, val name: String)
@Immutable
data class FaultUi(val code: String, val description: String, val category: String)
@Immutable
data class FaultCategoryUi(val name: String, val status: String, val faults: List<FaultUi>)
@Immutable
data class FaultSourceUi(val title: String, val status: StatusUi, val categories: List<FaultCategoryUi>)
@Immutable
data class CarUiState(val name: String, val profiles: List<VehicleUi>, val activeId: String,
    val status: StatusUi, val faults: List<FaultUi>, val canCheck: Boolean, val incomplete: Boolean,
    val profileError: Boolean, val details: List<DetailUi>,
    val adapterAvailable: Boolean = false, val adapterSelected: String? = null,
    val adapterMessage: String? = null, val adapterCandidates: List<com.lstepnio.egauge.AdapterCandidate> = emptyList(),
    val canSendAdapter: Boolean = false, val faultSources: List<FaultSourceUi> = emptyList(),
    val connectionStatus: StatusUi = StatusUi("Checking car connection", "Checks run automatically while the app is open."),
    val setupNeeded: Boolean = false, val transmissionChild: Boolean = false, val connectionLinks: List<ConnectionLinkUi> = emptyList(),
    val actionPages: List<com.lstepnio.egauge.GaugePageDraft> = emptyList(),
    val actions: List<com.lstepnio.egauge.PageAction> = emptyList(), val canEditActions: Boolean = false,
    val actionsSupported: Boolean = false)
@Immutable
data class SettingsUiState(val name: String, val found: Boolean, val rotation: Int?, val canRotate: Boolean,
    val busy: Boolean, val advanced: Boolean, val dynamicColor: Boolean, val version: String,
    val details: List<DetailUi>, val displaySettingsVersion: Int = 0, val brightness: Int? = null,
    val measurementSystem: MeasurementSystem = MeasurementSystem.Metric, val cycleSeconds: Int? = null,
    val settingsCurrent: Boolean = true, val gauges: List<GaugeUi> = emptyList(), val selectedGaugeId: String? = null)
@Immutable
data class GaugeUi(val id: String, val name: String, val vehicleName: String?, val source: String?, val needsReview: Boolean = false, val bothAdapters: Boolean = false)
@Immutable
data class CandidateUi(val id: String, val name: String, val shortId: String, val details: List<DetailUi>)
@Immutable
data class SetupUiState(val found: Boolean, val owner: OwnerAccess, val busy: Boolean,
    val candidates: List<CandidateUi>, val status: StatusUi, val details: List<DetailUi>,
    val pairingWindow: GaugePairingWindow? = null, val androidBonded: Boolean = false)
@Immutable
data class ExpertUiState(val canRead: Boolean, val canReadHardware: Boolean,
    val canAdoptGaugeSettings: Boolean, val details: List<DetailUi>,
    val vehicleName: String = "", val primarySource: String = "ECM", val hasPrimaryAdapter: Boolean = false,
    val hasTransmission: Boolean = false, val selectedSource: String = "ECM", val canEditVehicle: Boolean = false,
    val selectedAdapter: String? = null, val adapterCandidates: List<com.lstepnio.egauge.AdapterCandidate> = emptyList(),
    val canFindAdapter: Boolean = false, val adapterMessage: String? = null,
    val legacyParents: List<VehicleUi> = emptyList(), val vehicleMessage: String? = null, val bothAdapters: Boolean = false, val canUseBothAdapters: Boolean = false)
@Immutable
data class UpdatesUiState(val status: StatusUi, val availableVersion: String?, val installedVersion: String,
    val canCheck: Boolean, val canInstall: Boolean, val ready: Boolean, val busy: Boolean,
    val recoveryRequired: Boolean, val details: List<DetailUi>, val held: Boolean = false,
    val feedUnavailable: Boolean = false)
@Immutable
data class CompanionUiState(val home: HomeUiState, val customize: CustomizeUiState, val car: CarUiState,
    val settings: SettingsUiState, val expert: ExpertUiState, val setup: SetupUiState,
    val updates: UpdatesUiState, val operation: OperationUi, val notice: StatusUi? = null,
    val connection: ConnectionState = ConnectionState())
