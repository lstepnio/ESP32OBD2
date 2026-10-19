package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Owner opt-in: interrupt a read-only maintenance socket, restore Wi-Fi, then reconcile. */
@RunWith(AndroidJUnit4::class)
class WifiMaintenanceRecoveryJourneyTest {
    @get:Rule val compose = activityTestRule()

    @Test fun wifiLossFailsTheOldSessionAndFreshSessionPreservesSetup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires owner authorization for maintenance Wi-Fi checks",
            args.getString("allowMaintenanceWifi") == "true")
        val context = instrumentation.targetContext
        val model = (context.applicationContext as EGaugeApplication).model
        assertFalse(model.alertMonitoring)
        assertFalse(model.phoneAlertsEnabled)
        val wifiManager = context.getSystemService(WifiManager::class.java)
        assumeTrue("Wi-Fi must already be enabled so the test can restore its original state", wifiManager.isWifiEnabled)
        val device = context.getSystemService(BluetoothManager::class.java).adapter
            .getRemoteDevice(requireNotNull(model.rememberedGaugeId))
        val client = GaugeConfigTransferClient(context)
        fun wifiEnabled(enabled: Boolean) {
            val command = "svc wifi ${if (enabled) "enable" else "disable"}"
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        runBlocking {
            withTimeout(240_000) {
                val before = client.readBootIdentity(device)
                assertEquals(args.getString("expectedFirmwareVersion"), before.version)
                assertEquals(args.getString("expectedFirmwareElf"), before.elfSha256)
                assertEquals(2, before.otaState)
                val boot = requireNotNull(client.readAlertBatch(device, 0, 0)).boot
                val document = requireNotNull(client.readActiveDocument(device))
                val settings = client.readDisplaySettings(device)
                val first = client.openWifiBulk(device)
                var failureType: String? = null
                var failureMs = 0L
                try {
                    WifiBulkClient(context, first).use { wifi ->
                        assertEquals(56, wifi.otaStatus().size)
                        wifiEnabled(false)
                        withTimeout(15_000) { while (wifiManager.isWifiEnabled) delay(100) }
                        val started = SystemClock.elapsedRealtime()
                        try {
                            wifi.configurationStatus()
                            fail("An interrupted maintenance socket must not report a successful read")
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failureType = error.javaClass.simpleName
                            failureMs = SystemClock.elapsedRealtime() - started
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        // Restore the phone before any protected cleanup/readback, including on failure.
                        wifiEnabled(true)
                        withTimeout(15_000) { while (!wifiManager.isWifiEnabled) delay(100) }
                        client.closeWifiBulk(device)
                    }
                }
                assertNotNull("The lost network must fail explicitly", failureType)
                assertTrue("Lost read must finish within its 20-second IO deadline plus cleanup margin",
                    failureMs < 25_000)
                val reopenedAt = SystemClock.elapsedRealtime()
                val fresh = client.openWifiBulk(device)
                try {
                    assertNotEquals(first.sessionId, fresh.sessionId)
                    WifiBulkClient(context, fresh).use { wifi ->
                        assertEquals(56, wifi.otaStatus().size)
                        assertEquals(64, wifi.configurationStatus().size)
                    }
                } finally { client.closeWifiBulk(device) }
                val recoveryMs = SystemClock.elapsedRealtime() - reopenedAt
                assertEquals(before, client.readBootIdentity(device))
                assertEquals(boot, requireNotNull(client.readAlertBatch(device, 0, 0)).boot)
                val after = requireNotNull(client.readActiveDocument(device))
                assertEquals(document.revision, after.revision)
                assertEquals(document.sha256, after.sha256)
                assertEquals(settings, client.readDisplaySettings(device))
                File(context.getExternalFilesDir(null), "wifi-recovery-result.txt").writeText(
                    "Read-only Wi-Fi loss recovery\n" +
                        "Lost socket failed explicitly: $failureType after $failureMs ms\n" +
                        "Fresh session authenticated both status reads and closed in $recoveryMs ms\n" +
                        "Exact image/health, boot nonce, revision ${document.revision}, digest and settings unchanged\n" +
                        "Phone Wi-Fi restored; no firmware/configuration/ECU writes or bond changes\n")
            }
        }
    }
}
