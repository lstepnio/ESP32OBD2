package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only observer. The authorized host resets the gauge only after the checkpoint. */
@RunWith(AndroidJUnit4::class)
class GaugeRestartRecoveryJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowGaugeRestart") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun gaugeRestartThenForegroundResumeRetainsOwnerAndSetup() {
        assumeTrue("Requires an owner-authorized host-controlled gauge reset", allowed)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val model = (context.applicationContext as EGaugeApplication).model
        assertFalse("Stop monitoring before the isolated reset observer", model.alertMonitoring)
        assertFalse("Phone monitoring must be off for this check", model.phoneAlertsEnabled)
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready &&
                model.activeDocument != null && model.displaySettings != null &&
                model.bootIdentity != null
        }
        val image = requireNotNull(model.bootIdentity)
        assertEquals(args.getString("expectedFirmwareVersion"), image.version)
        assertEquals(args.getString("expectedFirmwareElf"), image.elfSha256)
        assertEquals(2, image.otaState)
        val document = requireNotNull(model.activeDocument)
        val settings = requireNotNull(model.displaySettings)
        val profiles = model.profileCollection.profiles
        val identity = model.rememberedGaugeId
        val device = context.getSystemService(BluetoothManager::class.java).adapter
            .getRemoteDevice(requireNotNull(identity))
        val client = GaugeConfigTransferClient(context)
        fun pausePollingAndDrainOwner() {
            val previous = model.operation.id
            compose.runOnUiThread {
                model.setConnectionForeground(false)
                // This ordinary read acquires the App coordinator's lease and awaits
                // cancelled polling cleanup before the isolated observer uses GATT.
                model.readRunningFirmware()
            }
            compose.waitUntil(30_000) { model.operation.id > previous && model.operation.terminal }
            assertEquals("Firmware checked", model.operation.title)
        }
        pausePollingAndDrainOwner()
        try {
            val boot = runBlocking { requireNotNull(client.readAlertBatch(device, 0, 0)).boot }
            val started = SystemClock.elapsedRealtime()
            instrumentation.sendStatus(
                2,
                Bundle().apply {
                    putString("checkpoint", "READY_FOR_GAUGE_RESTART")
                }
            )
            runBlocking {
                withTimeout(120_000) {
                    while (true) {
                        val observed = try {
                            client.readAlertBatch(device, 0, 0)?.boot
                        } catch (
                            error: Exception
                        ) {
                            if (error is CancellationException) throw error
                            null // Expected transient disconnect while the host resets the device.
                        }
                        if (observed != null && observed != boot) break
                        delay(500)
                    }
                }
            }
            val restartedAt = SystemClock.elapsedRealtime()
            compose.runOnUiThread { model.setConnectionForeground(true) }
            compose.waitUntil(90_000) {
                model.connection.phase == ConnectionPhase.Ready &&
                    (model.connection.checkedAtElapsedMs ?: 0) > restartedAt &&
                    (model.settingsObservedAtElapsedMs ?: 0) > restartedAt &&
                    !model.settingsCheckFailed
            }
            val resumeMs = SystemClock.elapsedRealtime() - restartedAt
            pausePollingAndDrainOwner()
            val after = runBlocking {
                withTimeout(90_000) {
                    Triple(
                        client.readBootIdentity(device),
                        requireNotNull(client.readActiveDocument(device)),
                        client.readDisplaySettings(device)
                    )
                }
            }
            assertEquals(image, after.first)
            assertEquals(document.revision, after.second.revision)
            assertEquals(document.sha256, after.second.sha256)
            assertEquals(settings, after.third)
            assertEquals(identity, model.rememberedGaugeId)
            assertEquals(profiles, model.profileCollection.profiles)
            assertEquals(OwnerAccess.AUTHENTICATED, model.ownerAccess)
            File(context.getExternalFilesDir(null), "gauge-restart-recovery-result.txt").writeText(
                "Host-controlled gauge reset observed by changed protected boot nonce\n" +
                    "Same-App foreground resume to automatic fresh connection/settings: $resumeMs ms\n" +
                    "Whole checkpoint/reboot/readback: ${SystemClock.elapsedRealtime() - started} ms\n" +
                    "Exact healthy image, authenticated owner, revision ${document.revision}, digest, settings and profiles retained\n" +
                    "No reconnect tap, firmware/configuration write, bond reset or ECU action\n" +
                    "Serial reset does not qualify rail-power loss or physical display/touch\n"
            )
        } finally {
            compose.runOnUiThread { model.setConnectionForeground(true) }
        }
    }
}
