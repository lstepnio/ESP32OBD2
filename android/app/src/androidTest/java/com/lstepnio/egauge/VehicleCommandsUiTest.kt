package com.lstepnio.egauge

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.car.PageActionsSheet
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
    private fun state(phase: VehicleCommandPhase = VehicleCommandPhase.Ready, qualified: Boolean = true, runningLimit: Double? = 105.0, configuredLimit: Double? = 105.0) = CarUiState(
        "Jeep", emptyList(), "jeep", StatusUi("Example", "Fixture"), emptyList(), false, false, false, emptyList(),
        canEditActions = true, commandsFresh = true,
        idleCoolantLimitC = configuredLimit, runningIdleCoolantLimitC = runningLimit,
        idlePresets = listOf(IdlePreset(IdlePresetId.AirUp, seconds = 10)),
        vehicleCommands = VehicleCommandKind.entries.associateWith { VehicleCommandStatus(it, if (it == VehicleCommandKind.Idle && phase == VehicleCommandPhase.RestartRequired) VehicleCommandPhase.Unavailable else phase, qualified = qualified) }
    )

    @Test fun burnoutOffersOnlyQualifiedStopAndNeverStart() {
        var stopped = false
        compose.setContent {
            EGaugeTheme {
                VehicleActionsSheet(state().copy(vehicleCommands = mapOf(VehicleCommandKind.Burnout to VehicleCommandStatus(VehicleCommandKind.Burnout, VehicleCommandPhase.Unknown, operation = 3, qualified = true, remainingSeconds = 10))), {}, { kind, stop, preset ->
                    assertEquals(VehicleCommandKind.Burnout, kind)
                    assertTrue(stop)
                    assertNull(preset)
                    stopped = true
                }, {})
            }
        }
        compose.onNodeWithText("Start Burnout Mode").assertDoesNotExist()
        compose.onNodeWithText("Stop Burnout Mode").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }

    @Test fun supervisedBrakesStartOnGaugeAndDescribeManualConfirmation() = supervisedBrakeUi(1f)

    @Test fun supervisedBrakeConfirmationRemainsReachableAtLargeText() = supervisedBrakeUi(2f)

    private fun supervisedBrakeUi(fontScale: Float) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val originalScale = instrumentation.targetContext.resources.configuration.fontScale
        fun scale(value: Float) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $value")
            ).use { it.readBytes() }
            compose.waitUntil(5_000) { instrumentation.targetContext.resources.configuration.fontScale == value }
            instrumentation.waitForIdleSync()
        }
        var commands = 0
        try {
            scale(fontScale)
            compose.setContent {
                EGaugeTheme {
                    VehicleActionsSheet(
                        state().copy(
                            vehicleCommands = mapOf(
                                VehicleCommandKind.AbsTcs to VehicleCommandStatus(VehicleCommandKind.AbsTcs, VehicleCommandPhase.Ready, qualified = true).withBrakeActionsTestVersion(1),
                                VehicleCommandKind.Burnout to VehicleCommandStatus(VehicleCommandKind.Burnout, VehicleCommandPhase.Verifying, qualified = true).withBrakeActionsTestVersion(1)
                            )
                        ),
                        {},
                        { _, _, _ -> commands++ },
                        {}
                    )
                }
            }
            compose.onNodeWithText("Disable ABS + TCS").assertDoesNotExist()
            compose.onNodeWithText("Start Burnout Mode").assertDoesNotExist()
            compose.onNodeWithText("Start on the gauge").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Check normal braking, then confirm on the gauge").performScrollTo().assertIsDisplayed()
            val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            if (output != null) {
                java.io.File(output, "supervised-brakes-$fontScale.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.runOnIdle { assertEquals(0, commands) }
        } finally {
            scale(originalScale)
        }
    }

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

    @Test fun airUpTimeCanBeOverriddenForOneSessionWithoutSaving() {
        var selected: IdlePreset? = null
        val timer = AirUpTimer(15, 35, 15, 35, 480)
        val preset = IdlePreset(IdlePresetId.AirUp, seconds = 480, airUp = timer)
        compose.setContent { EGaugeTheme { VehicleActionsSheet(state().copy(idlePresets = listOf(preset)), { fail("Session override must not persist") }, { _, _, value -> selected = value }, {}) } }
        compose.onNodeWithText("Air up").performClick()
        compose.onNodeWithText("Time for this session").performTextReplacement("4:30")
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle {
            assertEquals(270, selected!!.seconds)
            assertEquals(480, preset.seconds)
        }
        compose.onNodeWithText("Air up").performClick()
        compose.onNodeWithText("Time for this session").assertTextContains("8:00")
        compose.onNodeWithText("Time for this session").performTextReplacement("11:00")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test fun missingCoolantLimitWarnsAndBlocksOnlyIdleStart() {
        compose.setContent { EGaugeTheme { VehicleActionsSheet(state(runningLimit = null, configuredLimit = null), {}, { _, _, _ -> fail("No unguarded idle start") }, {}) } }
        compose.onNodeWithText("Set a coolant limit").assertIsDisplayed()
        compose.onNodeWithText("Air up").assertIsNotEnabled()
        compose.onNodeWithText("Disable ABS + TCS").assertIsEnabled()
    }

    @Test fun phoneOnlyLimitNeverClaimsGaugeProtection() {
        compose.setContent { EGaugeTheme { VehicleActionsSheet(state(runningLimit = null), {}, { _, _, _ -> fail("Unsent guard cannot start idle") }, {}) } }
        compose.onNodeWithText("Send the coolant alert to the gauge before starting raised idle.").assertIsDisplayed()
        compose.onNodeWithText("Air up").assertIsNotEnabled()
    }

    @Test fun shortcutCanBeSavedWithVisibleMissingLimitWarning() {
        var saved: PageAction? = null
        compose.setContent { EGaugeTheme { PageActionsSheet(state(runningLimit = null, configuredLimit = null).copy(commandsSupported = true, actionsSupported = true), {}, { saved = it }) } }
        compose.onNodeWithText("Vehicle actions").performClick()
        compose.onNodeWithText("Set a coolant limit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save action").performScrollTo().performClick()
        assertEquals(PageActionType.VehicleActions, saved?.type)
    }

    @Test fun configuredStopLimitUsesTheSelectedUnits() {
        compose.setContent { EGaugeTheme { VehicleActionsSheet(state().copy(measurementSystem = MeasurementSystem.Imperial), {}, { _, _, _ -> }, {}) } }
        compose.onNodeWithText("Raised idle stops at 221 °F; missing coolant limits the session to one minute, started on the gauge.").assertIsDisplayed()
    }

    @Test fun missingLimitWarningAndSaveRemainReachableAtLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                EGaugeTheme { PageActionsSheet(state(runningLimit = null, configuredLimit = null).copy(commandsSupported = true, actionsSupported = true, actions = listOf(PageAction(type = PageActionType.VehicleActions))), {}, {}) }
            }
        }
        compose.onNodeWithText("Set a coolant limit").performScrollTo().assertIsDisplayed()
        val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (output != null) {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            java.io.File(output, "coolant-shortcut-large-text.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Save action").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test fun unavailableActionsCannotStartAndRestartNeverClaimsRestoration() {
        compose.setContent { EGaugeTheme { VehicleActionsSheet(state(VehicleCommandPhase.RestartRequired, false), {}, { _, _, _ -> fail("Unavailable command sent") }, {}) } }
        compose.onNodeWithText("Air up").assertIsNotEnabled()
        compose.onNodeWithText("Disable ABS + TCS").assertDoesNotExist()
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
