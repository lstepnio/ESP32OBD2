package com.lstepnio.egauge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.expert.*
import com.lstepnio.egauge.ui.settings.SettingsScreen
import com.lstepnio.egauge.ui.state.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pure examples, without AppViewModel, saved phone data or BLE. */
@RunWith(AndroidJUnit4::class)
class VehicleHierarchyUiTest {
    @get:Rule val compose = createComposeRule()
    private fun frame(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            EGaugeTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    Surface(Modifier.size(390.dp, 1000.dp)) { content() }
                }
            }
        }
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitForIdle()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "expert-$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun expertMenuRemainsScrollableAtLargeText() {
        frame(fontScale = 1.8f) {
            ExpertScreen(
                ExpertUiState(
                    false,
                    false,
                    false,
                    emptyList(),
                    vehicleName = "Jeep",
                    vehicleId = "jeep",
                    hasPrimaryAdapter = true,
                    canEditVehicle = true
                ),
                ExpertActions({}, {}, {}, {})
            )
        }
        compose.onNodeWithText("Device data").performScrollTo().assertIsDisplayed()
        capture("large-text")
    }

    @Test fun expertKeepsRawDataAndRecoveryActionsInsideFocusedTools() {
        frame {
            ExpertScreen(
                ExpertUiState(
                    false,
                    false,
                    false,
                    emptyList(),
                    vehicleName = "Jeep",
                    vehicleId = "jeep",
                    hasPrimaryAdapter = true,
                    canEditVehicle = true
                ),
                ExpertActions({
                }, {}, {}, {})
            )
        }
        compose.onAllNodesWithText("Reload gauge data").assertCountEquals(0)
        compose.onAllNodesWithText("Hardware information").assertCountEquals(0)
        capture("menu")
        compose.onNodeWithText("Device data").performScrollTo().performClick()
        compose.onNodeWithText("Reload gauge data").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Hardware information").performScrollTo().assertIsNotEnabled()
        capture("device-data")
    }

    @Test fun transmissionIsAnExpertChildWithExplicitRemoval() {
        var removed = false
        frame {
            ExpertScreen(
                ExpertUiState(
                    false,
                    false,
                    false,
                    emptyList(),
                    vehicleName = "Jeep",
                    hasPrimaryAdapter = true,
                    hasTransmission = true,
                    canEditVehicle = true
                ),
                ExpertActions({}, {}, {}, {}, removeTransmission = { removed = true })
            )
        }
        compose.onNodeWithText("Second adapter").performScrollTo().performClick()
        compose.onNodeWithText("Find transmission adapter").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Remove second adapter").performScrollTo().performClick()
        assertEquals(false, removed)
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        assertEquals(true, removed)
    }

    @Test fun secondAdapterPickerSearchesOnceAndRejectsPrimaryEvenWhenPrimaryEditorWasSelected() {
        val primary = AdapterBinding("engine", "AA:BB:CC:DD:EE:01", "public")
        val child = AdapterBinding("child", "AA:BB:CC:DD:EE:02", "public")
        var searches = 0
        var selected: AdapterBinding? = null
        frame {
            ExpertScreen(
                ExpertUiState(
                    false, false, false, emptyList(), vehicleName = "Jeep",
                    hasPrimaryAdapter = true, hasTransmission = true, canEditVehicle = true,
                    canFindAdapter = true, primaryAdapter = primary,
                    adapterCandidates = listOf(
                        AdapterCandidate("Primary fixture", primary),
                        AdapterCandidate("Second fixture", child)
                    )
                ),
                ExpertActions({}, {}, {}, {}, findAdapters = { searches++ }, chooseAdapter = {
                    selected =
                        it
                })
            )
        }
        compose.onNodeWithText("Second adapter").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, searches)
        compose.onNodeWithText("Primary fixture").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Second fixture").performScrollTo().performClick()
        assertEquals(child, selected)
    }

    @Test fun secondAdapterWaitsForConnectionSearchesOnceAndClosesWhenVehicleChanges() {
        var searches = 0
        var state by mutableStateOf(
            ExpertUiState(
                false, false, false, emptyList(), vehicleName = "Jeep",
                vehicleId = "jeep", hasPrimaryAdapter = true, hasTransmission = true, canEditVehicle = true
            )
        )
        frame { ExpertScreen(state, ExpertActions({}, {}, {}, {}, findAdapters = { searches++ })) }
        compose.onNodeWithText("Second adapter").performScrollTo().performClick()
        assertEquals(0, searches)
        compose.runOnIdle { state = state.copy(canFindAdapter = true) }
        compose.waitForIdle()
        assertEquals(1, searches)
        compose.runOnIdle { state = state.copy(canFindAdapter = false) }
        compose.runOnIdle { state = state.copy(canFindAdapter = true) }
        compose.waitForIdle()
        assertEquals(1, searches)
        compose.runOnIdle { state = state.copy(vehicleId = "other", vehicleName = "Other") }
        compose.onAllNodesWithText("Find transmission adapter").assertCountEquals(0)
    }

    @Test fun savedGaugePickerShowsVehicleAndSourceAndDispatchesOnlyTheChosenIdentity() {
        var selected = ""
        frame {
            SettingsScreen(
                SettingsUiState(
                    "Main gauge", false, null, false, false, false, false, "Example", emptyList(),
                    gauges = listOf(
                        GaugeUi("one", "Main gauge", "Jeep", "ECM"),
                        GaugeUi("two", "TCM gauge", "Jeep", "TCM")
                    ),
                    selectedGaugeId = "one"
                ),
                {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, onSelectGauge = {
                    selected =
                        it
                }
            )
        }
        compose.onNodeWithText("Your gauges").performClick()
        compose.onAllNodesWithText("Transmission", substring = true).assertCountEquals(0)
        compose.onNodeWithText("TCM gauge").performClick()
        assertEquals("two", selected)
    }

    @Test fun carAlwaysEditsThePrimaryVehicleAdapter() {
        frame {
            CarScreen(
                CarUiState(
                    "Jeep",
                    listOf(
                        VehicleUi("jeep", "Jeep")
                    ),
                    "jeep",
                    StatusUi(
                        "Waiting for car",
                        "Checks run automatically"
                    ),
                    emptyList(), false, false, false, emptyList(), adapterAvailable = true
                ),
                {
                },
                {},
                {}
            )
        }
        compose.onNodeWithContentDescription("Car settings").performClick()
        compose.onNodeWithText("Adapter").performScrollTo().performClick()
        compose.onNodeWithText("Vehicle adapter").assertIsDisplayed()
        compose.onAllNodesWithText("Transmission child").assertCountEquals(0)
    }

    @Test fun bothAdaptersAreExplicitAndGatedInExpert() {
        var selected = false
        frame {
            ExpertScreen(
                ExpertUiState(
                    false, false, false, emptyList(), vehicleName = "Jeep",
                    hasPrimaryAdapter = true, hasTransmission = true, canEditVehicle = true, canUseBothAdapters = true
                ),
                ExpertActions({}, {}, {}, {}, useBoth = { selected = it })
            )
        }
        compose.onNodeWithText("Second adapter").performScrollTo().performClick()
        compose.onNodeWithText("Use both adapters on this gauge").performScrollTo().performClick()
        assertEquals(true, selected)
    }

    @Test fun oldFirmwareCannotEnableBothAdapters() {
        frame {
            ExpertScreen(
                ExpertUiState(
                    false,
                    false,
                    false,
                    emptyList(),
                    vehicleName = "Jeep",
                    hasPrimaryAdapter = true,
                    hasTransmission = true,
                    canEditVehicle = true
                ),
                ExpertActions({
                }, {}, {}, {})
            )
        }
        compose.onNodeWithText("Second adapter").performScrollTo().performClick()
        compose.onNodeWithText(
            "Use both adapters on this gauge"
        ).performScrollTo().assertIsNotEnabled()
    }
}
