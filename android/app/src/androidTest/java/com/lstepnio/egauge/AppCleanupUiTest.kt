package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.customize.DashboardEditorScreen
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import com.lstepnio.egauge.ui.settings.*
import com.lstepnio.egauge.ui.setup.SetupScreen
import com.lstepnio.egauge.ui.state.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated examples only: no saved-phone writes, radio operations or ECU commands. */
@RunWith(AndroidJUnit4::class)
class AppCleanupUiTest {
    @get:Rule val compose = createComposeRule()
    private fun frame(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                EGaugeTheme {
                    Surface(Modifier.requiredSize(390.dp, 1000.dp)) {
                        Column {
                            Text("Example · UI preview")
                            Box(Modifier.weight(1f)) { content() }
                        }
                    }
                }
            }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrument.targetContext.getExternalFilesDir(null), "app-cleanup").apply {
            mkdirs()
        }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG,
                100,
                it
            )
        }
    }

    @Test fun pairingRecoveryAndRenameStayInFocusedSettingsTools() {
        val state =
            SettingsUiState(
                "eGauge", true, 0, true, false, false, false, "Example", emptyList(),
                displaySettingsVersion = 3, brightness = 80, cycleSeconds = 10
            )
        frame { SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) }
        compose.onAllNodesWithText("Gauge name").assertCountEquals(0)
        compose.onAllNodesWithText("Remove from this phone").assertCountEquals(0)
        compose.onAllNodesWithText("Move gauge to another phone").assertCountEquals(0)
        capture("settings")
        compose.onNodeWithText("Pairing help").performScrollTo().performClick()
        compose.onNodeWithText("Remove Bluetooth pairing").assertExists()
        compose.onNodeWithText("Move gauge to another phone").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Your gauges").performScrollTo().performClick()
        compose.onNodeWithText("Gauge name").performScrollTo().performClick()
        compose.onNodeWithText("Name your gauge").assertExists()
    }

    @Test fun enabledPhoneAlertsDoNotClaimTheyAreOffWhenWaiting() {
        val state =
            SettingsUiState("eGauge", false, null, false, false, false, false, "Not checked", emptyList())
        frame { SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, phoneAlerts = true) }
        compose.onNodeWithText(
            "Notifications resume when the gauge is connected."
        ).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(
            "Gauge alerts still appear on the gauge."
        ).assertCountEquals(0)
    }

    @Test fun regularUpdatesShowVersionsWithoutDeveloperOrDuplicateInstallActions() {
        var installs = 0
        frame {
            UpdatesScreen(
                UpdatesUiState(
                    StatusUi("Update ready"), "Example 2", "Example 1",
                    true, true, true, false, false, emptyList()
                ),
                OperationUi(),
                {
                },
                {},
                { installs++ },
                {},
                {}
            )
        }
        compose.onNodeWithText("Installed: Example 1").assertExists()
        compose.onNodeWithText("Available: Example 2").assertExists()
        compose.onAllNodesWithText("Choose signed package").assertCountEquals(0)
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        capture("updates")
        compose.onNodeWithText("Install update").performClick()
        assertEquals(1, installs)
    }

    @Test fun anActiveUpdateDoesNotOfferAnotherInstallButton() {
        val operation =
            operationUi(
                OperationState(
                    1,
                    OperationKind.UPDATE,
                    OperationStage.SENDING,
                    "Sending",
                    progressPercent = 30
                )
            )
        frame {
            UpdatesScreen(
                UpdatesUiState(
                    StatusUi("Sending"), "Example 2", "Example 1",
                    false, false, true, true, false, emptyList()
                ),
                operation,
                {},
                {},
                {},
                {},
                {}
            )
        }
        compose.onAllNodesWithText("Install update").assertCountEquals(0)
        compose.onAllNodesWithTag("primary-action").assertCountEquals(0)
        compose.onNodeWithText("30% sent").assertExists()
    }

    @Test fun updateRecoveryCannotBecomeAnEnabledFilePickerWhenGaugeChecksAreUnavailable() {
        frame {
            UpdatesScreen(
                UpdatesUiState(
                    StatusUi("Check installed version"), null, "Example 1",
                    false, false, false, false, true, emptyList(), feedUnavailable = true
                ),
                OperationUi(),
                {},
                {},
                {},
                {},
                {}
            )
        }
        compose.onNodeWithTag("primary-action").assertIsNotEnabled()
        compose.onAllNodesWithText("Choose signed package").assertCountEquals(0)
        compose.onAllNodesWithText("Try online check").assertCountEquals(0)
    }

    @Test fun carHasOneAdapterEntryAndSearchesOncePerOpening() {
        var searches = 0
        var state by mutableStateOf(
            CarUiState(
                "Example car", listOf(VehicleUi("car", "Example car")), "car",
                StatusUi(
                    "Waiting for car"
                ),
                emptyList(), false, false, false, emptyList(), adapterAvailable = true
            )
        )
        frame { CarScreen(state, {}, {}, {}, onFindAdapters = { searches++ }) }
        compose.onAllNodesWithText("Choose adapter").assertCountEquals(1)
        capture("car")
        compose.onNodeWithText("Choose adapter").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, searches)
        compose.runOnIdle { state = state.copy(adapterMessage = "Looking for adapters") }
        compose.waitForIdle()
        assertEquals(1, searches)
        compose.onNodeWithText("Find adapter").performScrollTo().performClick()
        assertEquals(2, searches)
    }

    @Test fun replacingDiscoveredGaugesCannotSubmitTheOldSelection() {
        var chosen: String? = null
        var state by mutableStateOf(
            SetupUiState(
                false,
                OwnerAccess.UNKNOWN,
                false,
                listOf(CandidateUi("a", "Gauge A", "A", emptyList())),
                StatusUi("Choose gauge"),
                emptyList()
            )
        )
        frame { SetupScreen(state, {}, {}, {}, {}, { chosen = it }, {}, {}) }
        compose.onNodeWithText("Connect to gauge").assertIsEnabled()
        compose.runOnIdle {
            state =
                state.copy(
                    candidates = listOf(
                        CandidateUi("b", "Gauge B", "B", emptyList()),
                        CandidateUi("c", "Gauge C", "C", emptyList())
                    )
                )
        }
        compose.onNodeWithText("Connect to gauge").assertIsNotEnabled()
        compose.onNodeWithText("Gauge B · B").performScrollTo().performClick()
        compose.onNodeWithText("Connect to gauge").performScrollTo().performClick()
        assertEquals("b", chosen)
    }

    @Test fun alertEditorHandlesAnUnavailableReadingCatalogWithoutCrashing() {
        frame {
            DashboardEditorScreen(ScreenFixtures.customize.copy(readings = emptyList()), 4, {
            }, {}, ScreenFixtures.customizeActions)
        }
        compose.onNodeWithText("No readings available").assertExists()
        compose.onNodeWithText("Back to pages").assertExists()
    }
}
