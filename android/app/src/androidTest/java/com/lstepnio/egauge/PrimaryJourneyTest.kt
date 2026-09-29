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

    @Test fun customizeUsesThePreviewLedEditorWithOnePrimaryAction() {
        compose.onNodeWithText("Customize").performScrollTo().performClick()
        compose.onNodeWithText("Edit page").assertIsDisplayed()
        capture("dashboard")
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        assertNoJargon()
        compose.onNodeWithText("Manage pages").performScrollTo().performClick()
        compose.onNodeWithText("Add page").assertIsDisplayed()
        val pageCount = compose.onAllNodesWithText("Remove page").fetchSemanticsNodes().size
        compose.onNodeWithText("Add page").performClick()
        compose.onAllNodesWithText("Remove page").assertCountEquals(pageCount + 1)
        compose.onAllNodesWithText("Remove page").onFirst().performClick()
        compose.onAllNodesWithText("Remove page").assertCountEquals(pageCount)
        capture("manage-pages")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Edit page").performScrollTo().performClick()
        compose.onNodeWithText("Layout").performScrollTo().assertIsDisplayed()
        capture("page-editor")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Alerts").performScrollTo().performClick()
        compose.onNodeWithText("Edit alert").performScrollTo().performClick()
        compose.onNodeWithText("Warn above").performScrollTo().assertIsDisplayed()
        capture("alerts")
        compose.onNodeWithText("Save alert").performScrollTo().performClick()
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val alertCount = model.draft.alerts.size
        val available = demoCatalog.firstOrNull { it.id in ConfigurationProjector.supportedPidIds &&
            model.draft.alerts.none { alert -> alert.pidId == it.id } }
        available?.let { reading ->
            compose.onNodeWithText("Alerts").performScrollTo().performClick()
            compose.onNodeWithText("Add alert").performScrollTo().performClick()
            compose.onNodeWithText(readingName(reading.id)).performScrollTo().performClick()
            compose.onNodeWithText("${readingName(reading.id)} alert").assertIsDisplayed()
            compose.onNodeWithText("Save alert").performScrollTo().performClick()
            compose.onNodeWithText("Alerts").performScrollTo().performClick()
            compose.onAllNodesWithText("Remove alert").assertCountEquals(alertCount + 1)
            compose.onAllNodesWithText("Remove alert")[alertCount].performClick()
            compose.onAllNodesWithText("Remove alert").assertCountEquals(alertCount)
        }
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Review and send").performScrollTo().performClick()
        compose.onNodeWithText("Review and send").assertIsDisplayed()
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
