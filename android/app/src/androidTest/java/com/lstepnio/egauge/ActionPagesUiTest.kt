package com.lstepnio.egauge

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.car.ActionPagesSheet
import com.lstepnio.egauge.ui.state.CarUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fixture-only configuration. Never constructs a Bluetooth session. */
@RunWith(AndroidJUnit4::class)
class ActionPagesUiTest {
    @get:Rule val compose = createComposeRule()
    private fun state() = CarUiState(
        "Jeep", emptyList(), "jeep", StatusUi("Ready", ""), emptyList(), false, false, false, emptyList(),
        actions = listOf(PageAction(type = PageActionType.VehicleActions)), canEditActions = true, gaugeKnown = true, actionsSupported = true,
        vehicleCommands = VehicleCommandKind.entries.associateWith { VehicleCommandStatus(it, VehicleCommandPhase.Ready, qualified = true) }
    )

    @Test fun editTimerAndSaveOnlyConfiguration() {
        var saved: PageAction? = null
        compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { action, _ -> saved = action }, {}) } }
        compose.onNodeWithText("Winch").performScrollTo().performClick()
        compose.onNodeWithText("Duration").performScrollTo().performTextReplacement("2:30")
        // This fixture verifies configuration semantics. Retire the IME before
        // leaving the editor; a moving keyboard can intercept coordinate clicks.
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> assertTrue(click()) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Save on phone").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(150, saved!!.winchSeconds)
            assertEquals(ActionPageId.entries.toList(), saved!!.pages)
        }
    }

    @Test fun activeOperationLocksConfiguration() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state().copy(vehicleCommands = mapOf(VehicleCommandKind.Idle to VehicleCommandStatus(VehicleCommandKind.Idle, VehicleCommandPhase.Unknown))), { _, _ -> fail("Unresolved operation cannot lose its page") }, {}) } }
        compose.onNodeWithText("Save on phone").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Add action").performScrollTo().assertIsNotEnabled()
    }

    @Test fun emptySetupDoesNotOfferUnsupportedCommands() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state().copy(actions = emptyList(), vehicleCommands = emptyMap()), { _, _ -> }, {}) } }
        compose.onNodeWithText("Add action").performScrollTo().performClick()
        compose.onNodeWithText("No additional actions available for this car yet.").assertIsDisplayed()
        compose.onNodeWithText("Disable ABS + TCS").assertDoesNotExist()
    }
}
