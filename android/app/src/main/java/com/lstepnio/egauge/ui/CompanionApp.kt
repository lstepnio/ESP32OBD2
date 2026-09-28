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

private enum class Route(val title: String, val icon: GaugeIcon) {
    Gauge("Gauge", GaugeIcon.Gauge), Car("Car", GaugeIcon.Car), Settings("Settings", GaugeIcon.Settings),
    Expert("Expert", GaugeIcon.Tools), Customize("Customize", GaugeIcon.Gauge),
    Setup("Set up gauge", GaugeIcon.Bluetooth), Updates("Updates", GaugeIcon.Refresh),
    DevelopmentUpdates("Development updates", GaugeIcon.Tools),
}

@Composable
fun CompanionApp(model: AppViewModel, onFindGauge: () -> Unit, onInstallUpdate: () -> Unit,
    onSelectUpdate: () -> Unit, onSelectBuiltIn: () -> Unit, onBluetoothSettings: () -> Unit,
    fold: FoldingFeature? = null) {
    val state by model.uiState.collectAsStateWithLifecycle()
    EGaugeTheme(dynamicColor = state.settings.dynamicColor) {
        var route by rememberSaveable { mutableStateOf(Route.Gauge) }
        var customizeStep by rememberSaveable { mutableIntStateOf(0) }
        var expertTool by rememberSaveable { mutableStateOf("") }
        var detailsOpen by rememberSaveable { mutableStateOf(false) }
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
        PredictiveBackHandler(enabled = route != Route.Gauge && !detailsOpen && !progressOpen) { events ->
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
                                TextButton({ detailsOpen = true }) { Text("Details") }
                            }
                        }
                        val op = state.operation
                        // Discovery and reconnecting are routine background work. The compact gauge pill
                        // reports them without interrupting the current screen. Transfers remain visible
                        // until the gauge has proved their result, including recovery-required outcomes.
                        val transferInProgress = op.kind in setOf(OperationKind.CONFIGURATION, OperationKind.UPDATE) &&
                            (op.busy || op.needsCheck || op.status.tone == StatusTone.Success)
                        if (op.visible && transferInProgress)
                            OperationBanner(op, { progressOpen = true }, {
                                if (op.update) route = if (state.settings.advanced) Route.DevelopmentUpdates else Route.Updates
                                else if (model.configurationRecoveryRead) { customizeStep = 4; route = Route.Customize }
                                else model.checkGaugeForReview()
                            })
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
                                        HomeAction.Review -> { customizeStep = 4; route = Route.Customize }
                                        HomeAction.Customize -> { customizeStep = 0; route = Route.Customize }
                                    }
                                }, { customizeStep = 0; route = Route.Customize }, { detailsOpen = true }, { index ->
                                    model.selectPage(index)
                                    customizeStep = 0
                                    route = Route.Customize
                                },
                                    statusInBanner = transferInProgress || state.connection.phase !in setOf(ConnectionPhase.Idle, ConnectionPhase.Ready))
                                Route.Setup -> SetupScreen(state.setup, onFindGauge,
                                    { if (model.capabilities?.experimentalNumericConfig == true) model.checkGaugeForReview() else model.readSavedGauge() },
                                    { id -> model.gaugeCandidates.firstOrNull { it.id == id }?.let(model::selectGaugeCandidate) },
                                    { customizeStep = 0; route = Route.Customize }, ::back, { detailsOpen = true })
                                Route.Customize -> DashboardEditorScreen(state.customize, customizeStep, { customizeStep = it }, ::back,
                                    { detailsOpen = true }, CustomizeActions(
                                        model::selectPage, { id -> model.selectPid(demoCatalog.first { it.id == id }) },
                                        { id -> model.selectSecondaryPid(demoCatalog.first { it.id == id }) },
                                        model::addPage, model::removePage, model::movePage, model::selectLayout,
                                        model::setWarning, model::setCritical, model::setHysteresis, model::setTriggerDwell, model::setClearDwell,
                                        model::checkGaugeForReview, { model.sendNumericConfiguration(); route = Route.Gauge }, { route = Route.Setup }))
                                Route.Car -> CarScreen(state.car, model::readGaugeDiagnostics, { route = Route.Setup }, { detailsOpen = true },
                                    model::selectProfile, { name -> model.editProfileName(name); model.createProfile() })
                                Route.Settings -> SettingsScreen(state.settings, model::setAdvancedTools, model::setDynamicColor,
                                    model::renameGauge, model::rotateGauge, model::readSavedGauge, { route = Route.Updates },
                                    { route = Route.Setup }, { detailsOpen = true }, onBluetoothSettings)
                                Route.Updates, Route.DevelopmentUpdates -> UpdatesScreen(state.updates, state.operation, route == Route.DevelopmentUpdates,
                                    ::back, model::checkHostedFirmware, onInstallUpdate, model::readRunningFirmware, onSelectUpdate, { detailsOpen = true })
                                Route.Expert -> ExpertScreen(state.expert, expertTool, { expertTool = it }, ::back, { detailsOpen = true }, ExpertActions(
                                    model::search, model::filter, { id -> model.selectPid(demoCatalog.first { it.id == id }); customizeStep = 1; route = Route.Customize },
                                    model::editLabInput, model::editCustomRequest, model::selectCustomSource, model::setSecondAdapterEnabled,
                                    model::runWifiTransportSecurityCheck, model::readHardwareCapacity, model::readSavedGauge,
                                    model::readConfiguration, model::readConfigurationDocument, model::readGaugeDiagnostics,
                                    model::readRunningFirmware, onSelectBuiltIn, { route = Route.DevelopmentUpdates }))
                            }
                        }
                    }
                }
            }
        }
        if (detailsOpen) DetailsSheet("Details", state.home.details, { detailsOpen = false }) {
            if (model.capabilities != null) {
                OutlinedButton(model::checkGaugeForReview, enabled = !state.home.busy) { Text("Refresh gauge settings") }
                if (model.canAdoptGaugeDraft) OutlinedButton(model::adoptGaugeDraft, enabled = !state.home.busy) { Text("Use gauge settings") }
                if (model.capabilities?.hardwareCapacityVersion == 1)
                    OutlinedButton(model::readHardwareCapacity, enabled = !state.home.busy) { Text("Read hardware details") }
            }
        }
        if (progressOpen) DetailsSheet("Gauge activity", listOf(DetailUi(state.operation.status.title, state.operation.status.detail)) +
            state.home.details.filter { it.label.startsWith("Operation") }, { progressOpen = false }) {
            ProgressStepper(if (state.operation.update) listOf("Downloading", "Sending to gauge", "Restarting", "Done")
                else listOf("Preparing", "Sending", "Restarting", "Checking gauge"), state.operation.step,
                state.operation.progress, finished = state.operation.status.tone == StatusTone.Success)
        }
    }
}

@Composable
private fun OperationBanner(state: OperationUi, onExpand: () -> Unit, onRecover: () -> Unit) {
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
            TextButton(if (state.needsCheck) onRecover else onExpand, contentPadding = PaddingValues(0.dp)) {
                Text(if (state.needsCheck) "Review gauge status" else "View progress")
            }
        }
    }
}
