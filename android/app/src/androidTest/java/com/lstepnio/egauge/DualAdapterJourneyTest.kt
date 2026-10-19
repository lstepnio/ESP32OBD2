package com.lstepnio.egauge

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.junit4.*
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reads gauge state only, with an explicit opt-in. Configure two real adapters before running.
 * Optional interruptSource asks the operator to unplug and reconnect that radio during
 * bounded waits; allowPhoneBluetoothToggle also opts into an Android radio off/on cycle.
 * This test never sends configuration or update commands. */
@RunWith(AndroidJUnit4::class)
class DualAdapterJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowDualGaugeRead") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun independentSourcesResumeAndRecover() {
        assumeTrue("Requires an explicitly prepared dual-adapter bench", allowed)
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "dual-adapter-recovery"
        ).apply {
            mkdirs()
        }
        listOf("result.txt", "failure.txt", "stages.txt").forEach { File(output, it).delete() }
        val model = (compose.activity.application as EGaugeApplication).model
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready }
        args.getString("expectedFirmwareVersion")?.let { expected ->
            compose.waitUntil(30_000) { model.bootIdentity != null }
            assertEquals(expected, model.bootIdentity?.version)
            assertEquals(args.getString("expectedFirmwareElf"), model.bootIdentity?.elfSha256)
            assertEquals(2, model.bootIdentity?.otaState)
        }
        assertTrue("Enable both adapters for this gauge before running", model.bothAdapters)
        assertEquals(1, model.capabilities?.dualAdapterVersion)
        val original = requireNotNull(model.activeDocument)
        val started = SystemClock.elapsedRealtime()
        val report = StringBuilder()
        fun checkpoint(name: String) {
            report.append("${SystemClock.elapsedRealtime() - started} ms: $name\n")
            File(output, "stages.txt").writeText(report.toString())
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply {
                    putString("dual_stage", name)
                }
            )
        }
        fun healthy(after: Long) {
            try {
                compose.waitUntil(90_000) {
                    listOf("ECM", "TCM").all { source ->
                        val status = model.adapterStatuses[source]
                        val diagnostics = model.diagnosticsBySource[source]
                        status?.phase == 4 && status.bound && !status.simulated &&
                            status.vehicleId == original.vehicleProfileId &&
                            diagnostics?.source == source && diagnostics.connected &&
                            !diagnostics.simulated &&
                            diagnostics.revision == original.revision &&
                            (model.diagnosticsCheckedAt[source] ?: 0) > after
                    }
                }
            } catch (failure: Throwable) {
                File(output, "failure.txt").writeText(
                    listOf("ECM", "TCM").joinToString("\n") { source ->
                        val status = model.adapterStatuses[source]
                        val diagnostic = model.diagnosticsBySource[source]
                        "$source phase=${status?.phase} bound=${status?.bound} simulated=${status?.simulated} " +
                            "connected=${diagnostic?.connected} revision=${diagnostic?.revision} session=${diagnostic?.session} " +
                            "lastCheck=${model.diagnosticsCheckedAt[source]}"
                    }
                )
                throw failure
            }
        }
        healthy(0)
        checkpoint("BOTH_SOURCES_CONFIRMED")
        val sessions = model.diagnosticsBySource.mapValues { it.value.session }
        val beforeResume = android.os.SystemClock.elapsedRealtime()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        healthy(beforeResume)
        checkpoint("RESUME_CONFIRMED")
        assertEquals(sessions, model.diagnosticsBySource.mapValues { it.value.session })
        val phoneToggle = args.getString("allowPhoneBluetoothToggle") == "true"
        if (phoneToggle) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                .bufferedReader().use { it.readText().trim() }
            try {
                // Drain to EOF so closing the descriptor cannot interrupt the platform command.
                shell("svc bluetooth disable")
                compose.waitUntil(10_000) { shell("settings get global bluetooth_on") == "0" }
                checkpoint("PHONE_PLATFORM_BLUETOOTH_OFF")
                // An admitted protected read may still be unwinding its bounded operation.
                compose.waitUntil(60_000) {
                    assertEquals(
                        "Another service re-enabled Bluetooth during the off test",
                        "0",
                        shell("settings get global bluetooth_on")
                    )
                    model.connection.phase == ConnectionPhase.BluetoothOff
                }
                checkpoint("PHONE_BLUETOOTH_OFF")
                SystemClock.sleep(15_000)
                assertEquals(
                    "Bluetooth must remain off for the entire interval",
                    "0",
                    shell("settings get global bluetooth_on")
                )
            } finally {
                shell("svc bluetooth enable")
            }
            compose.waitUntil(10_000) { shell("settings get global bluetooth_on") == "1" }
            val restoredAt = SystemClock.elapsedRealtime()
            compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready }
            healthy(restoredAt)
            assertEquals(sessions, model.diagnosticsBySource.mapValues { it.value.session })
            checkpoint("PHONE_BLUETOOTH_RECOVERY_CONFIRMED")
        }
        val interrupted = args.getString("interruptSource")
        if (interrupted != null) {
            require(interrupted in setOf("ECM", "TCM"))
            val other = if (interrupted == "TCM") "ECM" else "TCM"
            checkpoint("READY_FOR_LOSS_$interrupted")
            compose.waitUntil(120_000) {
                model.adapterStatuses[interrupted]?.phase != 4 &&
                    model.adapterStatuses[interrupted] != null
            }
            val lostAt = android.os.SystemClock.elapsedRealtime()
            compose.waitUntil(60_000) {
                (model.diagnosticsCheckedAt[other] ?: 0) > lostAt &&
                    model.diagnosticsBySource[other]?.connected == true
            }
            assertEquals(sessions[other], model.diagnosticsBySource[other]?.session)
            checkpoint("READY_FOR_RESTORE_$interrupted")
            healthy(lostAt)
            assertEquals(sessions[other], model.diagnosticsBySource[other]?.session)
            assertNotEquals(sessions[interrupted], model.diagnosticsBySource[interrupted]?.session)
            checkpoint("RECOVERY_CONFIRMED_$interrupted")
        }
        assertEquals(original.revision, model.activeDocument?.revision)
        assertEquals(original.sha256, model.activeDocument?.sha256)
        File(output, "result.txt").writeText(
            "Physical dual-source reads and resume passed; interruption=${interrupted ?: "none"}; phoneBluetoothToggle=$phoneToggle; configuration unchanged.\n" +
                report
        )
    }
}
