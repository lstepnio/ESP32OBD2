package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit maintenance opt-in. Relaunch with automatic reads disabled to isolate the transport. */
@RunWith(AndroidJUnit4::class)
class WifiMaintenanceJourneyTest {
    @get:Rule val compose = activityTestRule()

    @Test fun privateNetworkReopensAndPreservesConfirmedSetup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Requires owner authorization for maintenance Wi-Fi checks",
            args.getString("allowMaintenanceWifi") == "true"
        )
        val cycles = args.getString("maintenanceCycles")?.toInt() ?: 2
        require(cycles in 1..30) { "Maintenance cycles must be between 1 and 30" }
        val context = instrumentation.targetContext
        val model = (context.applicationContext as EGaugeApplication).model
        assertFalse("Stop App monitoring before an isolated transport check", model.alertMonitoring)
        assertFalse(
            "Phone monitoring must be off for this isolated check",
            model.phoneAlertsEnabled
        )
        val device = context.getSystemService(BluetoothManager::class.java).adapter
            .getRemoteDevice(requireNotNull(model.rememberedGaugeId))
        val client = GaugeConfigTransferClient(context)
        val report = StringBuilder("Physical maintenance Wi-Fi validation\n")
        runBlocking {
            withTimeout(120_000L + cycles * 90_000L) {
                val before = client.readBootIdentity(device)
                assertEquals(args.getString("expectedFirmwareVersion"), before.version)
                assertEquals(args.getString("expectedFirmwareElf"), before.elfSha256)
                assertEquals(2, before.otaState)
                val bootNonce = requireNotNull(client.readAlertBatch(device, 0, 0)).boot
                val document = requireNotNull(client.readActiveDocument(device))
                val settings = client.readDisplaySettings(device)
                var previousSession: Long? = null
                repeat(cycles) { index ->
                    val started = SystemClock.elapsedRealtime()
                    val session = client.openWifiBulk(device)
                    val opened = SystemClock.elapsedRealtime() - started
                    try {
                        assertNotEquals(previousSession, session.sessionId)
                        previousSession = session.sessionId
                        WifiBulkClient(context, session).use { wifi ->
                            val ota = wifi.otaStatus()
                            assertEquals(56, ota.size)
                            assertEquals(4, ota[0].toInt())
                            val config = wifi.configurationStatus()
                            assertEquals(64, config.size)
                            assertEquals(3, config[0].toInt())
                        }
                    } finally {
                        client.closeWifiBulk(device)
                    }
                    assertEquals(
                        "No image change during maintenance",
                        before,
                        client.readBootIdentity(device)
                    )
                    assertEquals(
                        "No gauge reboot during maintenance",
                        bootNonce,
                        requireNotNull(client.readAlertBatch(device, 0, 0)).boot
                    )
                    report.append(
                        "Cycle ${index + 1}: protected open $opened ms; authenticated Wi-Fi status reads, protected close and unchanged boot nonce passed\n"
                    )
                }
                val after = requireNotNull(client.readActiveDocument(device))
                assertEquals(document.revision, after.revision)
                assertEquals(document.sha256, after.sha256)
                assertEquals(settings, client.readDisplaySettings(device))
                report.append(
                    "Version ${before.version}; ELF ${before.elfSha256}; OTA health 2 retained\n"
                )
                    .append(
                        "Revision ${document.revision}, digest and settings unchanged; no firmware/configuration/ECU mutation\n"
                    )
            }
        }
        File(
            context.getExternalFilesDir(null),
            "wifi-maintenance-result.txt"
        ).writeText(report.toString())
    }
}
