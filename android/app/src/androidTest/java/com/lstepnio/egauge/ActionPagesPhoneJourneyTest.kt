package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Owner opt-in, real connection and read-only navigation. Never saves or starts an action. */
@RunWith(AndroidJUnit4::class)
class ActionPagesPhoneJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowGaugeRead") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun actionSetupOpensAndDismissesWithoutChangingSavedOrRunningSetup() {
        assumeTrue("Requires the paired bench gauge", allowed)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val model = (compose.activity.application as EGaugeApplication).model
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready && model.activeDocument != null &&
                model.displaySettings != null && model.bootIdentity != null
        }
        assertEquals(args.getString("expectedFirmwareVersion"), model.bootIdentity?.version)
        assertEquals(2, model.capabilities?.pageActionsVersion)
        val document = requireNotNull(model.activeDocument)
        val settings = model.displaySettings
        val profiles = model.profileCollection
        val gauges = model.knownGauges
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "action-pages-phone")
            .apply { mkdirs() }
        File(output, "verified-active.json").writeText(document.json)
        compose.onNodeWithText("Action pages").performScrollTo().performClick()
        compose.onNodeWithText("Swipe up on your gauge to open these actions.").assertIsDisplayed()
        compose.onNodeWithText("Save on phone").performScrollTo().assertIsDisplayed()
        File(output, "setup.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        Espresso.pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Save on phone").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(profiles, model.profileCollection)
        assertEquals(gauges, model.knownGauges)
        assertEquals(document.revision, model.activeDocument?.revision)
        assertEquals(document.sha256, model.activeDocument?.sha256)
        assertEquals(settings, model.displaySettings)
        File(output, "result.txt").writeText(
            "Real paired Pixel opened and dismissed Gauge > Action pages.\n" +
                "Typed action-page capability negotiated; profiles, per-gauge assignments, running revision/digest and settings unchanged.\n" +
                "No save, configuration send or vehicle command invoked.\n"
        )
    }
}
