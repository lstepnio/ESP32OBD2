package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.car.ActionPagesSheet
import com.lstepnio.egauge.ui.state.CarUiState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fixture-only configuration. Never constructs a Bluetooth session. */
@RunWith(AndroidJUnit4::class)
class ActionPagesUiTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(output).mkdirs()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun state() = CarUiState(
        "Jeep", emptyList(), "jeep", StatusUi("Ready", ""), emptyList(), false, false, false, emptyList(),
        actions = listOf(PageAction(type = PageActionType.VehicleActions)), canEditActions = true, gaugeKnown = true, actionsSupported = true,
        vehicleCommands = VehicleCommandKind.entries.associateWith { VehicleCommandStatus(it, VehicleCommandPhase.Ready, qualified = true) }
    )

    private fun finishEditor() {
        compose.onNodeWithText("Done").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Save on phone").fetchSemanticsNodes().size == 1
        }
    }

    @Test fun savedBurnoutDraftStaysEditableWithoutCommandSupport() {
        var saved: PageAction? = null
        val action = PageAction(type = PageActionType.VehicleActions, pages = listOf(ActionPageId.Burnout))
        compose.setContent { EGaugeTheme { ActionPagesSheet(state().copy(actions = listOf(action), vehicleCommands = emptyMap(), commandsSupported = false), { value, _ -> saved = value }, {}) } }
        compose.onNodeWithText("Burnout Mode").assertDoesNotExist()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(action.withoutBurnout(), saved) }
    }

    @Test fun editTimerAndSaveOnlyConfiguration() {
        var saved: PageAction? = null
        compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { action, _ -> saved = action }, {}) } }
        compose.onNodeWithText("Winch").performScrollTo().performClick()
        compose.onNodeWithText("Duration").performScrollTo().performClick()
        compose.onNodeWithText("Duration").performScrollTo().performTextReplacement("2:30")
        compose.closeKeyboardAndWaitForIdle()
        finishEditor()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(150, saved!!.winchSeconds)
            assertEquals(legacyActionPages, saved!!.pages)
        }
    }

    @Test fun compactEditorKeepsEstimateOptionalAndOneCoolantRow() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { _, _ -> }, {}) } }
        compose.onNodeWithText("Air up").assertIsDisplayed()
        capture("action-pages-list.png")
        compose.onNodeWithText("Air up").performScrollTo().performClick()
        compose.onAllNodesWithText("Add a coolant limit").assertCountEquals(1)
        compose.onNodeWithText("Idle cannot start until a high-temperature alert is set.").assertExists()
        compose.onNodeWithText("Coolant limit").assertDoesNotExist()
        compose.onNodeWithText("Set a coolant limit").assertDoesNotExist()
        compose.onNodeWithText("Starting PSI").assertDoesNotExist()
        capture("action-pages-editor-warning.png")
        compose.onNodeWithText("Estimate from tire pressure").performScrollTo().performClick()
        compose.onNodeWithText("Pressure estimate").performScrollTo().performClick()
        compose.onNodeWithText("Starting PSI").performScrollTo().assertIsDisplayed()
    }

    @Test fun openingDurationPreservesPressureEstimateUntilTimeChanges() {
        val timer = AirUpTimer(20, 35, 20, 35, 300)
        var savedPresets: List<IdlePreset>? = null
        compose.setContent {
            EGaugeTheme {
                ActionPagesSheet(state().copy(idleCoolantLimitC = 105.0, idlePresets = listOf(IdlePreset(IdlePresetId.AirUp, seconds = 300, airUp = timer))), { _, presets -> savedPresets = presets }, {})
            }
        }
        compose.onNodeWithText("Air up").performScrollTo().performClick()
        compose.onNodeWithText("Coolant limit").assertIsDisplayed()
        capture("action-pages-editor.png")
        compose.onNodeWithText("Duration").performScrollTo().performClick()
        finishEditor()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(timer, savedPresets!!.single().airUp) }
        compose.onNodeWithText("Air up").performScrollTo().performClick()
        compose.onNodeWithText("Duration").performScrollTo().performClick()
        compose.onNodeWithText("Duration").performScrollTo().performTextReplacement("2:30")
        compose.closeKeyboardAndWaitForIdle()
        finishEditor()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(savedPresets!!.single().airUp)
            assertEquals(150, savedPresets!!.single().seconds)
        }
    }

    @Test fun numericTargetSurvivesTimerEditAndFloorIsExplicit() {
        var saved: List<IdlePreset>? = null
        compose.setContent {
            EGaugeTheme {
                ActionPagesSheet(state().copy(idlePresets = listOf(IdlePreset(IdlePresetId.AirUp, 1400))), { _, presets -> saved = presets }, {})
            }
        }
        compose.onNodeWithText("Air up").performScrollTo().performClick()
        compose.onNodeWithText("1400 RPM · Saved on phone").assertExists()
        compose.onNodeWithText("Duration").performScrollTo().performClick()
        compose.onNodeWithText("Duration").performTextReplacement("2:30")
        compose.closeKeyboardAndWaitForIdle()
        finishEditor()
        compose.onNodeWithText("Winch").performScrollTo().performClick()
        compose.onNodeWithText("Minimum RPM").performScrollTo().performClick()
        compose.onNodeWithText("Minimum RPM").performTextInput("3001")
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Done").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Minimum RPM").performScrollTo().performTextReplacement("1300")
        compose.closeKeyboardAndWaitForIdle()
        capture("action-target-minimum-compact.png")
        finishEditor()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1400, saved!!.single { it.id == IdlePresetId.AirUp }.rpm)
            assertEquals(150, saved!!.single { it.id == IdlePresetId.AirUp }.seconds)
            assertEquals(1300, saved!!.single { it.id == IdlePresetId.Winch }.rpm)
        }
    }

    @Test fun largeTextRemovedBurnoutDraftKeepsSaveReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val originalScale = instrumentation.targetContext.resources.configuration.fontScale
        fun scale(value: Float) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $value")
            ).use { it.readBytes() }
            compose.waitUntil(5_000) { instrumentation.targetContext.resources.configuration.fontScale == value }
            instrumentation.waitForIdleSync()
        }
        var saved: PageAction? = null
        val action = PageAction(type = PageActionType.VehicleActions, pages = listOf(ActionPageId.Burnout))
        try {
            scale(2f)
            compose.setContent { EGaugeTheme { ActionPagesSheet(state().copy(actions = listOf(action), vehicleCommands = emptyMap()), { value, _ -> saved = value }, {}) } }
            compose.onNodeWithText("Burnout Mode").assertDoesNotExist()
            compose.onNodeWithText("Save on phone").performScrollTo().assertIsEnabled().assertIsDisplayed()
            capture("burnout-save-large-text.png")
            compose.onNodeWithText("Save on phone").performClick()
            compose.runOnIdle { assertEquals(action.withoutBurnout(), saved) }
        } finally {
            scale(originalScale)
        }
    }

    @Test fun largeTextTargetEditorKeepsDraftAndDoneReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = instrumentation.targetContext.resources.configuration.fontScale
        fun scale(value: Float) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $value")
            ).use { it.readBytes() }
            compose.waitUntil(5_000) { instrumentation.targetContext.resources.configuration.fontScale == value }
            instrumentation.waitForIdleSync()
        }
        try {
            // Dialog windows obtain density from Android resources, not an outer
            // Compose LocalDensity provider. Exercise the actual 200% setting.
            scale(2f)
            compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { _, _ -> }, {}) } }
            compose.onNodeWithText("Air up").performScrollTo().performClick()
            compose.onNodeWithText("Target RPM").performScrollTo().performClick()
            compose.onNodeWithText("Target RPM").performTextInput("1200")
            compose.closeKeyboardAndWaitForIdle()
            compose.onNodeWithText("Target RPM").performScrollTo().assertIsDisplayed()
            capture("action-target-large-text.png")
            compose.onNodeWithText("Done").performScrollTo().assertIsEnabled()
        } finally {
            scale(originalScale)
        }
    }

    @Test fun expandedInvalidEstimateBlocksSavingPage() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { _, _ -> fail("Invalid estimate cannot save") }, {}) } }
        compose.onNodeWithText("Air up").performScrollTo().performClick()
        compose.onNodeWithText("Estimate from tire pressure").performScrollTo().performClick()
        compose.onNodeWithText("Pressure estimate").performScrollTo().performClick()
        compose.onNodeWithText("Starting PSI").performScrollTo().performTextReplacement("20")
        compose.onNodeWithText("Target PSI").performScrollTo().performTextReplacement("15")
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Target PSI must be above starting PSI.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performScrollTo().assertIsNotEnabled()
    }

    @Test fun removeLivesInEditorAndCanBeUndone() {
        var saved: PageAction? = null
        compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { action, _ -> saved = action }, {}) } }
        compose.onNodeWithText("Remove action").assertDoesNotExist()
        compose.onNodeWithText("Winch").performScrollTo().performClick()
        compose.onNodeWithText("Remove action").performScrollTo().performClick()
        compose.onNodeWithText("Winch").assertDoesNotExist()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(legacyActionPages, saved!!.pages) }
    }

    @Test fun namedOverflowReordersWithoutChangingTimers() {
        var saved: PageAction? = null
        compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { action, _ -> saved = action }, {}) } }
        compose.onNodeWithText("Action page options").performScrollTo().performClick()
        compose.onNodeWithText("Reorder actions").performClick()
        compose.onNodeWithText("Move Winch up").performScrollTo().performClick()
        compose.onNodeWithText("Done reordering").performScrollTo().performClick()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(ActionPageId.Winch, ActionPageId.AirUp, ActionPageId.AbsTcs), saved!!.pages)
            assertEquals(state().actions.single().airUpSeconds, saved!!.airUpSeconds)
            assertEquals(state().actions.single().winchSeconds, saved!!.winchSeconds)
        }
    }

    @Test fun pendingSaveUsesOneHonestMessage() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state(), { _, _ -> }, {}, setupPending = true) } }
        compose.onAllNodesWithText("Saved on phone").assertCountEquals(1)
        compose.onNodeWithText("Saved to gauge").assertDoesNotExist()
    }

    @Test fun savedIdlePagesKeepTheirTimersBeforeQualification() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state().copy(vehicleCommands = emptyMap()), { _, _ -> }, {}) } }
        compose.onAllNodesWithText("5:00").assertCountEquals(2)
        compose.onAllNodesWithText("Not supported yet").assertCountEquals(1)
    }

    @Test fun activeOperationLocksConfiguration() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state().copy(vehicleCommands = mapOf(VehicleCommandKind.Idle to VehicleCommandStatus(VehicleCommandKind.Idle, VehicleCommandPhase.Unknown))), { _, _ -> fail("Unresolved operation cannot lose its page") }, {}) } }
        compose.onNodeWithText("Save on phone").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Add action").performScrollTo().assertIsNotEnabled()
    }

    @Test fun supervisedBurnoutCannotBeAddedAndPreservesLegacyPagesAndTimers() {
        var saved: PageAction? = null
        var savedPresets: List<IdlePreset>? = null
        val original = PageAction(type = PageActionType.VehicleActions, airUpSeconds = 60, winchSeconds = 180)
        val presets = listOf(IdlePreset(IdlePresetId.AirUp, seconds = 60), IdlePreset(IdlePresetId.Winch, seconds = 180))
        val burnout = VehicleCommandStatus(VehicleCommandKind.Burnout).withBrakeActionsTestVersion(1)
        assertFalse(burnout.qualified)
        assertFalse(burnout.canStart)
        compose.setContent {
            EGaugeTheme {
                ActionPagesSheet(state().copy(actions = listOf(original), idlePresets = presets, vehicleCommands = mapOf(VehicleCommandKind.Burnout to burnout)), { action, values ->
                    saved = action
                    savedPresets = values
                }, {})
            }
        }
        compose.onNodeWithText("Add action").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Burnout Mode").assertDoesNotExist()
        compose.onNodeWithText("Save on phone").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(original, saved)
            assertEquals(presets, savedPresets)
            assertFalse(burnout.canStart)
        }
    }

    @Test fun emptySetupDoesNotOfferUnsupportedCommands() {
        compose.setContent { EGaugeTheme { ActionPagesSheet(state().copy(actions = emptyList(), vehicleCommands = emptyMap()), { _, _ -> }, {}) } }
        compose.onNodeWithText("Add action").performScrollTo().performClick()
        compose.onNodeWithText("No additional actions available for this car yet.").assertIsDisplayed()
        compose.onNodeWithText("Disable ABS + TCS").assertDoesNotExist()
        compose.onNodeWithText("Burnout Mode").assertDoesNotExist()
    }
}
