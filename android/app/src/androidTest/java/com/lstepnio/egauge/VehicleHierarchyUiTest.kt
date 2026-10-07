package com.lstepnio.egauge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.expert.*
import com.lstepnio.egauge.ui.settings.SettingsScreen
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pure examples, without AppViewModel, saved phone data or BLE. */
@RunWith(AndroidJUnit4::class)
class VehicleHierarchyUiTest {
    @get:Rule val compose = createComposeRule()
    private fun frame(content: @Composable () -> Unit) {
        compose.setContent { EGaugeTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Surface(Modifier.size(390.dp, 1000.dp)) { content() }
            }
        } }
    }
    @Test fun transmissionIsAnExpertChildWithExplicitRemoval() {
        var source = ""
        var removed = false
        frame { ExpertScreen(ExpertUiState(false, false, false, emptyList(), vehicleName = "Jeep",
            hasPrimaryAdapter = true, hasTransmission = true, selectedSource = "TCM", canEditVehicle = true),
            ExpertActions({}, {}, {}, {}, {}, selectSource = { source = it }, removeTransmission = { removed = true })) }
        compose.onNodeWithText("Child adapter").performScrollTo().performClick()
        assertEquals("TCM", source)
        compose.onNodeWithText("Remove transmission child").performScrollTo().performClick()
        assertEquals(false, removed)
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        assertEquals(true, removed)
    }
    @Test fun savedGaugePickerShowsVehicleAndSourceAndDispatchesOnlyTheChosenIdentity() {
        var selected = ""
        frame { SettingsScreen(SettingsUiState("Main gauge", false, null, false, false, false, false, "Example", emptyList(),
            gauges = listOf(GaugeUi("one", "Main gauge", "Jeep", "ECM"), GaugeUi("two", "TCM gauge", "Jeep", "TCM")),
            selectedGaugeId = "one"), {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, onSelectGauge = { selected = it }) }
        compose.onNodeWithText("Your gauges").performClick()
        compose.onAllNodesWithText("Transmission", substring = true).assertCountEquals(0)
        compose.onNodeWithText("TCM gauge").performClick()
        assertEquals("two", selected)
    }
    @Test fun carAlwaysEditsThePrimaryVehicleAdapter() {
        frame { CarScreen(CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep")), "jeep", StatusUi("Waiting for car", "Checks run automatically"), emptyList(), false, false, false, emptyList(), adapterAvailable = true), {}, {}, {}) }
        compose.onNodeWithText("Choose adapter").performScrollTo().performClick()
        compose.onNodeWithText("Vehicle adapter").assertIsDisplayed()
        compose.onAllNodesWithText("Transmission child").assertCountEquals(0)
    }
    @Test fun bothAdaptersAreExplicitAndGatedInExpert() {
        var selected = false
        frame { ExpertScreen(ExpertUiState(false, false, false, emptyList(), vehicleName = "Jeep",
            hasPrimaryAdapter = true, hasTransmission = true, canEditVehicle = true, canUseBothAdapters = true),
            ExpertActions({}, {}, {}, {}, {}, useBoth = { selected = it })) }
        compose.onNodeWithText("Use both adapters on this gauge").performScrollTo().performClick()
        assertEquals(true, selected)
    }
    @Test fun oldFirmwareCannotEnableBothAdapters() {
        frame { ExpertScreen(ExpertUiState(false, false, false, emptyList(), vehicleName = "Jeep",
            hasPrimaryAdapter = true, hasTransmission = true, canEditVehicle = true), ExpertActions({}, {}, {}, {}, {})) }
        compose.onNodeWithText("Use both adapters on this gauge").performScrollTo().assertIsNotEnabled()
    }

}
