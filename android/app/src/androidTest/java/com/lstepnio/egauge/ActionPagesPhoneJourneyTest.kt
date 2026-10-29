package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        compose.onNodeWithText("Action page options").assertIsDisplayed()
        compose.onNodeWithText("Air up").performClick()
        compose.onNodeWithText("Duration").assertIsDisplayed()
        compose.onNodeWithText("Coolant limit").assertIsDisplayed()
        compose.onNodeWithText("Back").performScrollTo().performClick()
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
        // Direct protected reads catch an unresolved command journal before a
        // Jeep trip. A readable configuration alone cannot establish readiness.
        compose.runOnUiThread { model.setConnectionForeground(false) }
        val commandStates = try {
            val before = model.operation.id
            compose.runOnUiThread { model.readRunningFirmware() }
            compose.waitUntil(30_000) {
                model.operation.id > before && model.operation.terminal
            }
            assertEquals("Firmware checked", model.operation.title)
            args.getString("expectedFirmwareElf")?.let {
                assertEquals(it, model.bootIdentity?.elfSha256)
            }
            val context = instrumentation.targetContext
            val device = context.getSystemService(BluetoothManager::class.java).adapter
                .getRemoteDevice(requireNotNull(model.rememberedGaugeId))
            val client = GaugeConfigTransferClient(context)
            runBlocking {
                withTimeout(45_000) {
                    VehicleCommandKind.supported(model.capabilities?.vehicleCommandsVersion, model.capabilities?.burnoutActionsVersion).map { client.readVehicleCommand(device, it) }
                }
            }.also { states ->
                assertTrue(
                    states.all {
                        it.revision == document.revision && !it.qualified && !it.busy &&
                            it.phase == VehicleCommandPhase.Unavailable
                    }
                )
            }
        } finally {
            compose.runOnUiThread { model.setConnectionForeground(true) }
        }
        File(output, "result.txt").writeText(
            "Real paired Pixel opened and dismissed Gauge > Action pages.\n" +
                "Typed action-page capability negotiated; profiles, per-gauge assignments, running revision/digest and settings unchanged.\n" +
                "Direct protected command states: ${commandStates.joinToString { "${it.kind}: ${it.phase}" }}.\n" +
                "No unresolved command journal; both controllers remain unavailable without vehicle inputs.\n" +
                "No save, configuration send or vehicle command invoked.\n"
        )
    }
}
