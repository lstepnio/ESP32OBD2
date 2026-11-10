package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.settings.SunsetBrightnessDialog
import com.lstepnio.egauge.ui.settings.SunsetPhoneLocation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SunsetBrightnessUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun automaticScheduleRequiresLocationAndManualSaveDoesNot() {
        var saved: Triple<Boolean, Int, SunsetLocation?>? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                EGaugeTheme {
                    SunsetBrightnessDialog(SunsetBrightness(false, false, false, 10, SunsetLocation(0, 0), 0), 100, {}, locate = { SunsetPhoneLocation.LocationOff }) { automatic, level, location ->
                        saved = Triple(automatic, level, location)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Sunset to sunrise").performClick()
        compose.onNodeWithText("Turn on Location in Android settings, then try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save to gauge").assertIsNotEnabled()
        capture("sunset-brightness-large-text")
        compose.onNodeWithContentDescription("Sunset to sunrise").performScrollTo().performClick()
        compose.onNodeWithText("Save to gauge").performClick()
        assertEquals(Triple(false, 10, null), saved)
    }

    @Test fun enablingAutomaticallyFindsLocationAndRetryRecovers() {
        val location = SunsetLocation(3970, -10500)
        var attempts = 0
        var saved: Triple<Boolean, Int, SunsetLocation?>? = null
        compose.setContent {
            EGaugeTheme {
                SunsetBrightnessDialog(
                    SunsetBrightness(false, false, false, 10, SunsetLocation(0, 0), 0),
                    90,
                    {},
                    locate = { if (++attempts == 1) SunsetPhoneLocation.Unavailable else SunsetPhoneLocation.Ready(location) }
                ) { automatic, level, fix -> saved = Triple(automatic, level, fix) }
            }
        }
        compose.onNodeWithContentDescription("Sunset to sunrise").performClick()
        compose.onNodeWithText("Your phone couldn’t find a location.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save to gauge").assertIsNotEnabled()
        compose.onNodeWithText("Use phone location").performScrollTo().performClick()
        compose.onNodeWithText("Location ready").performScrollTo().assertIsDisplayed()
        capture("sunset-brightness-compact")
        compose.onNodeWithText("Save to gauge").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(Triple(true, 10, location), saved) }
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (directory != null) {
            File(directory, "$name.png").outputStream().use {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
