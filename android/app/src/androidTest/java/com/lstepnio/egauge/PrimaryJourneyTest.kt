package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, no device writes and no vehicle queries. Local pages are only viewed. */
@RunWith(AndroidJUnit4::class)
class PrimaryJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun primaryNavigationKeepsTechnicalTermsOutOfDefaultFlow() {
        compose.onNodeWithText("Gauge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Not connected").assertIsDisplayed()
        compose.onAllNodesWithText("Your gauge is offline").assertCountEquals(0)
        capture("gauge")
        compose.onNodeWithText("Car", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No car readings yet").assertIsDisplayed()
        assertNoJargon()
        capture("car")
        compose.onNodeWithText("About clearing codes").performScrollTo().performClick()
        capture("clear-codes")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        capture("settings")
        compose.onNodeWithText("Show advanced tools").performScrollTo().assertIsDisplayed()
        assertNoJargon()
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        capture("settings-tools")
        compose.onNodeWithText("Check updates").performScrollTo().performClick()
        capture("updates")
    }

    @Test fun customizeIsAGuidedFlowWithOnePrimaryAction() {
        compose.onNodeWithText("Customize").performScrollTo().performClick()
        compose.onNodeWithText("Choose readings").assertIsDisplayed()
        capture("readings")
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        assertNoJargon()
        compose.onNodeWithText("Choose layouts").performScrollTo().performClick()
        capture("layouts")
        compose.onNodeWithText("Set limits").performScrollTo().performClick()
        capture("limits")
        compose.onNodeWithText("Warn above").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review pages").performScrollTo().performClick()
        compose.onNodeWithText("Review your gauge").assertIsDisplayed()
        capture("review")
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        assertNoJargon()
    }

    private fun capture(name: String) {
        val variant = InstrumentationRegistry.getArguments().getString("captureScreens") ?: return
        val instrument = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "journey-captures/$variant").apply { mkdirs() }
        compose.waitForIdle()
        org.junit.Assert.assertEquals("Capture requires the app in the foreground",
            "com.lstepnio.egauge", instrument.uiAutomation.rootInActiveWindow?.packageName?.toString())
        File(folder, "$name.png").outputStream().use {
            instrument.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertNoJargon() {
        listOf("SHA-256", "revision", "ECM", "TCM", "PID", "Hysteresis", "PHONE DRAFT", "GitHub", "projection", "evidence").forEach { word ->
            compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }
}
