package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
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

/** Explicit physical read-only opt-in. No configuration sends, vehicle reads or firmware writes. */
@RunWith(AndroidJUnit4::class)
class AutomaticConnectionJourneyTest {
    private val allowed = InstrumentationRegistry.getArguments().getString("allowGaugeRead") == "true"
    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun openingResumingAndBluetoothRecoveryNeedNoConnectTap() {
        assumeTrue("Requires the paired bench gauge", allowed)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "automatic-connection").apply { mkdirs() }
        val started = android.os.SystemClock.elapsedRealtime()
        fun readyAfter(time: Long) {
            compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready &&
                (model.connection.checkedAtElapsedMs ?: 0) > time }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Gauge ready").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Gauge ready").assertIsDisplayed()
            assertEquals(OwnerAccess.AUTHENTICATED, model.ownerAccess)
            val document = requireNotNull(model.activeDocument)
            assertTrue(isConfirmedSetup(document.revision, document.sha256, model.runtimeIdentity))
        }
        fun capture(name: String) {
            compose.waitForIdle()
            assertEquals("com.lstepnio.egauge", instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
            File(output, "$name.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        readyAfter(0)
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        val original = requireNotNull(model.activeDocument)
        val target = model.rememberedGaugeId
        capture("opened")
        val beforeResume = requireNotNull(model.connection.checkedAtElapsedMs)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        readyAfter(beforeResume)
        capture("resumed")
        val toggle = InstrumentationRegistry.getArguments().getString("toggleBluetooth") == "true"
        if (toggle) {
            try {
                instrumentation.uiAutomation.executeShellCommand("svc bluetooth disable").close()
                compose.waitUntil(60_000) { model.connection.phase == ConnectionPhase.BluetoothOff }
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Bluetooth is off").fetchSemanticsNodes().isNotEmpty() }
                capture("bluetooth-off")
            } finally {
                instrumentation.uiAutomation.executeShellCommand("svc bluetooth enable").close()
            }
            val retryAfter = android.os.SystemClock.elapsedRealtime()
            readyAfter(retryAfter)
            capture("reconnected")
        }
        assertEquals(target, model.rememberedGaugeId)
        assertEquals(original.revision, model.activeDocument?.revision)
        assertEquals(original.sha256, model.activeDocument?.sha256)
        assertFalse(model.configurationNeedsReview)
        assertEquals(OperationStage.IDLE, model.operation.stage)
        File(output, "result.txt").writeText("Physical automatic connection validation\n" +
            "Opening to protected confirmation: $elapsed ms\n" +
            "Resume: protected confirmation refreshed without a tap\n" +
            "Bluetooth off/on recovery: $toggle\n" +
            "Gauge identity unchanged; saved and running revision ${original.revision} retained\n" +
            "Configuration digest unchanged: ${original.sha256}\n" +
            "No configuration, vehicle or firmware action invoked\n")
    }
}
