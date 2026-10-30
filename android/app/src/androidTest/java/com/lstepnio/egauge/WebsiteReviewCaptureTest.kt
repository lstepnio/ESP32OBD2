package com.lstepnio.egauge

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.connection.ConnectionState
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.car.*
import com.lstepnio.egauge.ui.customize.*
import com.lstepnio.egauge.ui.expert.*
import com.lstepnio.egauge.ui.home.HomeScreen
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import com.lstepnio.egauge.ui.settings.*
import com.lstepnio.egauge.ui.setup.SetupScreen
import com.lstepnio.egauge.ui.state.*
import java.io.File
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Capture-only immutable fixtures. No activity, transport actions, or real device access. */
class WebsiteReviewCaptureTest {
    @get:Rule val compose = createComposeRule()
    private data class Scene(val route: String, val state: String = "default", val large: Boolean = false, val dark: Boolean = false, val scale: Float = 1f)
    private val marketing = InstrumentationRegistry.getArguments().getString("websiteMarketing") == "true"
    private val ok = StatusUi("Saved & running on gauge", "Your gauge confirmed these settings.", StatusTone.Success)
    private val car = CarUiState(
        "Example car", listOf(VehicleUi("example", "Example car")), "example",
        StatusUi("Engine warning", tone = StatusTone.Warning), emptyList(), true, false, false, emptyList(),
        gaugeKnown = true, adapterAvailable = true, adapterSelected = "Example engine adapter",
        connectionStatus = StatusUi("Car connected", tone = StatusTone.Success),
        faultSources = listOf(FaultSourceUi("Engine", StatusUi("Engine warning", tone = StatusTone.Warning), listOf(FaultCategoryUi("Stored", "Checked just now", listOf(FaultUi("P0300", "", "Stored"))))))
    )
    private val expert = ExpertUiState(true, true, false, listOf(DetailUi("Capture source", "Synthetic review fixture")), vehicleName = "Example car", vehicleId = "example", hasPrimaryAdapter = true, canEditVehicle = true, canShowcase = true)
    private val actions = ExpertActions({}, {}, {}, {})

    @Test fun captureWebsiteReview() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        // Model-only routes read disposable emulator defaults. No foreground coordinator is started.
        lateinit var model: AppViewModel
        val store = ViewModelStore()
        compose.runOnUiThread {
            model = AppViewModel(context)
            store.put("review", model)
        }
        var scene by mutableStateOf(Scene("gauge"))
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(if (marketing) 2f else 1f, scene.scale),
                LocalConnectionStatus provides {
                    if (scene.state == "permission-denied") {
                        ConnectionStatusWidget("Allow nearby devices", false, emptyList(), UpdateNotice.None, {})
                    } else {
                        ConnectionStatusWidget("Gauge ready", true, listOf(ConnectionLinkUi("example", "Car", StatusUi("Car connected", tone = StatusTone.Success))), UpdateNotice.None, {})
                    }
                }
            ) {
                EGaugeTheme(dark = scene.dark) {
                    Surface((if (scene.route == "actions") Modifier.fillMaxSize() else Modifier.requiredSize(if (scene.large) 1000.dp else 390.dp, if (scene.large) 800.dp else 844.dp)).testTag("review-capture"), color = MaterialTheme.colorScheme.background) {
                        Row {
                            if (scene.large) NavigationRail(containerColor = MaterialTheme.colorScheme.background) { navigation(scene, true) }
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.weight(1f).fillMaxWidth()) { key(scene) { screen(scene, model) } }
                                if (!scene.large) NavigationBar(containerColor = MaterialTheme.colorScheme.background) { navigationBar(scene) }
                            }
                        }
                    }
                }
            }
        }
        val output = File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), if (marketing) "website-marketing" else "website-review")
        check(output.mkdirs() || output.isDirectory)
        val routes = listOf("gauge", "car", "settings", "expert", "setup", "customize", "alerts", "alert-settings", "car-advanced", "updates")
        val cases = if (marketing) listOf("gauge" to "default", "car" to "default", "settings" to "default", "actions" to "default") else routes.map { it to "default" } + listOf("alerts" to "empty", "setup" to "loading", "gauge" to "permission-denied", "updates" to "error", "alerts" to "synthetic-current", "customize" to "review", "car-advanced" to "hidden-codes", "car-advanced" to "second-adapter")
        try {
            for (large in if (marketing) listOf(false) else listOf(false, true)) {
                for (dark in if (marketing) listOf(true) else listOf(false, true)) {
                    for ((route, state) in cases) {
                        compose.runOnIdle { scene = Scene(route, state, large, dark) }
                        compose.mainClock.advanceTimeBy(1_000)
                        compose.waitForIdle()
                        val name = "$route-$state-${if (large) "large" else "phone"}-${if (dark) "dark" else "light"}.png"
                        val bitmap = if (route == "actions") {
                            compose.onNodeWithText("Action pages").assertIsDisplayed()
                            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        } else {
                            compose.onNodeWithTag("review-capture").captureToImage().asAndroidBitmap()
                        }
                        File(output, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    }
                }
            }
        } finally {
            compose.runOnUiThread { store.clear() }
        }
    }

    @Composable private fun navigation(scene: Scene, rail: Boolean) {
        val selected = when (scene.route) {
            "setup", "customize" -> "gauge"
            "alerts", "alert-settings", "car-advanced" -> "car"
            "updates" -> "settings"
            else -> scene.route
        }
        (if (marketing) listOf("gauge", "car", "settings") else listOf("gauge", "car", "settings", "expert")).forEach { route ->
            val icon = when (route) {
                "gauge" -> GaugeIcon.Gauge
                "car" -> GaugeIcon.Car
                "settings" -> GaugeIcon.Settings
                else -> GaugeIcon.Tools
            }
            if (rail) NavigationRailItem(selected == route, {}, { EGaugeIcon(icon) }, label = { Text(route.replaceFirstChar(Char::uppercase)) })
        }
    }

    @Composable private fun RowScope.navigationBar(scene: Scene) {
        val selected = when (scene.route) {
            "setup", "customize" -> "gauge"
            "alerts", "alert-settings", "car-advanced" -> "car"
            "updates" -> "settings"
            else -> scene.route
        }
        (if (marketing) listOf("gauge", "car", "settings") else listOf("gauge", "car", "settings", "expert")).forEach { route ->
            val icon = when (route) {
                "gauge" -> GaugeIcon.Gauge
                "car" -> GaugeIcon.Car
                "settings" -> GaugeIcon.Settings
                else -> GaugeIcon.Tools
            }
            NavigationBarItem(selected == route, {}, { EGaugeIcon(icon) }, label = { Text(route.replaceFirstChar(Char::uppercase)) })
        }
    }

    @Composable private fun screen(scene: Scene, model: AppViewModel) {
        when (scene.route) {
            "gauge" -> if (scene.state == "permission-denied") {
                HomeScreen(HomeUiState("eGauge", "Allow nearby devices", false, connectionStatus(ConnectionState(phase = ConnectionPhase.PermissionRequired), 0), emptyList(), false, "Set up gauge", HomeAction.SetUp, false, emptyList()), {}, {})
            } else {
                if (marketing) {
                    HomeScreen(HomeUiState("eGauge", "Gauge ready", true, StatusUi("Gauge ready", tone = StatusTone.Success), ScreenFixtures.customize.pages, false, "Customize", HomeAction.Customize, false, emptyList(), showStatus = false), {}, {})
                } else {
                    ScreenFixtures.Screen("gauge", false)
                }
            }

            "actions" -> ActionPagesSheet(
                car.copy(
                    canEditActions = false,
                    actionsSupported = true,
                    commandsSupported = true,
                    actions = listOf(PageAction(type = PageActionType.VehicleActions, winchSeconds = 60)),
                    idlePresets = listOf(IdlePreset(IdlePresetId.AirUp, seconds = 300), IdlePreset(IdlePresetId.Winch, seconds = 60)),
                    idleCoolantLimitC = 105.0
                ),
                { _, _ -> error("Capture cannot save vehicle configuration") },
                {}
            )

            "car" -> CarScreen(car, {}, {}, {}, currentAlerts = listOf(CarAttentionAlert("coolant", "Coolant above your limit", "108 °C · Limit 105 °C", 3)), hasRecordedHistory = true)

            "settings" -> SettingsScreen(SettingsUiState("eGauge", true, 0, true, false, true, false, BuildConfig.VERSION_NAME, emptyList(), displaySettingsVersion = 3, brightness = 80, cycleSeconds = 10, gauges = listOf(GaugeUi("example", "eGauge", "Example car", "Engine")), selectedGaugeId = "example", smartCycleSupported = true, autoOrientationSupported = true, orientationProtocolSupported = true), {}, {}, {}, {}, { _, _ -> }, {}, {}, {})

            "expert", "car-advanced" -> when (scene.state) {
                "hidden-codes" -> HiddenFaultCodesScreen(expert.copy(diagnosticFilters = listOf(DiagnosticCodeFilters.rule("ECM", "P0420")), filterTargets = listOf(DetailUi("Example gauge", "Review and send"))), actions, {}, {})
                "second-adapter" -> ExpertScreen(expert.copy(hasTransmission = true, primaryAdapter = AdapterBinding("example-engine", "00:00:00:00:00:01", "public"), secondAdapter = AdapterBinding("example-transmission", "00:00:00:00:00:02", "public"), bothAdapters = true, canUseBothAdapters = true), actions, carAdvanced = true)
                else -> ExpertScreen(expert, actions, carAdvanced = scene.route == "car-advanced")
            }

            "setup" -> if (scene.state == "loading") ScreenFixtures.Screen("setup", false) else SetupScreen(SetupUiState(true, OwnerAccess.DISCOVERED, false, listOf(CandidateUi("synthetic-gauge", "eGauge", "EXAMPLE", emptyList())), StatusUi("Gauge found", "Example pairing scene, no radio connection."), emptyList()), {}, {}, {}, {}, {}, {}, {})

            "customize" -> DashboardEditorScreen(ScreenFixtures.customize, if (scene.state == "review") 5 else 0, {}, {}, ScreenFixtures.customizeActions)

            "alerts" -> if (scene.state == "synthetic-current") {
                val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 108f, 105f, "Coolant", "degC", 0, 1)
                val entry = StoredAlert("example", "example", "synthetic-gauge", 1000, true, event, JSONObject())
                Column {
                    ScreenTitle("Alerts")
                    TabRow(0) {
                        Tab(true, {}, text = { Text("Current") })
                        Tab(false, {}, text = { Text("History") })
                    }
                    AlertWorkspaceList(listOf(AlertWorkspaceRow(entry)), false, rememberLazyListState(), Modifier.weight(1f), { true }, { alertAttentionState(it, true, true) }, {}, {}, {})
                }
            } else {
                CarAlertWorkspace(model, {}, {})
            }

            "alert-settings" -> CarAlertSettingsScreen(model, ScreenFixtures.customize, {}, {}, {})

            "updates" -> if (scene.state == "error") UpdatesScreen(UpdatesUiState(StatusUi("Could not check for updates", "Try again when your connection is available.", StatusTone.Error), null, "Example dev.75", true, false, false, false, false, emptyList(), feedUnavailable = true), operationUi(OperationState(0, OperationKind.READ, OperationStage.IDLE, "")), {}, {}, {}, {}, {}) else ScreenFixtures.Screen("updates", false)
        }
    }
}
