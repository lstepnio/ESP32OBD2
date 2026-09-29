package com.lstepnio.egauge.ui.state

import androidx.compose.runtime.Immutable
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.OwnerAccess
import com.lstepnio.egauge.connection.ConnectionState
import com.lstepnio.egauge.core.designsystem.*

/** Immutable screen contracts. Feature composables do not read transport or repository objects. */
@Immutable
data class PageUi(val id: String, val name: String, val readingId: String, val readingName: String,
                  val unit: String, val layout: GaugeLayout, val preview: ReadingPreviewUi,
                  val secondaryId: String? = null)
@Immutable
data class ReadingUi(val id: String, val name: String, val unit: String, val details: List<DetailUi>)
@Immutable
data class AlertUi(val id: String, val readingId: String, val readingName: String, val unit: String,
                   val direction: String, val warning: Int, val critical: Int, val resetMargin: Int,
                   val triggerSeconds: Float, val clearSeconds: Float, val range: IntRange)
@Immutable
data class HomeUiState(val gaugeName: String, val connection: String, val connectionVerified: Boolean,
    val status: StatusUi, val pages: List<PageUi>, val pendingChanges: Boolean,
    val primaryLabel: String, val primaryAction: HomeAction, val busy: Boolean, val details: List<DetailUi>)
enum class HomeAction { SetUp, Check, Review, Customize }
@Immutable
data class CustomizeUiState(val pages: List<PageUi>, val editingPage: Int, val readings: List<ReadingUi>,
    val alerts: List<AlertUi>, val blockers: List<String>, val canSend: Boolean,
    val needsCheck: Boolean, val found: Boolean, val busy: Boolean, val editingEnabled: Boolean,
    val advanced: Boolean, val supportedLayouts: Set<GaugeLayout>, val details: List<DetailUi>)
@Immutable
data class VehicleUi(val id: String, val name: String)
@Immutable
data class FaultUi(val code: String, val description: String, val category: String)
@Immutable
data class CarUiState(val name: String, val profiles: List<VehicleUi>, val activeId: String,
    val status: StatusUi, val faults: List<FaultUi>, val canCheck: Boolean, val incomplete: Boolean,
    val profileError: Boolean, val details: List<DetailUi>)
@Immutable
data class SettingsUiState(val name: String, val found: Boolean, val rotation: Int?, val canRotate: Boolean,
    val busy: Boolean, val advanced: Boolean, val dynamicColor: Boolean, val version: String,
    val details: List<DetailUi>, val displaySettingsVersion: Int = 0, val brightness: Int? = null)
@Immutable
data class CandidateUi(val id: String, val name: String, val details: List<DetailUi>)
@Immutable
data class SetupUiState(val found: Boolean, val owner: OwnerAccess, val busy: Boolean,
    val candidates: List<CandidateUi>, val status: StatusUi, val details: List<DetailUi>)
@Immutable
data class ExpertUiState(val readings: List<ReadingUi>, val canRead: Boolean, val canReadHardware: Boolean,
    val canCheckWifi: Boolean, val secondAdapter: Boolean, val details: List<DetailUi>,
    val query: String, val sourceFilter: String, val selectedReadingId: String, val labInput: String,
    val decoded: StatusUi, val customInput: String, val customSource: String, val customResult: StatusUi,
    val wifiStatus: String, val busy: Boolean)
@Immutable
data class UpdatesUiState(val status: StatusUi, val availableVersion: String?, val installedVersion: String,
    val canCheck: Boolean, val canInstall: Boolean, val ready: Boolean, val busy: Boolean,
    val recoveryRequired: Boolean, val details: List<DetailUi>)
@Immutable
data class CompanionUiState(val home: HomeUiState, val customize: CustomizeUiState, val car: CarUiState,
    val settings: SettingsUiState, val expert: ExpertUiState, val setup: SetupUiState,
    val updates: UpdatesUiState, val operation: OperationUi, val notice: StatusUi? = null,
    val connection: ConnectionState = ConnectionState())
