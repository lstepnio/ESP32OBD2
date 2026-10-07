package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.ui.state.readingName
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, no device writes and no vehicle queries. Local pages are only viewed. */
@RunWith(AndroidJUnit4::class)
class PrimaryJourneyTest {
    @get:Rule val compose = activityTestRule()

    @Test fun primaryNavigationKeepsTechnicalTermsOutOfDefaultFlow() {
        compose.onNodeWithText("Gauge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Not connected").assertIsDisplayed()
        compose.onAllNodesWithText("Your gauge is offline").assertCountEquals(0)
        capture("gauge")
        compose.onNodeWithText("Car", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No car readings yet").assertIsDisplayed()
        compose.onAllNodesWithText("About clearing codes").assertCountEquals(0)
        assertNoJargon()
        capture("car")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        capture("settings")
        compose.onNodeWithText("Show advanced tools").performScrollTo().assertIsDisplayed()
        assertNoJargon()
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        capture("settings-tools")
        compose.onNodeWithText("Check updates").performScrollTo().performClick()
        capture("updates")
    }

    @Test fun customizeUsesOneWorkspaceWithoutChangingSavedSettings() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val original = model.draft
        compose.onNodeWithText("Customize").performScrollTo().performClick()
        compose.onNodeWithText("Reading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Layout").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        compose.onNodeWithText("Review and send").assertIsDisplayed()
        capture("customize-workspace")
        assertNoJargon()
        compose.onNodeWithText("Manage pages").performScrollTo().performClick()
        capture("manage-pages")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Layout").performScrollTo().performClick()
        capture("layout-choices")
        compose.onNodeWithContentDescription("Close choices").performClick()
        compose.onNodeWithText("Review and send").performClick()
        capture("review")
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        assertNoJargon()
        org.junit.Assert.assertEquals("Read-only journey must preserve every saved choice", original, model.draft)
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
