package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.ui.state.isConfirmedSetup
import com.lstepnio.egauge.ui.state.sameSettings
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Owner-supervised sensor qualification only. Never supplies sensor samples or runs vehicle commands. */
@RunWith(AndroidJUnit4::class)
class OrientationQualificationJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowOrientationQualification") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun fourPhysicalOrientationsPreserveSettingsAndRestoreManualAngle() {
        assumeTrue("Requires explicit owner-supervised orientation qualification", allowed)
        val expectedVersion = requireNotNull(args.getString("expectedFirmwareVersion"))
        val expectedElf = requireNotNull(args.getString("expectedFirmwareElf"))
        require(expectedVersion.isNotBlank() && expectedElf.matches(Regex("[a-f0-9]{64}")))
        val model = (compose.activity.application as EGaugeApplication).model
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "orientation-qualification").apply { mkdirs() }
        val report = StringBuilder("Owner-supervised Auto orientation qualification\n")
        fun record(message: String) {
            report.append(message).append('\n')
            File(output, "result.txt").writeText(report.toString())
        }
        fun checkpoint(name: String) {
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply {
                    putString("checkpoint", name)
                    putString("stream", "$name\n")
                }
            )
        }
        fun operation(action: () -> Unit) {
            val before = model.operation.id
            compose.runOnUiThread(action)
            compose.waitUntil(90_000) { model.operation.id > before && model.operation.terminal }
            assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
        }
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready && model.orientationSettings != null &&
                model.displaySettings != null && model.activeDocument != null && !model.localSaveBusy
        }
        assertFalse("Stop phone monitoring before qualification", model.alertMonitoring)
        assertTrue("Wi-Fi must already be enabled; this fixture never toggles the phone radio", context.getSystemService(WifiManager::class.java).isWifiEnabled)
        assertEquals(1, model.capabilities?.automaticOrientationVersion)
        operation(model::readRunningFirmware)
        assertEquals(expectedVersion, model.bootIdentity?.version)
        assertEquals(expectedElf, model.bootIdentity?.elfSha256)
        assertEquals("Exact candidate must be healthy", 2, model.bootIdentity?.otaState)
        operation(model::checkGaugeForReview)
        val document = requireNotNull(model.activeDocument)
        assertTrue(isConfirmedSetup(document.revision, document.sha256, model.runtimeIdentity))
        assertTrue("Reconcile unsent setup changes first", sameSettings(GaugeDraftComparison.savedDraft(document, model.profileCollection.activeId), model.transmittedDraft))
        val profiles = model.profileCollection
        val draft = model.draft
        val gauges = model.knownGauges
        val gaugeId = model.rememberedGaugeId
        val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(requireNotNull(gaugeId))
        val client = GaugeConfigTransferClient(context)
        val smartVersion = requireNotNull(model.capabilities).smartPageCyclingVersion

        // Disable periodic ownership, then await a serialized App operation and its bounded cleanup.
        fun <T> protectedRead(block: suspend () -> T): T {
            assertFalse(model.alertMonitoring)
            compose.runOnUiThread { model.setConnectionForeground(false) }
            try {
                operation(model::readRunningFirmware)
                assertEquals(expectedVersion, model.bootIdentity?.version)
                assertEquals(expectedElf, model.bootIdentity?.elfSha256)
                return runBlocking { withTimeout(125_000) { block() } }
            } finally {
                compose.runOnUiThread { model.setConnectionForeground(true) }
            }
        }
        fun readSettings() = protectedRead {
            val orientation = client.readOrientationSettings(device)
            val display = client.readDisplaySettings(device, smartVersion)
            assertEquals("Protected settings revision must agree", display.revision, orientation.revision)
            orientation to display
        }
        val (original, display) = readSettings()
        assertFalse("Start in saved manual mode", original.automatic)
        assertEquals("A healthy live sensor is required", 1, original.sensorStatus)
        assertTrue(original.manualAngle in 0..3)
        assertEquals(original.manualAngle, original.appliedAngle)
        assertEquals(original.manualAngle, display.rotation)
        assertTrue("Cycling preservation requires settings v3", display.version >= 3)
        val alternateCycle = if (display.cycleSeconds == 5) 10 else 5
        var enableStarted = false
        var enableConfirmed = false
        var restorationConfirmed = false
        var provenRevision = original.revision
        var uncertainWrite = false
        var failure: Throwable? = null
        var expectedConfig = document
        var configurationSendStarted = false
        var configurationConfirmed = false
        val configurationBytes = ConfigurationProjector.projectVehicle(
            context.assets.open("numeric_config_template.json").bufferedReader().use { it.readText() },
            profiles.active,
            document.revision,
            model.bothAdapters,
            if ((model.capabilities?.configurationVersion ?: 0) >= 3) 2 else 1,
            model.transmittedDraft.actions
        ).second
        val targetDigest = MessageDigest.getInstance("SHA-256").digest(configurationBytes).joinToString("") { "%02x".format(it) }
        fun functionalJson(json: String) = canonical(JSONObject(json).also { it.remove("baseRevision") })
        assertEquals("Resend must preserve the complete functional setup", functionalJson(document.json), functionalJson(configurationBytes.toString(Charsets.UTF_8)))
        fun reconcileConfiguration() {
            val (observed, runtime) = protectedRead {
                requireNotNull(client.readActiveDocument(device)) to client.readRuntimeIdentity(device)
            }
            assertTrue(isConfirmedSetup(observed.revision, observed.sha256, runtime))
            if (configurationSendStarted && observed.revision == document.revision + 1 && observed.sha256 == targetDigest) {
                assertEquals(functionalJson(document.json), functionalJson(observed.json))
                expectedConfig = observed // Update only after exact protected healthy running proof.
                configurationConfirmed = true
            } else {
                assertEquals(expectedConfig, observed)
            }
        }
        val angles = mutableSetOf<Int>()
        record("Firmware: $expectedVersion; original manual angle: ${original.manualAngle * 90}; settings revision: ${original.revision}")
        try {
            enableStarted = true
            uncertainWrite = true
            operation { model.saveOrientation(true, original.manualAngle) }
            val enabled = requireNotNull(model.orientationSettings)
            assertTrue(enabled.automatic)
            assertEquals(original.manualAngle, enabled.manualAngle)
            assertEquals(original.revision + 1, enabled.revision)
            assertEquals(1, enabled.sensorStatus)
            enableConfirmed = true
            provenRevision = enabled.revision
            uncertainWrite = false
            // The same requested brightness is a client no-op, so exercise a real reversible cycling write.
            uncertainWrite = true
            operation { model.savePageCycling(alternateCycle, if (display.supportsSmartCycle) display.smartCycle else null) }
            val (afterCycle, cycledDisplay) = readSettings()
            assertTrue("Unrelated cycling write must preserve Auto", afterCycle.automatic)
            assertEquals(original.manualAngle, afterCycle.manualAngle)
            assertEquals(enabled.revision + 1, afterCycle.revision)
            assertEquals(display.copy(cycleSeconds = alternateCycle, revision = afterCycle.revision), cycledDisplay)
            assertEquals(1, afterCycle.sensorStatus)
            provenRevision = afterCycle.revision
            uncertainWrite = false
            repeat(2) { cycle ->
                protectedRead {
                    val beforeWifi = client.readOrientationSettings(device)
                    assertTrue(beforeWifi.automatic)
                    assertEquals(1, beforeWifi.sensorStatus)
                    assertEquals(afterCycle.revision, beforeWifi.revision)
                    val startedAt = SystemClock.elapsedRealtime()
                    fun memory(label: String, snapshot: GaugeConfigTransferClient.HardwareSnapshot) {
                        record("Wi-Fi cycle ${cycle + 1} $label: internal free/min/largest ${snapshot.internalFreeBytes}/${snapshot.internalMinimumFreeBytes}/${snapshot.internalLargestBlockBytes}; PSRAM free/min ${snapshot.psramFreeBytes}/${snapshot.psramMinimumFreeBytes} bytes.")
                    }
                    if (model.capabilities?.hardwareCapacityVersion == 1) memory("before", client.readHardwareSnapshot(device))
                    try {
                        val session = client.openWifiBulk(device)
                        WifiBulkClient(context, session).use { wifi ->
                            assertEquals(56, wifi.otaStatus().size)
                            assertEquals(64, wifi.configurationStatus().size)
                            if (model.capabilities?.hardwareCapacityVersion == 1) memory("maintenance", client.readHardwareSnapshot(device))
                            delay(1_500)
                            val suspended = client.readOrientationSettings(device)
                            assertEquals("Maintenance must expose stale sensor status", 2, suspended.sensorStatus)
                            assertTrue(suspended.automatic)
                            assertEquals(original.manualAngle, suspended.manualAngle)
                            assertEquals(beforeWifi.revision, suspended.revision)
                            assertEquals("Suspension holds the applied angle", beforeWifi.appliedAngle, suspended.appliedAngle)
                            delay(500)
                            assertEquals(suspended, client.readOrientationSettings(device))
                            record("Wi-Fi cycle ${cycle + 1}: authenticated status reads; sensor status 2; applied angle held ${suspended.appliedAngle * 90}; saved Auto/revision unchanged.")
                        }
                    } finally {
                        withContext(NonCancellable) { withTimeout(45_000) { client.closeWifiBulk(device) } }
                    }
                    withTimeout(20_000) {
                        while (true) {
                            val recovered = client.readOrientationSettings(device)
                            assertTrue(recovered.automatic)
                            assertEquals(original.manualAngle, recovered.manualAngle)
                            assertEquals(beforeWifi.revision, recovered.revision)
                            if (recovered.sensorStatus == 1) break
                            assertEquals(2, recovered.sensorStatus)
                            delay(500)
                        }
                    }
                    if (model.capabilities?.hardwareCapacityVersion == 1) memory("recovered", client.readHardwareSnapshot(device))
                    record("Wi-Fi cycle ${cycle + 1}: fresh sensor status 1 recovered in ${SystemClock.elapsedRealtime() - startedAt} ms total. Suspension/recovery only; no unplug/fault-injection claim.")
                }
            }
            protectedRead {
                val initial = client.readOrientationSettings(device)
                assertTrue(initial.automatic)
                assertEquals(afterCycle.revision, initial.revision)
                assertEquals(1, initial.sensorStatus)
                checkpoint("AUTO_ORIENTATION_READY")
                record("Move the gauge deliberately through 0, 90, 180 and 270 degrees; hold each steady. Window: 120 seconds.")
                withTimeout(120_000) {
                    while (angles.size < 4) {
                        val current = client.readOrientationSettings(device)
                        assertTrue(current.automatic)
                        assertEquals(original.manualAngle, current.manualAngle)
                        assertEquals(afterCycle.revision, current.revision)
                        assertEquals("Sensor health or motion qualification failed", 1, current.sensorStatus)
                        assertTrue(current.appliedAngle in 0..3)
                        if (angles.add(current.appliedAngle)) {
                            record("Observed applied angle ${current.appliedAngle * 90}; sensor status ${current.sensorStatus}; settings revision ${current.revision}")
                            checkpoint("AUTO_ORIENTATION_APPLIED_${current.appliedAngle * 90}")
                        }
                        if (angles.size < 4) delay(1_000)
                    }
                }
            }
            assertEquals(setOf(0, 1, 2, 3), angles)
            record("All four applied angles observed from protected live sensor reads. Physical legibility/touch remain owner observations.")
            // Return to ordinary App ownership for the one same-setup restart transaction.
            operation(model::checkGaugeForReview)
            assertEquals(document, model.activeDocument)
            assertTrue(isConfirmedSetup(document.revision, document.sha256, model.runtimeIdentity))
            configurationSendStarted = true
            operation(model::sendNumericConfiguration)
            assertEquals(document.revision + 1, model.activeConfigRevision)
            assertEquals(targetDigest, model.expectedSentDigest)
            assertTrue(isConfirmedSetup(document.revision + 1, targetDigest, model.runtimeIdentity))
            reconcileConfiguration()
            assertTrue("Fresh protected read must prove the exact new configuration", configurationConfirmed)
            assertEquals(document.revision + 1, expectedConfig.revision)
            val restartStartedAt = SystemClock.elapsedRealtime()
            compose.waitUntil(60_000) {
                (model.settingsObservedAtElapsedMs ?: 0) > restartStartedAt && !model.settingsCheckFailed &&
                    model.orientationSettings?.automatic == true && model.orientationSettings?.sensorStatus == 1
            }
            val (restarted, restartedDisplay) = readSettings()
            assertTrue(restarted.automatic)
            assertEquals(1, restarted.sensorStatus)
            assertEquals(original.manualAngle, restarted.manualAngle)
            assertEquals(afterCycle.revision, restarted.revision)
            assertEquals(cycledDisplay, restartedDisplay)
            record("Same functional setup restart confirmed at configuration revision ${expectedConfig.revision}; exact expected digest and cleared trial. Saved Auto/settings revision unchanged; live sensor healthy.")
        } catch (problem: Throwable) {
            failure = problem
            record("Failure: ${problem.javaClass.simpleName}: ${problem.message}")
            throw problem
        } finally {
            try {
                // Restoration is a fresh protected inverse, never a retry of an uncertain Auto write.
                compose.runOnUiThread { model.setConnectionForeground(true) }
                reconcileConfiguration()
                val (current, currentDisplay) = readSettings()
                assertEquals(original.manualAngle, current.manualAngle)
                assertTrue("Unexpected settings revision: leave owner changes alone", current.revision in provenRevision..(provenRevision + if (uncertainWrite) 1 else 0))
                assertEquals("Fresh revision must identify the expected mode", current.revision > original.revision, current.automatic)
                assertTrue(currentDisplay.cycleSeconds in setOf(display.cycleSeconds, alternateCycle))
                assertEquals(display.copy(cycleSeconds = currentDisplay.cycleSeconds, revision = currentDisplay.revision), currentDisplay)
                if (currentDisplay.cycleSeconds != display.cycleSeconds) {
                    assertTrue(current.automatic)
                    operation { model.savePageCycling(display.cycleSeconds, if (display.supportsSmartCycle) display.smartCycle else null) }
                }
                val (beforeManual, beforeManualDisplay) = readSettings()
                val cycleRestored = currentDisplay.cycleSeconds != display.cycleSeconds
                assertEquals(current.revision + if (cycleRestored) 1 else 0, beforeManual.revision)
                assertEquals(current.automatic, beforeManual.automatic)
                assertEquals(original.manualAngle, beforeManual.manualAngle)
                assertEquals(display.copy(revision = beforeManualDisplay.revision), beforeManualDisplay)
                // Explicit manual selection is required even when its angle equals the saved value.
                operation { model.saveOrientation(false, original.manualAngle) }
                val (restored, restoredDisplay) = readSettings()
                assertEquals(beforeManual.revision + if (beforeManual.automatic) 1 else 0, restored.revision)
                assertFalse(restored.automatic)
                assertEquals(original.manualAngle, restored.manualAngle)
                assertEquals(original.manualAngle, restored.appliedAngle)
                assertEquals(display.copy(revision = restoredDisplay.revision), restoredDisplay)
                assertEquals(profiles, model.profileCollection)
                assertEquals(draft, model.draft)
                assertEquals(gauges, model.knownGauges)
                assertEquals(gaugeId, model.rememberedGaugeId)
                reconcileConfiguration()
                assertEquals(functionalJson(document.json), functionalJson(expectedConfig.json))
                restorationConfirmed = true
                record("Restored manual ${restored.manualAngle * 90} degrees; original display preferences; settings revision ${restored.revision}. Functional configuration, profiles and assignments unchanged; configuration revision ${expectedConfig.revision}.")
            } catch (cleanup: Throwable) {
                record("Restoration not confirmed: ${cleanup.message}; no uncertain write was retried.")
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            } finally {
                compose.runOnUiThread { model.setConnectionForeground(true) }
                record("Enable started: $enableStarted; enabled confirmed: $enableConfirmed; observed angles: ${angles.sorted()}; manual restoration confirmed: $restorationConfirmed; same-setup send started: $configurationSendStarted; same-setup confirmed: $configurationConfirmed")
            }
        }
    }

    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }

        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }

        null, JSONObject.NULL -> "null"

        is String -> JSONObject.quote(value)

        else -> value.toString()
    }
}
