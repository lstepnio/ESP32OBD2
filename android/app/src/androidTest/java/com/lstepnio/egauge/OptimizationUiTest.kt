package com.lstepnio.egauge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ConnectionStatusWidget
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.car.FaultCodeExplanation
import com.lstepnio.egauge.ui.home.HomeScreen
import com.lstepnio.egauge.ui.settings.SettingsScreen
import com.lstepnio.egauge.ui.setup.SetupScreen
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Rendered fixtures only. No gauge, adapter or saved-profile writes. */
@RunWith(AndroidJUnit4::class)
class OptimizationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun faultCodesAreVisibleBeforeOpeningAnExplanation() {
        val codes = listOf("U0121", "U0140", "P1DCA", "P1DF3").map { FaultUi(it, "", "Stored") }
        val source = FaultSourceUi("Transmission", StatusUi("Warning", tone = StatusTone.Warning), listOf(FaultCategoryUi("Stored", "", codes)))
        val state = CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", source.status, emptyList(), true, false, false, emptyList(), faultSources = listOf(source))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalConnectionStatus provides { ConnectionPill("Gauge ready", true, onClick = {}) }) {
                EGaugeTheme(dark = true) {
                    Surface(Modifier.requiredSize(390.dp, 1000.dp).testTag("fault-frame")) { CarScreen(state, {}, {}, {}) }
                }
            }
        }
        compose.onNodeWithText("U0121 · Transmission").performScrollTo().assertIsDisplayed()
        captureFaultScreen("car-faults")
        compose.onNodeWithText("U0121 · Transmission").performClick()
        compose.onNodeWithText("What it means").assertExists()
        compose.onNodeWithText("Next step").performScrollTo().assertIsDisplayed()
        captureFaultScreen("fault-explanation")
        compose.onNodeWithContentDescription("Go back").performScrollTo().performClick()
        compose.onNodeWithText("View all").performScrollTo().performClick()
        compose.onNodeWithText("P1DF3 · Transmission").performScrollTo().performClick()
        compose.onNode(hasText("Controller not initialized") and hasAnyAncestor(hasTestTag("fault-explanation"))).assertExists()
        compose.onNodeWithText("Next step").performScrollTo().assertIsDisplayed()
    }

    @Test fun savedCodesUseTheSameLookupAndStayClearlyHistorical() {
        val fault = CarFaultUi("U0140", "", "Transmission", "Stored", listOf("Stored"), false)
        compose.setContent { EGaugeTheme { Column { FaultCodeExplanation(fault, recorded = true) } } }
        compose.onNodeWithText("U0140 · Transmission").assertIsDisplayed()
        compose.onNodeWithText("What it means").assertExists()
        compose.onNodeWithText("Saved with this alert. This is not a live fault check.").assertExists()
    }

    private fun captureFaultScreen(name: String) {
        val directory = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = java.io.File(directory, "fault-experience/$name.png")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        val bitmap = if (name == "fault-explanation") compose.onNodeWithTag("fault-explanation").captureToImage() else compose.onNodeWithTag("fault-frame").captureToImage()
        file.outputStream().use { check(bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun compactHeaderKeepsItsTitleOnOneLineAndThePillReachable() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                EGaugeTheme {
                    Surface(Modifier.requiredSize(336.dp, 300.dp)) {
                        ScreenTitle("Review and send", {
                        }, { ConnectionPill("Car reconnecting", false, onClick = {}) })
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(
            "Review and send"
        ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText("Car reconnecting").assertIsDisplayed()
    }

    @Test fun connectionWidgetAnnouncesEveryRequiredLinkWithoutHidingTheFailedChild() {
        val links =
            listOf(
                ConnectionLinkUi(
                    "ecm",
                    "Engine adapter",
                    StatusUi("Car connected", tone = StatusTone.Success)
                ),
                ConnectionLinkUi(
                    "tcm",
                    "Transmission adapter",
                    StatusUi("Connecting to car", tone = StatusTone.Loading)
                )
            )
        compose.setContent {
            EGaugeTheme {
                ConnectionStatusWidget("Gauge ready", true, links, UpdateNotice.None, {})
            }
        }
        compose.onNodeWithContentDescription(
            "Phone to gauge: Gauge ready. Engine adapter: Car connected. Transmission adapter: Connecting to car"
        )
            .assertIsDisplayed().assertHasClickAction()
    }

    @Test fun partialConnectionKeepsWorkingFaultDirectAndUnavailableSiblingQuiet() {
        val engine =
            FaultSourceUi(
                "Engine",
                StatusUi("Engine warning is off", tone = StatusTone.Success),
                listOf(
                    FaultCategoryUi(
                        "Stored",
                        "",
                        listOf(FaultUi("P0301", "Engine misfire", "Stored"))
                    )
                )
            )
        val child =
            FaultSourceUi(
                "Transmission",
                StatusUi("Warning status unavailable", tone = StatusTone.Stale),
                listOf(
                    FaultCategoryUi(
                        "Pending",
                        "",
                        listOf(FaultUi("P0700", "Transmission fault", "Last checked · Pending", current = false))
                    )
                )
            )
        val state =
            CarUiState(
                "Jeep",
                listOf(
                    VehicleUi("jeep", "Jeep")
                ),
                "jeep", vehicleFaultStatus(listOf(engine, child)),
                emptyList(), true, false, false, emptyList(), faultSources = listOf(engine, child)
            )
        compose.setContent { EGaugeTheme { CarScreen(state, {}, {}, {}) } }
        compose.onAllNodesWithText("Fault checks incomplete").assertCountEquals(0)
        compose.onAllNodesWithText("Transmission checks are unavailable or out of date.", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Pending").assertCountEquals(0)
        compose.onNodeWithText("P0301 · Engine").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("P0700", substring = true).assertCountEquals(0)
        compose.onNodeWithText("P0301 · Engine").performClick()
        compose.onNodeWithText("What it means").assertExists()
        compose.onNodeWithText("Next step").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Check coverage").assertCountEquals(0)
    }

    @Test fun largeTextCarKeepsAdapterChoicesScrollableAndVehicleSwitchClosesCodes() {
        val fault = FaultUi("P0301", "Engine misfire", "Stored")
        var state by mutableStateOf(
            CarUiState(
                "Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep",
                StatusUi(
                    "Warning",
                    tone = StatusTone.Warning
                ),
                listOf(fault), true, false, false, emptyList(),
                adapterAvailable = true, adapterSelected = "Vehicle adapter"
            )
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                EGaugeTheme {
                    Surface(Modifier.requiredSize(390.dp, 844.dp)) { CarScreen(state, {}, {}, {}) }
                }
            }
        }
        compose.onNodeWithText("P0301 · Vehicle").performScrollTo().performClick()
        compose.onNode(hasText("P0301 · Vehicle") and hasAnyAncestor(hasTestTag("fault-explanation"))).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            state =
                state.copy(activeId = "other", name = "Other car", faults = emptyList())
        }
        compose.onAllNodesWithText("P0301 · Vehicle").assertCountEquals(0)
        compose.onNodeWithContentDescription("Car settings").performClick()
        compose.onNodeWithText("Adapter").performScrollTo().performClick()
        compose.onNodeWithText("Remove adapter").performScrollTo().assertIsDisplayed()
    }

    @Test fun rememberedGaugeReconnectDoesNotAskForSetupAgain() {
        val state = CarUiState(
            "Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep",
            StatusUi(
                "Waiting",
                tone = StatusTone.Disabled
            ),
            emptyList(), false, false, false, emptyList(), gaugeKnown = true
        )
        compose.setContent { EGaugeTheme { CarScreen(state, {}, {}, {}) } }
        compose.onAllNodesWithText("Set up gauge").assertCountEquals(0)
        compose.onAllNodesWithText("Choose adapter").assertCountEquals(0)
        compose.onAllNodesWithText("Waiting for car").assertCountEquals(0)
        compose.onAllNodesWithText("Needs attention").assertCountEquals(0)
        compose.onAllNodesWithText("No active alerts").assertCountEquals(0)
    }

    @Test fun ordinaryReconnectionDoesNotAddAnotherHomeStatusCard() {
        val home = HomeUiState(
            "eGauge", "Reconnecting", false,
            StatusUi("Reconnecting to your gauge", tone = StatusTone.Offline),
            listOf(GaugePageDraft("one", "RPM", GaugeLayout.Numeric, listOf("rpm"))).map(::pageUi),
            false, "Customize", HomeAction.Customize, false, emptyList(), showStatus = false
        )
        compose.setContent { EGaugeTheme { HomeScreen(home, {}, {}) } }
        compose.onNodeWithText("Reconnecting").assertExists()
        compose.onAllNodesWithText("Reconnecting to your gauge").assertCountEquals(0)
    }

    @Test fun changingGaugeClosesASettingsEditorRatherThanApplyingItsOldChoice() {
        var state by mutableStateOf(
            SettingsUiState(
                "First", true, 0, true, false, false, false, "test", emptyList(),
                displaySettingsVersion = 3, brightness = 80, selectedGaugeId = "first"
            )
        )
        compose.setContent {
            EGaugeTheme { SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) }
        }
        compose.onNodeWithText("Brightness").performScrollTo().performClick()
        compose.onNodeWithText("Display brightness").assertIsDisplayed()
        compose.runOnIdle {
            state =
                state.copy(name = "Second", selectedGaugeId = "second", brightness = 20)
        }
        compose.onAllNodesWithText("Display brightness").assertCountEquals(0)
    }

    @Test fun pairingCompletionPointsToTheImplementedAdapterWorkflow() {
        val state =
            SetupUiState(
                true,
                OwnerAccess.AUTHENTICATED,
                false,
                emptyList(),
                StatusUi("Paired"),
                emptyList()
            )
        var car = 0
        compose.setContent {
            EGaugeTheme { SetupScreen(state, {}, {}, {}, {}, {}, {}, {}, { car++ }) }
        }
        compose.onAllNodesWithText("Adapter setup is not available yet").assertCountEquals(0)
        compose.onNodeWithText(
            "Select the adapter plugged into your car. You can customize your pages any time."
        ).assertExists()
        compose.onNodeWithText("Choose adapter").performClick()
        assertEquals(1, car)
    }

    @Test fun androidBondCompletionWaitsForAutomaticOwnerReadWithoutARefreshTap() {
        val state = SetupUiState(
            true,
            OwnerAccess.DISCOVERED,
            false,
            emptyList(),
            StatusUi("Checking gauge access", tone = StatusTone.Loading),
            emptyList(),
            androidBonded = true
        )
        compose.setContent { EGaugeTheme { SetupScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Checking gauge access").assertExists()
        compose.onAllNodesWithText("Check gauge access").assertCountEquals(0)
    }

    @Test fun orientationProtocolKeepsManualWritesAvailableWhileSensorBecomesReady() {
        var state by mutableStateOf(
            SettingsUiState(
                "eGauge", true, 2, true, false, false, false, "test", emptyList(),
                displaySettingsVersion = 3, brightness = 80, selectedGaugeId = "starting-sensor",
                orientationProtocolSupported = true
            )
        )
        val saves = mutableListOf<Pair<Boolean, Int>>()
        var legacySaves = 0
        compose.setContent {
            EGaugeTheme {
                SettingsScreen(
                    state, {}, {}, {}, { legacySaves++ }, { _, _ -> legacySaves++ }, {}, {}, {},
                    onSaveOrientation = { automatic, angle -> saves += automatic to angle }
                )
            }
        }
        compose.onNodeWithText("Rotation").performScrollTo().performClick()
        compose.onAllNodesWithText("Auto").assertCountEquals(0)
        compose.onNode(hasText("180°") and isSelected()).assertIsDisplayed()
        compose.onNodeWithText("Rotate gauge").performClick()
        compose.runOnIdle {
            assertEquals(listOf(false to 2), saves)
            assertEquals(0, legacySaves)
        }
        compose.runOnIdle { state = state.copy(automaticOrientation = false, autoOrientationSupported = true) }
        compose.onNodeWithText("Rotation").performScrollTo().performClick()
        compose.onNodeWithText("Auto").performClick()
        compose.onNodeWithText("Rotate gauge").performClick()
        compose.runOnIdle {
            assertEquals(listOf(false to 2, true to 2), saves)
            assertEquals(0, legacySaves)
        }
    }

    @Test fun autoOrientationIsOfferedOnlyBySupportedHardwareAndManualSelectionExitsAuto() {
        var state by mutableStateOf(
            SettingsUiState(
                "eGauge", true, 2, true, false, false, false, "test", emptyList(),
                displaySettingsVersion = 3, brightness = 80, selectedGaugeId = "orientation-gauge"
            )
        )
        val saves = mutableListOf<Pair<Boolean, Int>>()
        compose.setContent {
            EGaugeTheme {
                SettingsScreen(
                    state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {},
                    onSaveOrientation = { automatic, angle -> saves += automatic to angle }
                )
            }
        }
        compose.onNodeWithText("Rotation").performScrollTo().performClick()
        compose.onAllNodesWithText("Auto").assertCountEquals(0)
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { state = state.copy(orientationProtocolSupported = true, autoOrientationSupported = true, automaticOrientation = true, orientationSensorStale = true) }
        compose.onNodeWithText("Auto · holding orientation").assertExists()
        compose.onNodeWithText("Rotation").performScrollTo().performClick()
        compose.onNodeWithText("Auto").assertExists()
        compose.onNodeWithText("Holding the last orientation until the sensor recovers.").assertExists()
        compose.onNodeWithText("180°").performClick()
        compose.onNodeWithText("Rotate gauge").performClick()
        compose.runOnIdle { assertEquals(listOf(false to 2), saves) }
    }

    @Test fun smartCyclingRetainsDisabledPreferenceAndSavesBothFieldsTogether() {
        var state by mutableStateOf(
            SettingsUiState(
                "eGauge", true, 0, true, false, false, false, "test", emptyList(),
                displaySettingsVersion = 3, brightness = 80, cycleSeconds = 10,
                selectedGaugeId = "smart-gauge", smartCycleSupported = true, smartCycle = true
            )
        )
        val saves = mutableListOf<Pair<Int, Boolean>>()
        compose.setContent {
            EGaugeTheme {
                SettingsScreen(
                    state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {},
                    onSaveSmartCycle = { seconds, smart ->
                        saves += seconds to smart
                        state = state.copy(cycleSeconds = seconds, smartCycle = smart)
                    }
                )
            }
        }
        compose.onNodeWithText("Every 10 seconds · Smart").assertExists()
        compose.onNodeWithText("Auto-cycle pages").performScrollTo().performClick()
        compose.onNodeWithText("Smart").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(saves.isEmpty()) }
        compose.onNodeWithText("Every 10 seconds · Smart").assertExists()
        compose.onNodeWithText("Auto-cycle pages").performScrollTo().performClick()
        compose.onNodeWithText("Smart").performScrollTo().assertIsOn()
        compose.onNodeWithText("Off", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Smart").performScrollTo().assertIsNotEnabled().assertIsOn()
        compose.onNodeWithText("Choose an interval to use Smart.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save to gauge").performClick()
        compose.runOnIdle { assertEquals(listOf(0 to true), saves) }
        compose.onNodeWithText("Auto-cycle pages").performScrollTo().performClick()
        compose.onNodeWithText("Smart").performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Every 10 seconds").performScrollTo().performClick()
        compose.onNodeWithText("Smart").performScrollTo().assertIsEnabled().assertIsOn()
        compose.onNodeWithText("Save to gauge").performClick()
        compose.runOnIdle { assertEquals(listOf(0 to true, 10 to true), saves) }
        compose.onNodeWithText("Every 10 seconds · Smart").assertExists()
        compose.onNodeWithText("Auto-cycle pages").performScrollTo().performClick()
        compose.onNodeWithText("Smart").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("Save to gauge").performClick()
        compose.runOnIdle { assertEquals(listOf(0 to true, 10 to true, 10 to false), saves) }
        compose.onNodeWithText("Every 10 seconds · Smart").assertDoesNotExist()
        compose.onNodeWithText("Every 10 seconds").assertExists()
    }

    @Test fun legacyCyclingEditorKeepsItsExistingSaveCallbackWithoutSmart() {
        var saved: Int? = null
        val state = SettingsUiState(
            "eGauge", true, 0, true, false, false, false, "test", emptyList(),
            displaySettingsVersion = 3, brightness = 80, cycleSeconds = 10
        )
        compose.setContent {
            EGaugeTheme {
                SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, onSaveCycle = { saved = it })
            }
        }
        compose.onNodeWithText("Auto-cycle pages").performScrollTo().performClick()
        compose.onNodeWithText("Smart").assertDoesNotExist()
        compose.onNodeWithText("Every 15 seconds").performScrollTo().performClick()
        compose.onNodeWithText("Save to gauge").performClick()
        compose.runOnIdle { assertEquals(15, saved) }
    }

    @Test fun largeTextDialogKeepsTheLastCycleChoiceAndSaveReachable() {
        val state =
            SettingsUiState(
                "eGauge", true, 0, true, false, false, false, "test", emptyList(),
                displaySettingsVersion = 3, brightness = 80, cycleSeconds = 0
            )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                EGaugeTheme {
                    Surface(Modifier.requiredSize(390.dp, 844.dp)) {
                        SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Auto-cycle pages").performScrollTo().performClick()
        compose.onNodeWithText("Every 60 seconds").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save to gauge").assertIsDisplayed()
    }

    @Test fun startupKeepsConnectionWorkInThePillWithoutHealthOrIncompleteTasks() {
        val state = CarUiState(
            "Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep",
            StatusUi("Waiting for car", tone = StatusTone.Loading), emptyList(),
            false, true, false, emptyList(), gaugeKnown = true,
            faultSources = listOf(FaultSourceUi("Engine", StatusUi("Checking", tone = StatusTone.Loading), emptyList()))
        )
        compose.setContent {
            EGaugeTheme {
                CompositionLocalProvider(LocalConnectionStatus provides { ConnectionPill("Car reconnecting", false, onClick = {}) }) {
                    CarScreen(state, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Car reconnecting").assertIsDisplayed()
        listOf("Vehicle health", "Needs attention", "Fault checks incomplete", "Waiting for car", "No active alerts").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
        compose.onNodeWithText("History").performScrollTo().assertIsDisplayed()
    }

    @Test fun completeHiddenOnlyCheckHasNoAttentionCardCodeOrHiddenCount() {
        val raw = GaugeConfigTransferClient.Diagnostics(
            true, true, 1, true, 1, "P0420", true, 0, null, true, 0, null,
            milAgeMs = 0,
            categories = listOf("Stored", "Pending", "Permanent").mapIndexed { index, name ->
                DiagnosticCategory(name, DiagnosticAvailability.Available, true, true, if (index == 0) listOf("P0420") else emptyList(), 0)
            }
        )
        val engine = diagnosticSources(raw, 0, "ECM", listOf(DiagnosticCodeFilter("ECM", "P0420"))).filter { it.title == "Engine" }
        val state = CarUiState(
            "Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", vehicleFaultStatus(engine),
            emptyList(), true, false, false, emptyList(), faultSources = engine
        )
        compose.setContent { EGaugeTheme { CarScreen(state, {}, {}, {}) } }
        compose.onNodeWithText("No active alerts").assertIsDisplayed()
        listOf("Needs attention", "P0420", "hidden", "filtered").forEach {
            compose.onAllNodesWithText(it, substring = true, ignoreCase = true, useUnmergedTree = true).assertCountEquals(0)
        }
        assertEquals(listOf("P0420"), raw.categories!!.first().codes)
    }

    @Test fun carUtilitiesAndSettingsDispatchTheirDirectDestinations() {
        val calls = mutableListOf<String>()
        val state = CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", StatusUi("Waiting"), emptyList(), false, false, false, emptyList(), gaugeKnown = true)
        compose.setContent {
            EGaugeTheme {
                CarScreen(state, {}, {}, {}, onHistory = { calls += "history" }, onAlertSettings = { calls += "alerts" }, onAdvanced = { calls += "advanced" }, onGaugeAssignment = { calls += "gauge" })
            }
        }
        compose.onNodeWithText("History").performScrollTo().performClick()
        compose.onNodeWithText("Alert settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Car settings").performScrollTo().performClick()
        compose.onNodeWithText("Gauge").performScrollTo().performClick()
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        assertEquals(listOf("history", "alerts", "gauge", "advanced"), calls)
        compose.onNodeWithContentDescription("Go back").performScrollTo().performClick()
        compose.onNodeWithText("History").performScrollTo().assertIsDisplayed()
    }

    @Test fun attentionEpisodeOpensDirectlyAndPreviousActivityDoesNotInventDriveOrCount() {
        val calls = mutableListOf<String>()
        var alerts by mutableStateOf(listOf(CarAttentionAlert("coolant", "Coolant temperature", "108 °C · Limit 105 °C", 3)))
        val state = CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", StatusUi("Waiting"), emptyList(), false, false, false, emptyList(), gaugeKnown = true)
        compose.setContent {
            EGaugeTheme {
                CarScreen(state, {}, {}, {}, currentAlerts = alerts, hasRecordedHistory = true, onOpenAlert = { calls += it }, onHistory = { calls += "history" })
            }
        }
        compose.onNodeWithText("Coolant temperature").performScrollTo().performClick()
        assertEquals(listOf("coolant"), calls)
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithText("Previous activity").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("No active alerts").assertCountEquals(0)
        compose.onNodeWithText("Previous activity").performScrollTo().performClick()
        assertEquals(listOf("coolant", "history"), calls)
    }

    @Test fun unknownFaultOpensItsHonestExplanationInOneTap() {
        val source = FaultSourceUi("Engine", StatusUi("Warning", tone = StatusTone.Warning), listOf(FaultCategoryUi("Stored", "", listOf(FaultUi("P1999", "", "Stored")))))
        val state = CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", source.status, emptyList(), true, false, false, emptyList(), faultSources = listOf(source))
        compose.setContent { EGaugeTheme { CarScreen(state, {}, {}, {}) } }
        compose.onNodeWithText("P1999 · Engine").performScrollTo().performClick()
        compose.onNodeWithText("A verified explanation for P1999 is not included. Its meaning may depend on the vehicle and controller.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next step").performScrollTo().assertIsDisplayed()
    }

    @Test fun lastKnownCodeRemainsDirectlyReachableWithoutClaimingCurrentFault() {
        val source = FaultSourceUi("Engine", StatusUi("Last checked", tone = StatusTone.Stale), listOf(FaultCategoryUi("Stored", "", listOf(FaultUi("P0700", "", "Stored", current = false)))))
        val state = CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", source.status, emptyList(), false, true, false, emptyList(), faultSources = listOf(source))
        compose.setContent { EGaugeTheme { CarScreen(state, {}, {}, {}, hasRecordedHistory = true) } }
        compose.onNodeWithText("P0700 · Engine · Last checked").performScrollTo().performClick()
        compose.onNodeWithText("Last checked. This code has not been confirmed cleared.").performScrollTo().assertIsDisplayed()
    }
}
