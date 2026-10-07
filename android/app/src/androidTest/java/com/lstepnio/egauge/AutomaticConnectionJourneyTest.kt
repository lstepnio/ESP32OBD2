package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
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

/** Explicit physical read-only opt-in. No configuration sends, vehicle writes or firmware writes. */
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
        val sharedStatus = SemanticsMatcher("Universal connection status action") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show connection status"
        }
        fun readyAfter(time: Long) {
            compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready &&
                (model.connection.checkedAtElapsedMs ?: 0) > time }
            compose.waitUntil(5_000) { compose.onAllNodes(sharedStatus).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(sharedStatus).assertIsDisplayed()
            compose.waitUntil(30_000) { model.displaySettings != null && !model.settingsCheckFailed &&
                (model.settingsObservedAtElapsedMs ?: 0) > time }
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
        val originalSettings = requireNotNull(model.displaySettings)
        val originalProfiles = model.profileCollection.profiles
        val expectedVersion = InstrumentationRegistry.getArguments().getString("expectedFirmwareVersion")
        val expectedElf = InstrumentationRegistry.getArguments().getString("expectedFirmwareElf")
        if (expectedVersion != null) {
            compose.waitUntil(30_000) { model.bootIdentity != null }
            assertEquals(expectedVersion, model.bootIdentity?.version)
            assertEquals(2, model.bootIdentity?.otaState)
            if (expectedElf != null) assertEquals(expectedElf, model.bootIdentity?.elfSha256)
        }
        val verifiedBoot = model.bootIdentity
        val target = model.rememberedGaugeId
        capture("opened")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val beforeResume = android.os.SystemClock.elapsedRealtime()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        readyAfter(beforeResume)
        val resumeElapsed = android.os.SystemClock.elapsedRealtime() - beforeResume
        capture("resumed")
        val toggle = InstrumentationRegistry.getArguments().getString("toggleBluetooth") == "true"
        var bluetoothRecoveryElapsed: Long? = null
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
            bluetoothRecoveryElapsed = android.os.SystemClock.elapsedRealtime() - retryAfter
            capture("reconnected")
        }
        val picker = InstrumentationRegistry.getArguments().getString("checkGaugePickerRecovery") == "true"
        if (picker) {
            val beforePicker = android.os.SystemClock.elapsedRealtime()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Your gauges").performScrollTo().performClick()
            compose.onNodeWithText("Add gauge").performClick()
            compose.waitUntil(30_000) { model.connection.phase == ConnectionPhase.ChooseGauge }
            compose.onNodeWithContentDescription("Go back").performClick()
            readyAfter(beforePicker)
            capture("picker-cancelled")
        }
        assertEquals(target, model.rememberedGaugeId)
        assertEquals(original.revision, model.activeDocument?.revision)
        assertEquals(original.sha256, model.activeDocument?.sha256)
        assertEquals(originalSettings, model.displaySettings)
        assertEquals(originalProfiles, model.profileCollection.profiles)
        assertFalse(model.configurationNeedsReview)
        assertEquals(OperationStage.IDLE, model.operation.stage)
        File(output, "verified-active.json").writeText(requireNotNull(model.activeDocument).json)
        File(output, "result.txt").writeText("Physical automatic connection validation\n" +
            "Opening to protected confirmation: $elapsed ms\n" +
            "Running firmware version: ${verifiedBoot?.version}; OTA health: ${verifiedBoot?.otaState}\n" +
            "Resume: protected confirmation and settings refreshed without a tap in $resumeElapsed ms\n" +
            "Bluetooth off/on recovery: $toggle; recovery duration: $bluetoothRecoveryElapsed ms\n" +
            "Add-gauge cancellation recovery: $picker\n" +
            "Gauge identity unchanged; saved and running revision ${original.revision} retained\n" +
            "Configuration digest unchanged: ${original.sha256}\n" +
            "Settings populated and refreshed automatically; brightness, rotation, units and cycle unchanged\n" +
            "No configuration, vehicle write or firmware action invoked\n")
    }
}
