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
                    SunsetBrightnessDialog(SunsetBrightness(false, false, false, 10, SunsetLocation(0, 0), 0), 100, {}) { automatic, level, location ->
                        saved = Triple(automatic, level, location)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Sunset to sunrise").performClick()
        compose.onNodeWithText("Use phone location").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save to gauge").assertIsNotEnabled()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (directory != null) {
            File(directory, "sunset-brightness-large-text.png").outputStream().use {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithContentDescription("Sunset to sunrise").performScrollTo().performClick()
        compose.onNodeWithText("Save to gauge").performClick()
        assertEquals(Triple(false, 10, null), saved)
    }
}
