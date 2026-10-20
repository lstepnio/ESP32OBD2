package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.car.VehicleActionsSheet
import com.lstepnio.egauge.ui.state.CarUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Composable state fixtures only. No Bluetooth, AppModel or vehicle IO. */
@RunWith(AndroidJUnit4::class)
class VehicleCommandsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun state(phase: VehicleCommandPhase = VehicleCommandPhase.Ready, qualified: Boolean = true) = CarUiState(
        "Jeep", emptyList(), "jeep", StatusUi("Example", "Fixture"), emptyList(), false, false, false, emptyList(),
        canEditActions = true, commandsFresh = true,
        idlePresets = listOf(IdlePreset(IdlePresetId.AirUp, seconds = 10)),
        vehicleCommands = VehicleCommandKind.entries.associateWith { VehicleCommandStatus(it, if (it == VehicleCommandKind.Idle && phase == VehicleCommandPhase.RestartRequired) VehicleCommandPhase.Unavailable else phase, qualified = qualified) }
    )

    @Test fun shortcutConfirmationDoesNotSendUntilConfirmed() {
        var starts = 0
        compose.setContent {
            EGaugeTheme {
                VehicleActionsSheet(state(), {}, { kind, stop, preset ->
                    assertEquals(VehicleCommandKind.Idle, kind)
                    assertFalse(stop)
                    assertEquals(10, preset!!.seconds)
                    starts++
                }, {})
            }
        }
        compose.onNodeWithText("Air up").performClick()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithText("Use this car's fixed raised-idle setting for 10 seconds while parked.").assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun unavailableActionsCannotStartAndRestartNeverClaimsRestoration() {
        compose.setContent { EGaugeTheme { VehicleActionsSheet(state(VehicleCommandPhase.RestartRequired, false), {}, { _, _, _ -> fail("Unavailable command sent") }, {}) } }
        compose.onNodeWithText("Air up").assertIsNotEnabled()
        compose.onNodeWithText("Switch off ABS & traction control").assertIsNotEnabled()
        compose.onNodeWithText("Restart the vehicle to restore ABS and traction control").assertIsDisplayed()
    }

    @Test fun stopNeedsNoSecondConfirmation() {
        var stopped = false
        compose.setContent {
            EGaugeTheme {
                VehicleActionsSheet(state(VehicleCommandPhase.Active), {}, { kind, stop, _ ->
                    assertEquals(VehicleCommandKind.Idle, kind)
                    stopped = stop
                }, {})
            }
        }
        compose.onNodeWithText("Stop raised idle").performClick()
        compose.runOnIdle { assertTrue(stopped) }
        compose.onNodeWithText("Confirm").assertDoesNotExist()
    }
}
