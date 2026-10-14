package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit maintenance opt-in. Run with the App stopped to exclude other BLE owners. */
@RunWith(AndroidJUnit4::class)
class WifiMaintenanceJourneyTest {
    @Test fun privateNetworkReopensAndPreservesConfirmedSetup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires owner authorization for maintenance Wi-Fi checks",
            args.getString("allowMaintenanceWifi") == "true")
        val context = instrumentation.targetContext
        val model = (context.applicationContext as EGaugeApplication).model
        assertFalse("Stop App monitoring before an isolated transport check", model.alertMonitoring)
        assertFalse("Phone monitoring must be off for this isolated check", model.phoneAlertsEnabled)
        val device = context.getSystemService(BluetoothManager::class.java).adapter
            .getRemoteDevice(requireNotNull(model.rememberedGaugeId))
        val client = GaugeConfigTransferClient(context)
        val report = StringBuilder("Physical maintenance Wi-Fi validation\n")
        runBlocking {
            withTimeout(240_000) {
                val before = client.readBootIdentity(device)
                assertEquals(args.getString("expectedFirmwareVersion"), before.version)
                assertEquals(args.getString("expectedFirmwareElf"), before.elfSha256)
                assertEquals(2, before.otaState)
                val document = requireNotNull(client.readActiveDocument(device))
                val settings = client.readDisplaySettings(device)
                var previousSession: Long? = null
                repeat(2) { index ->
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
                    assertEquals("No reset or image change during maintenance", before, client.readBootIdentity(device))
                    report.append("Cycle ${index + 1}: protected open $opened ms; authenticated Wi-Fi status reads and protected close passed\n")
                }
                val after = requireNotNull(client.readActiveDocument(device))
                assertEquals(document.revision, after.revision)
                assertEquals(document.sha256, after.sha256)
                assertEquals(settings, client.readDisplaySettings(device))
                report.append("Version ${before.version}; ELF ${before.elfSha256}; OTA health 2 retained\n")
                    .append("Revision ${document.revision}, digest and settings unchanged; no firmware/configuration/ECU mutation\n")
            }
        }
        File(context.getExternalFilesDir(null), "wifi-maintenance-result.txt").writeText(report.toString())
    }
}
