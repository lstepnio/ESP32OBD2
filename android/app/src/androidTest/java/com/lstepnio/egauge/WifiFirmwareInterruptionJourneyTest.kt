package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Separately authorized signed update; the host resets only at the emitted checkpoint. */
@RunWith(AndroidJUnit4::class)
class WifiFirmwareInterruptionJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowWifiFirmwareInterruption") == "true"
    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun interruptedCandidateReconcilesPreviousHealthyImageBeforeRetry() {
        assumeTrue("Requires explicit signed OTA interruption authorization", allowed)
        val target = args.getString("interruptPercent")?.toInt() ?: 0
        require(target in setOf(0, 10, 50, 90)) { "Use 0 for verified/preactivation, or 10/50/90 percent" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val model = (context.applicationContext as EGaugeApplication).model
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready &&
            model.activeDocument != null && model.displaySettings != null && model.bootIdentity != null }
        assertFalse(model.alertMonitoring)
        assertFalse(model.phoneAlertsEnabled)
        val beforeBoot = requireNotNull(model.bootIdentity)
        assertEquals(args.getString("currentFirmwareVersion"), beforeBoot.version)
        assertEquals(args.getString("currentFirmwareElf"), beforeBoot.elfSha256)
        assertEquals(2, beforeBoot.otaState)
        val document = requireNotNull(model.activeDocument)
        val settings = requireNotNull(model.displaySettings)
        val profiles = model.profileCollection.profiles
        compose.runOnUiThread { model.checkHostedFirmware() }
        compose.waitUntil(120_000) { !model.hostedUpdateBusy }
        assertEquals(model.hostedUpdateMessage, args.getString("candidateVersion"), model.hostedUpdate?.release?.version)
        compose.runOnUiThread { model.downloadHostedFirmware() }
        compose.waitUntil(120_000) { !model.hostedUpdateBusy }
        val bundle = model.updateBundle()
        assertEquals(args.getString("candidateElf"), bundle.elfSha256.joinToString("") { "%02x".format(it) })
        val started = SystemClock.elapsedRealtime()
        val priorOperation = model.operation.id
        compose.runOnUiThread {
            model.setDebugOtaPauseBeforeActivationMs(if (target == 0) 60_000 else 0)
            model.installSelectedUpdate()
        }
        try {
            fun atCheckpoint(): Boolean = model.operation.id > priorOperation &&
                model.operation.kind == OperationKind.UPDATE && if (target == 0)
                model.operation.stage == OperationStage.VERIFYING &&
                    model.operation.detail?.contains("Debug activation pause active") == true
            else model.operation.stage == OperationStage.SENDING &&
                (model.operation.progressPercent ?: -1) >= target
            compose.waitUntil(180_000) { atCheckpoint() ||
                (model.operation.id > priorOperation && model.operation.kind == OperationKind.UPDATE && model.operation.terminal) }
            assertTrue("Update must reach the selected interruption boundary: ${model.operation}", atCheckpoint())
            val observed = model.operation.progressPercent
            if (target != 0) assertTrue("Do not reset after accidentally reaching complete transfer", requireNotNull(observed) < 100)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("checkpoint", "READY_FOR_OTA_RESET")
                putInt("observedPercent", observed ?: -1)
                putInt("targetPercent", target)
            })
            compose.waitUntil(180_000) { model.operation.kind == OperationKind.UPDATE && model.operation.terminal }
            val interrupted = model.operation
            assertTrue("Interrupted transfer cannot report installed", interrupted.stage in
                setOf(OperationStage.FAILED, OperationStage.OUTCOME_UNKNOWN))
            val failedAt = SystemClock.elapsedRealtime()
            compose.waitUntil(120_000) { model.connection.phase == ConnectionPhase.Ready &&
                (model.connection.checkedAtElapsedMs ?: 0) > failedAt &&
                (model.settingsObservedAtElapsedMs ?: 0) > failedAt && !model.settingsCheckFailed &&
                model.updateRecoveryResult?.state == UpdateRecoveryState.PREVIOUS_FIRMWARE }
            val recoveredMs = SystemClock.elapsedRealtime() - failedAt
            val previous = model.operation.id
            compose.runOnUiThread { model.setConnectionForeground(false); model.readRunningFirmware() }
            compose.waitUntil(30_000) { model.operation.id > previous && model.operation.terminal }
            assertEquals("Firmware checked", model.operation.title)
            assertEquals(beforeBoot, model.bootIdentity)
            val device = context.getSystemService(BluetoothManager::class.java).adapter
                .getRemoteDevice(requireNotNull(model.rememberedGaugeId))
            val client = GaugeConfigTransferClient(context)
            val actual = runBlocking { withTimeout(90_000) {
                requireNotNull(client.readActiveDocument(device)) to client.readDisplaySettings(device)
            } }
            assertEquals(document.revision, actual.first.revision)
            assertEquals(document.sha256, actual.first.sha256)
            assertEquals(settings, actual.second)
            assertEquals(profiles, model.profileCollection.profiles)
            assertNull(UpdateRecoveryJournal(context).read())
            File(context.getExternalFilesDir(null), "wifi-interruption-$target-result.txt").writeText(
                "Target $target; observed checkpoint progress $observed; total ${SystemClock.elapsedRealtime() - started} ms\n" +
                    "Interrupted outcome: ${interrupted.stage}; ${interrupted.title}\n" +
                    "Automatic previous-image reconciliation/settings: $recoveredMs ms\n" +
                    "Previous exact version ${beforeBoot.version}, ELF ${beforeBoot.elfSha256}, partition ${beforeBoot.partitionAddress}, health 2 retained\n" +
                    "Revision ${document.revision}, digest, settings and profiles unchanged; journal cleared only after authenticated reconciliation\n" +
                    "No bond/configuration/ECU operation or automatic update retry\n")
        } finally {
            compose.runOnUiThread { model.setDebugOtaPauseBeforeActivationMs(0); model.setConnectionForeground(true) }
        }
    }
}
