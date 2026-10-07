package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.settings.SettingsScreen
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated examples: no AppViewModel, Bluetooth, gauge or saved phone settings. */
@RunWith(AndroidJUnit4::class)
class ConnectionStatusUiTest {
    @get:Rule val compose = createComposeRule()
    private val ready = StatusUi("Car connected", "Adapter connected.", StatusTone.Success)
    private fun frame(content: @Composable () -> Unit) {
        compose.setContent {
            EGaugeTheme(dark = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalConnectionStatus provides {
                    ConnectionStatusWidget("Gauge ready", true,
                        listOf(ConnectionLinkUi("engine", "Engine adapter", ready)), UpdateNotice.None, {})
                }) {
                    Surface(Modifier.size(390.dp, 820.dp).testTag("review")) { content() }
                }
            }
        }
    }
    @Test fun carShowsRelevantFaultsWithoutManualRefreshOrInactiveSource() {
        val fault = FaultUi("P0301", faultDescription("P0301"), "Stored")
        val category = FaultCategoryUi("Stored", "Checked just now", listOf(fault))
        val state = CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep",
            StatusUi("Engine reports a warning", "Review the reported codes.", StatusTone.Critical),
            listOf(fault), true, false, false, emptyList(), adapterAvailable = true,
            adapterSelected = "Vehicle adapter", faultSources = listOf(FaultSourceUi("Engine", ready, listOf(category))),
            connectionStatus = ready)
        frame { CarScreen(state, {}, {}, {}) }
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("P0301").assertIsDisplayed()
        listOf("Check faults", "Check adapter", "Transmission", "Find adapter").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
        capture("car")
        compose.onAllNodesWithText("Check coverage").assertCountEquals(0)
    }
    @Test fun settingsUsesTheSameWidgetAndReadsWithoutATap() {
        val state = SettingsUiState("eGauge", true, 1, true, false, false, false, "Example", emptyList(),
            displaySettingsVersion = 3, brightness = 100, measurementSystem = MeasurementSystem.Imperial, cycleSeconds = 10)
        frame { SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) }
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("90°").assertIsDisplayed()
        compose.onNodeWithText("Imperial").assertIsDisplayed()
        compose.onAllNodesWithText("Check current settings").assertCountEquals(0)
        capture("settings")
    }
    @Test fun failedSettingsReadsDisableEditsAndRetryWithoutRefreshButtons() {
        val state = SettingsUiState("eGauge", true, 1, true, false, false, false, "Example", emptyList(),
            displaySettingsVersion = 3, brightness = 100, cycleSeconds = 10, settingsCurrent = false)
        frame { SettingsScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) }
        compose.onNodeWithText("Brightness").assertIsNotEnabled()
        compose.onNodeWithText("Rotation").assertIsNotEnabled()
        compose.onNodeWithText("Units").assertIsNotEnabled()
        compose.onNodeWithText("Auto-cycle pages").assertIsNotEnabled()
        compose.onNodeWithText("Last checked settings").assertIsDisplayed()
        compose.onAllNodesWithText("Refresh").assertCountEquals(0)
    }
    @Test fun partialDualConnectionCannotShowAllConnected() {
        var presses = 0
        compose.setContent { EGaugeTheme {
            ConnectionStatusWidget("Gauge ready", true, listOf(
                ConnectionLinkUi("engine", "Engine adapter", ready),
                ConnectionLinkUi("transmission", "Transmission adapter", StatusUi("Connecting to car", "", StatusTone.Loading))
            ), UpdateNotice.None, { presses++ })
        } }
        compose.onAllNodesWithText("Connected").assertCountEquals(0)
        compose.onNodeWithText("Car reconnecting").performClick()
        assertEquals(1, presses)
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureConnectionExamples") != "true") return
        compose.waitForIdle()
        val image = compose.onNodeWithTag("review").captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "connection-examples")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
