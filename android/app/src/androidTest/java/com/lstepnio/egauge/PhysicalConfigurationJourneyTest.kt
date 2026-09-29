package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.ui.state.isConfirmedSetup
import com.lstepnio.egauge.ui.state.readingName
import com.lstepnio.egauge.ui.state.sameSettings
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicitly opt-in bench validation. Never reads OBD data, clears codes or installs firmware. */
@RunWith(AndroidJUnit4::class)
class PhysicalConfigurationJourneyTest {
    @get:Rule val compose = activityTestRule()

    @Test fun threeReadingSendWaitsForRunningProofAndRestoresTheOriginalGaugeLayout() {
        assumeTrue("Physical writes require an explicit bench run",
            InstrumentationRegistry.getArguments().getString("allowGaugeWrite") == "true")
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "physical-journey").apply { mkdirs() }
        fun runOperation(action: () -> Unit) {
            val before = model.operation.id
            compose.runOnUiThread(action)
            compose.waitUntil(120_000) { model.operation.id > before && model.operation.terminal }
            assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
        }
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        runOperation(model::discoverGauge)
        runOperation(model::checkGaugeForReview)
        val original = requireNotNull(model.activeDocument) { "A restorable saved configuration is required" }
        val originalDraft = requireNotNull(GaugeDraftComparison.savedDraft(original, model.profileCollection.activeId)) {
            "The existing layout cannot be restored safely by this test"
        }
        assertTrue("Only a confirmed, healthy original setup is eligible",
            isConfirmedSetup(original.revision, original.sha256, model.runtimeIdentity))
        var confirmed = false
        var sendStarted = false
        var restored = false
        val report = StringBuilder("Physical gauge configuration journey\n")
            .append("Original revision: ${original.revision}\nOriginal digest: ${original.sha256}\n")
        try {
            compose.onNodeWithText("Gauge", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Customize").performScrollTo().performClick()
            compose.onNodeWithText("Manage pages").performScrollTo().performClick()
            while (model.draft.pages.size > 3) compose.onAllNodesWithText("Remove page").onFirst().performScrollTo().performClick()
            while (model.draft.pages.size < 3) compose.onNodeWithText("Add page").performScrollTo().performClick()
            compose.onNodeWithText("Done").performScrollTo().performClick()
            listOf("rpm", "coolant", "load").forEachIndexed { index, id ->
                compose.onNodeWithText("Edit page").performScrollTo().performClick()
                compose.onNodeWithText(readingName(id)).performScrollTo().performClick()
                compose.onNodeWithText("Done").performScrollTo().performClick()
                if (index < 2) compose.onNodeWithTag("page-carousel").performTouchInput { swipeLeft() }
            }
            compose.onNodeWithText("Edit page").performScrollTo()
            capture("customize-readings")
            compose.onNodeWithText("Edit page").performScrollTo().performClick()
            listOf(GaugeLayout.Numeric, GaugeLayout.Arc, GaugeLayout.Bar).forEachIndexed { index, layout ->
                compose.onNodeWithText(layout.label).performScrollTo().performClick()
                compose.onNodeWithText("Done").performScrollTo().performClick()
                if (index < 2) {
                    compose.onNodeWithTag("page-carousel").performTouchInput { swipeLeft() }
                    compose.onNodeWithText("Edit page").performScrollTo().performClick()
                }
            }
            compose.onNodeWithText("Coolant alerts").performScrollTo().performClick()
            capture("customize-limits")
            compose.onNodeWithText("Done").performScrollTo().performClick()
            compose.onNodeWithText("Review and send").performScrollTo().performClick()
            capture("customize-review")
            compose.onNodeWithText("Send to gauge").performScrollTo().assertIsEnabled()
            capture("customize-send")
            val before = model.operation.id
            sendStarted = true
            compose.onNodeWithText("Send to gauge").performClick()
            compose.waitUntil(120_000) { model.operation.id > before && model.operation.terminal }
            assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
            assertTrue(isConfirmedSetup(model.activeConfigRevision, model.expectedSentDigest, model.runtimeIdentity))
            confirmed = true
            compose.onNodeWithText("Saved & running on gauge").assertIsDisplayed()
            capture("configuration-confirmed")
            report.append("Three-reading revision: ${model.activeConfigRevision}\nThree-reading digest: ${model.expectedSentDigest}\n")
                .append("Running revision and digest matched; trial cleared.\n")
        } finally {
            // Never blindly retry an uncertain write. Only restore after the first send is proven active.
            if (confirmed) {
                compose.runOnUiThread { model.activeDocumentRead(original); model.adoptGaugeDraft() }
                assertTrue(sameSettings(originalDraft, model.draft))
                runOperation(model::checkGaugeForReview) // Fresh protected base, not the original revision/hash.
                runOperation(model::sendNumericConfiguration)
                assertTrue(isConfirmedSetup(model.activeConfigRevision, model.expectedSentDigest, model.runtimeIdentity))
                runOperation(model::checkGaugeForReview)
                val finalDocument = requireNotNull(model.activeDocument)
                assertTrue(sameSettings(originalDraft,
                    requireNotNull(GaugeDraftComparison.savedDraft(finalDocument, model.profileCollection.activeId))))
                restored = true
                capture("configuration-restored")
                report.append("Restored revision: ${finalDocument.revision}\nRestored digest: ${finalDocument.sha256}\n")
                    .append("Original pages, layouts, bindings and limits verified after restoration.\n")
            }
            report.append("Send started: $sendStarted; confirmed: $confirmed; restored: $restored\n")
            File(output, "result.txt").writeText(report.toString())
        }
    }
}
