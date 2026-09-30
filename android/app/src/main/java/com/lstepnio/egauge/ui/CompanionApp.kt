package com.lstepnio.egauge.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import com.lstepnio.egauge.*
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.customize.*
import com.lstepnio.egauge.ui.expert.*
import com.lstepnio.egauge.ui.home.HomeScreen
import com.lstepnio.egauge.ui.settings.*
import com.lstepnio.egauge.ui.setup.SetupScreen
import com.lstepnio.egauge.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private enum class Route(val title: String, val icon: GaugeIcon) {
    Gauge("Gauge", GaugeIcon.Gauge), Car("Car", GaugeIcon.Car), Settings("Settings", GaugeIcon.Settings),
    Expert("Expert", GaugeIcon.Tools), Customize("Customize", GaugeIcon.Gauge),
    Setup("Set up gauge", GaugeIcon.Bluetooth), Updates("Updates", GaugeIcon.Refresh),
    DevelopmentUpdates("Development updates", GaugeIcon.Tools),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionApp(model: AppViewModel, onFindGauge: () -> Unit, onInstallUpdate: () -> Unit,
    onSelectUpdate: () -> Unit, onSelectBuiltIn: () -> Unit, onBluetoothSettings: () -> Unit,
    fold: FoldingFeature? = null) {
    val state by model.uiState.collectAsStateWithLifecycle()
    EGaugeTheme(dynamicColor = state.settings.dynamicColor) {
        var route by rememberSaveable { mutableStateOf(Route.Gauge) }
        var customizeStep by rememberSaveable { mutableIntStateOf(0) }
        var expertTool by rememberSaveable { mutableStateOf("") }
        var progressOpen by rememberSaveable { mutableStateOf(false) }
        var backProgress by remember { mutableFloatStateOf(0f) }
        fun back() {
            when {
                route == Route.Customize && customizeStep > 0 -> customizeStep = 0
                route == Route.Expert && expertTool.isNotBlank() -> expertTool = ""
                route == Route.Updates -> route = Route.Settings
                route == Route.DevelopmentUpdates -> route = Route.Expert
                else -> route = Route.Gauge
            }
        }
        LaunchedEffect(state.settings.advanced) {
            if (!state.settings.advanced && route in setOf(Route.Expert, Route.DevelopmentUpdates)) route = Route.Settings
        }
        LaunchedEffect(state.connection.phase) {
            if (route == Route.Gauge && state.connection.phase in setOf(ConnectionPhase.PairRequired, ConnectionPhase.ChooseGauge))
                route = Route.Setup
        }
        PredictiveBackHandler(enabled = route != Route.Gauge && !progressOpen) { events ->
            try { events.collect { backProgress = it.progress }; back() }
            catch (_: CancellationException) { /* A cancelled gesture keeps the current step. */ }
            finally { backProgress = 0f }
        }
        val destinations = listOf(Route.Gauge, Route.Car, Route.Settings) + if (state.settings.advanced) listOf(Route.Expert) else emptyList()
        val selectedRoute = when (route) { Route.Setup, Route.Customize -> Route.Gauge
            Route.Updates -> Route.Settings; Route.DevelopmentUpdates -> Route.Expert; else -> route }
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val rail = maxWidth >= EGaugeTokens.Layout.railBreakpoint.dp
            // A separating hinge must never run through a control. Use the larger unobstructed pane.
            val foldInsets = fold?.takeIf { it.isSeparating }?.let { feature ->
                val bounds = feature.bounds
                if (feature.orientation == FoldingFeature.Orientation.VERTICAL) {
                    val left = with(density) { bounds.left.toDp() }
                    val right = maxWidth - with(density) { bounds.right.toDp() }
                    if (right > left) PaddingValues(start = with(density) { bounds.right.toDp() })
                    else PaddingValues(end = right + with(density) { bounds.width().toDp() })
                } else {
                    val top = with(density) { bounds.top.toDp() }
                    val bottom = maxHeight - with(density) { bounds.bottom.toDp() }
                    if (bottom > top) PaddingValues(top = with(density) { bounds.bottom.toDp() })
                    else PaddingValues(bottom = bottom + with(density) { bounds.height().toDp() })
                }
            } ?: PaddingValues(0.dp)
            Scaffold(containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = { if (!rail) NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    destinations.forEach { destination ->
                        NavigationBarItem(selectedRoute == destination, onClick = { route = destination },
                            icon = { EGaugeIcon(destination.icon) }, label = { Text(destination.title) })
                    }
                } }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(foldInsets)) {
                    if (rail) NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                        Spacer(Modifier.height(20.dp))
                        destinations.forEach { destination ->
                            NavigationRailItem(selectedRoute == destination, onClick = { route = destination },
                                icon = { EGaugeIcon(destination.icon) }, label = { Text(destination.title) })
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        state.notice?.takeIf { it.tone in setOf(StatusTone.Error, StatusTone.Critical, StatusTone.Stale) }?.let { notice ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                StatusCard(notice)
                            }
                        }
                        val op = state.operation
                        // Discovery and reconnecting are routine background work. The compact gauge pill
                        // reports them without interrupting the current screen. Transfers remain visible
                        // until the gauge has proved their result, including recovery-required outcomes.
                        val banner = rememberTransferBannerState(op)
                        if (banner.visible)
                            OperationBanner(op, { progressOpen = true }, {
                                if (op.update) route = if (state.settings.advanced) Route.DevelopmentUpdates else Route.Updates
                                else if (model.configurationRecoveryRead) { customizeStep = 5; route = Route.Customize }
                                else model.checkGaugeForReview()
                            }, banner.dismiss)
                        Box(Modifier.weight(1f).widthIn(max = EGaugeTokens.Layout.contentMax.dp).fillMaxWidth()
                            .graphicsLayer { scaleX = 1f - backProgress * .035f; scaleY = 1f - backProgress * .035f
                                alpha = 1f - backProgress * .15f }) {
                            when (route) {
                                Route.Gauge -> HomeScreen(state.home, {
                                    when (state.home.primaryAction) {
                                        HomeAction.SetUp -> if (state.connection.phase in setOf(ConnectionPhase.PairRequired, ConnectionPhase.ChooseGauge)) route = Route.Setup
                                            else if (model.rememberedGaugeId != null || state.connection.phase == ConnectionPhase.PermissionRequired ||
                                                state.connection.phase == ConnectionPhase.BluetoothOff) onFindGauge() else route = Route.Setup
                                        HomeAction.Check -> model.checkGaugeForReview()
                                        HomeAction.Review -> { customizeStep = 5; route = Route.Customize }
                                        HomeAction.Customize -> { customizeStep = 0; route = Route.Customize }
                                    }
                                }, { customizeStep = 0; route = Route.Customize }, { index ->
                                    model.selectPage(index)
                                    customizeStep = 0
                                    route = Route.Customize
                                }, onUpdates = { route = Route.Updates })
                                Route.Setup -> SetupScreen(state.setup, onFindGauge,
                                    { if (model.capabilities?.experimentalNumericConfig == true) model.checkGaugeForReview() else model.readSavedGauge() },
                                    { id -> model.gaugeCandidates.firstOrNull { it.id == id }?.let(model::selectGaugeCandidate) },
                                    { customizeStep = 0; route = Route.Customize }, ::back)
                                Route.Customize -> DashboardEditorScreen(state.customize, customizeStep, { customizeStep = it }, ::back,
                                    CustomizeActions(
                                        model::selectPage, { id -> model.selectPid(demoCatalog.first { it.id == id }) },
                                        { id -> model.selectSecondaryPid(demoCatalog.first { it.id == id }) },
                                        model::addPage, model::removePage, model::movePage, model::selectLayout,
                                        model::saveAlert, model::removeAlert,
                                        model::checkGaugeForReview, { model.sendNumericConfiguration(); route = Route.Gauge }, { route = Route.Setup }))
                                Route.Car -> CarScreen(state.car, model::readGaugeDiagnostics, { route = Route.Setup },
                                    model::selectProfile, { name -> model.editProfileName(name); model.createProfile() })
                                Route.Settings -> SettingsScreen(state.settings, model::setAdvancedTools, model::setDynamicColor,
                                    model::renameGauge, model::rotateGauge, model::readSavedGauge,
                                    model::readDisplaySettings, model::saveDisplaySettings, { route = Route.Updates },
                                    { route = Route.Setup }, onBluetoothSettings, model::saveMeasurementSystem)
                                Route.Updates, Route.DevelopmentUpdates -> UpdatesScreen(state.updates, state.operation, route == Route.DevelopmentUpdates,
                                    ::back, model::checkHostedFirmware, onInstallUpdate, model::readRunningFirmware, onSelectUpdate)
                                Route.Expert -> ExpertScreen(state.expert, expertTool, { expertTool = it }, ::back, ExpertActions(
                                    model::search, model::filter, { id -> model.selectPid(demoCatalog.first { it.id == id }); customizeStep = 0; route = Route.Customize },
                                    model::editLabInput, model::editCustomRequest, model::selectCustomSource, model::setSecondAdapterEnabled,
                                    model::readHardwareCapacity, model::readSavedGauge,
                                    model::readConfiguration, model::readConfigurationDocument, model::readGaugeDiagnostics,
                                    model::readRunningFirmware, onSelectBuiltIn, { route = Route.DevelopmentUpdates },
                                    model::checkGaugeForReview, model::adoptGaugeDraft))
                            }
                        }
                    }
                }
            }
        }
        if (progressOpen) ModalBottomSheet(onDismissRequest = { progressOpen = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Gauge activity", style = MaterialTheme.typography.headlineSmall)
                StatusCard(state.operation.status)
                ProgressStepper(if (state.operation.update) listOf("Downloading", "Sending to gauge", "Restarting", "Done")
                    else listOf("Preparing", "Sending", "Restarting", "Checking gauge"), state.operation.step,
                    state.operation.progress, finished = state.operation.status.tone == StatusTone.Success)
                TextButton({ progressOpen = false }, Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

internal class TransferBannerState(val visible: Boolean, val dismiss: () -> Unit)

@Composable
internal fun rememberTransferBannerState(operation: OperationUi): TransferBannerState {
    var dismissedSuccessId by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(operation.id, operation.visible, operation.busy, operation.needsCheck, operation.status.tone) {
        if (successBannerMayDismiss(operation)) {
            delay(6_000)
            dismissedSuccessId = operation.id
        }
    }
    val transfer = operation.kind in setOf(OperationKind.CONFIGURATION, OperationKind.UPDATE) &&
        (operation.busy || operation.needsCheck || successBannerMayDismiss(operation))
    return TransferBannerState(operation.visible && transfer &&
        !(successBannerMayDismiss(operation) && dismissedSuccessId == operation.id)) {
        if (successBannerMayDismiss(operation)) dismissedSuccessId = operation.id
    }
}

@Composable
private fun OperationBanner(state: OperationUi, onExpand: () -> Unit, onRecover: () -> Unit,
    onDismiss: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics {
            liveRegion = LiveRegionMode.Polite
        }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EGaugeIcon(if (state.needsCheck) GaugeIcon.Warning else if (state.busy) GaugeIcon.Refresh else GaugeIcon.Check,
                    color = toneColor(state.status.tone))
                Text(state.status.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (state.needsCheck) Text(state.status.detail, style = MaterialTheme.typography.bodyMedium)
            TextButton(when { state.needsCheck -> onRecover; successBannerMayDismiss(state) -> onDismiss
                else -> onExpand }, contentPadding = PaddingValues(0.dp)) {
                Text(when { state.needsCheck -> "Review gauge status"; successBannerMayDismiss(state) -> "Dismiss"
                    else -> "View progress" })
            }
        }
    }
}
