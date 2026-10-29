package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.car.IdleActionsSheet
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Composable fixtures only; no AppViewModel, Bluetooth or vehicle requests. */
@RunWith(AndroidJUnit4::class)
class IdleActionsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(output).mkdirs()
        File(output, name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun unqualifiedCarCanSaveAWinchDraftButCannotStart() {
        var saved: List<IdlePreset>? = null
        compose.setContent { EGaugeTheme { IdleActionsSheet("jeep", emptyList(), true, { saved = it }, {}) } }
        compose.onNodeWithText("Winch").performClick()
        compose.onNodeWithText("Minimum RPM").performTextInput("1200")
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Start Winch").performScrollTo().assertIsNotEnabled()
        capture("idle-setup.png")
        compose.onNodeWithText("Save preset").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull("Save preset must invoke its callback", saved)
            assertEquals(IdlePreset(IdlePresetId.Winch, 1200), saved!!.single { it.id == IdlePresetId.Winch })
        }
    }

    @Test fun qualifiedFixtureRejectsNumericDraftUntilFixedSettingIsExplicitlyChosen() {
        var selected: IdlePreset? = null
        compose.setContent { EGaugeTheme { IdleActionsSheet("fixture", listOf(IdlePreset(IdlePresetId.AirUp, 1200)), true, {}, {}, IdleControlState(IdlePhase.Ready), { selected = it }, coolantLimitC = 105.0) } }
        compose.onNodeWithText("Start Air up").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithText("Target RPM").performScrollTo().performTextClearance()
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Start Air up").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(IdlePreset(IdlePresetId.AirUp), selected) }
    }

    @Test fun calibratedFourTirePresetShowsEstimateAndSavesOverride() {
        var saved = emptyList<IdlePreset>()
        compose.setContent { EGaugeTheme { IdleActionsSheet("jeep", emptyList(), true, { saved = it }, {}) } }
        compose.onNodeWithText("Pressure estimate").performScrollTo().performClick()
        compose.onNodeWithText("Save preset").performScrollTo().assertIsNotEnabled()
        for ((label, input) in listOf("Starting PSI" to "15", "Target PSI" to "35", "Measured from PSI" to "15", "Measured to PSI" to "35", "Measured fill time" to "8:00")) {
            compose.onNodeWithText(label).performScrollTo().performTextInput(input)
        }
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Estimated time · 8:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Time override").performScrollTo().performTextInput("4:30")
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Hide calibration").performScrollTo().performClick()
        compose.onNodeWithText("Estimated time · 8:00").performScrollTo()
        capture("air-up-pressure-preset.png")
        compose.onNodeWithText("Save preset").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(270, saved.single().seconds)
            assertEquals(35, saved.single().airUp!!.targetPsi)
        }
    }

    @Test fun activeFixtureShowsCountdownAndStopAndLocksPresetChanges() {
        var stopped = false
        compose.setContent { EGaugeTheme { IdleActionsSheet("fixture", listOf(IdlePreset(IdlePresetId.AirUp, 1200)), true, {}, {}, IdleControlState(IdlePhase.Active, IdlePresetId.AirUp, 1200, 240), onStop = { stopped = true }) } }
        compose.onNodeWithText("Air up · 1200 rpm · 4:00 remaining").assertIsDisplayed()
        capture("idle-active.png")
        compose.onNodeWithText("Stop raised idle").performClick()
        compose.runOnIdle { assertTrue(stopped) }
        compose.onNodeWithText("Target RPM").assertIsNotEnabled()
        compose.onNodeWithText("Save preset").performScrollTo().assertIsNotEnabled()
    }
}
