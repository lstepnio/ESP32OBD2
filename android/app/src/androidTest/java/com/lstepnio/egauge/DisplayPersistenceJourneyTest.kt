package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.ui.state.isConfirmedSetup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in bench write: change cycling, resend the same setup, verify persistence and restore cycling. */
@RunWith(AndroidJUnit4::class)
class DisplayPersistenceJourneyTest {
    private val allowed = InstrumentationRegistry.getArguments().getString("allowGaugeWrite") == "true"
    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun cycleAndDisplaySettingsSurviveSetupRestart() {
        assumeTrue("Requires explicit bench write authorization", allowed)
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready && model.activeDocument != null && model.runtimeIdentity != null && model.displaySettings != null }
        val original = requireNotNull(model.displaySettings)
        val document = requireNotNull(model.activeDocument)
        assertTrue(isConfirmedSetup(document.revision, document.sha256, model.runtimeIdentity))
        assertEquals(document.vehicleProfileId, model.profileCollection.activeId)
        assertNotNull(model.sentDraft)
        assertTrue(original.version >= 3)
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "display-persistence").apply { mkdirs() }
        val report = StringBuilder("Display persistence bench test\nOriginal: $original\n")
        fun cycle(seconds: Int) {
            val previous = model.operation.id
            compose.runOnUiThread { model.savePageCycleSeconds(seconds) }
            compose.waitUntil(60_000) { model.operation.id > previous && model.operation.terminal }
            assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
            assertEquals(seconds, model.displaySettings?.cycleSeconds)
        }
        File(output, "result.txt").writeText(report.toString())
        var originalFailure: Throwable? = null
        try {
            cycle(5)
            report.append("Five-second interval confirmed.\n")
            File(output, "result.txt").writeText(report.toString())
            val previous = model.operation.id
            val started = android.os.SystemClock.elapsedRealtime()
            compose.runOnUiThread { model.sendNumericConfiguration() }
            compose.waitUntil(150_000) { model.operation.id > previous && model.operation.terminal }
            assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
            assertEquals(document.revision + 1, model.activeConfigRevision)
            assertTrue(isConfirmedSetup(model.activeConfigRevision, model.expectedSentDigest, model.runtimeIdentity))
            // Observe the ordinary serialized settings poll after restart, never open a competing GATT session.
            compose.waitUntil(60_000) { (model.settingsObservedAtElapsedMs ?: 0) > started && !model.settingsCheckFailed }
            val current = requireNotNull(model.displaySettings)
            report.append("After restart: $current\nConfirmation: ${android.os.SystemClock.elapsedRealtime()-started} ms\nRevision: ${model.activeConfigRevision}\nDigest: ${model.expectedSentDigest}\n")
            assertEquals(5, current.cycleSeconds)
            assertEquals(original.rotation, current.rotation)
            assertEquals(original.brightness, current.brightness)
            assertEquals(original.units, current.units)
            compose.waitUntil(30_000) { model.activeDocument?.revision == document.revision + 1 }
            val before = org.json.JSONObject(document.json).also { it.remove("baseRevision") }
            val after = org.json.JSONObject(requireNotNull(model.activeDocument).json).also { it.remove("baseRevision") }
            assertEquals(before.toString(), after.toString())
            report.append("Exact setup and action retained.\n")
        } catch (failure: Throwable) {
            originalFailure = failure
            report.append("Failure: ${failure.javaClass.simpleName}: ${failure.message}\n")
            throw failure
        } finally {
            try {
                val checkedAfter = android.os.SystemClock.elapsedRealtime()
                compose.waitUntil(60_000) { (model.settingsObservedAtElapsedMs ?: 0) >= checkedAfter && !model.settingsCheckFailed }
                val current = requireNotNull(model.displaySettings)
                if (current.cycleSeconds == 5 && current.rotation == original.rotation &&
                    current.brightness == original.brightness && current.units == original.units) {
                    cycle(original.cycleSeconds)
                    report.append("Restored interval: ${model.displaySettings?.cycleSeconds}\n")
                } else {
                    report.append("Restore skipped: settings changed during test: $current\n")
                }
            } catch (cleanup: Throwable) {
                report.append("Restore not confirmed: ${cleanup.message}\n")
                if (originalFailure != null) originalFailure.addSuppressed(cleanup) else throw cleanup
            } finally {
                File(output, "result.txt").writeText(report.toString())
            }
        }
    }

    @Test fun restoreBenchCycleAfterInterruptedTest() {
        val requested = InstrumentationRegistry.getArguments().getString("restoreCycleSeconds")?.toIntOrNull()
        assumeTrue("Opt-in interrupted-test restoration only", allowed && requested != null)
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready && model.displaySettings != null && !model.settingsCheckFailed }
        assertEquals("Leave an owner-changed interval alone", 5, model.displaySettings?.cycleSeconds)
        val previous = model.operation.id
        compose.runOnUiThread { model.savePageCycleSeconds(requireNotNull(requested)) }
        compose.waitUntil(60_000) { model.operation.id > previous && model.operation.terminal }
        assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
        assertEquals(requested, model.displaySettings?.cycleSeconds)
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "display-persistence").apply { mkdirs() }
        File(output, "restoration.txt").writeText("Protected settings restoration: ${model.displaySettings}\n")
    }
}
