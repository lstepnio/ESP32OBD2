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
import com.lstepnio.egauge.ui.car.SavedFaultCodes
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
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("View all 4 codes").performScrollTo().performClick()
        compose.onNodeWithText("P1DF3 · Transmission").performScrollTo().performClick()
        compose.onNode(hasText("Code explanation unavailable") and hasAnyAncestor(hasTestTag("fault-explanation"))).assertExists()
        compose.onNodeWithText("Next step").performScrollTo().assertIsDisplayed()
    }

    @Test fun savedCodesUseTheSameLookupAndStayClearlyHistorical() {
        val faults = listOf(CarFaultUi("U0140", "", "Transmission", "Stored", listOf("Stored"), false))
        compose.setContent { EGaugeTheme { Column { SavedFaultCodes(faults, "episode") } } }
        compose.onNodeWithText("U0140 · Transmission · Saved with alert").assertIsDisplayed()
        compose.onNodeWithText("U0140 · Transmission · Saved with alert").performClick()
        compose.onNodeWithText("What it means").assertExists()
        compose.onNodeWithText("Saved with this alert. This is not a live fault check.").assertExists()
        compose.onNodeWithText("Back to saved codes").performClick()
        compose.onNodeWithText("U0140 · Transmission · Saved with alert").assertIsDisplayed()
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

    @Test fun dualFaultsKeepSourceMeaningAndIndependentUnavailableStatus() {
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
                        listOf(FaultUi("P0700", "Transmission fault", "Last checked · Pending"))
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
        compose.onNodeWithText("Fault checks incomplete").assertExists()
        compose.onNodeWithText(
            "Transmission checks are unavailable or out of date.",
            substring = true
        ).assertExists()
        compose.onAllNodesWithText("Pending").assertCountEquals(0)
        compose.onNodeWithText("P0301 · Engine").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("P0700 · Transmission · Last checked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("P0700 · Transmission · Last checked").performClick()
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
        compose.onNode(hasText("P0301 · Vehicle") and hasAnyAncestor(hasTestTag("fault-explanation"))).assertIsDisplayed()
        compose.runOnIdle {
            state =
                state.copy(activeId = "other", name = "Other car", faults = emptyList())
        }
        compose.onAllNodesWithText("P0301 · Vehicle").assertCountEquals(0)
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
        compose.onNodeWithText("Waiting for car").assertExists()
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
}
