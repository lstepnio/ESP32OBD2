package com.lstepnio.egauge

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.ui.state.isConfirmedSetup
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit owner opt-in for protected ECM/TCM cache reads only. These reads do
 * not request fresh ECU acquisition. Gauge freshness/availability/age fields
 * remain evidence, including unavailable or stale results. No diagnostic clear,
 * reset, vehicle control, filter edit, or configuration write is invoked.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticReadbackJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowDiagnosticReadback") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun preserveSetupAndReadBothCachedDiagnosticEndpoints() {
        assumeTrue("Requires explicit read-only diagnostic authorization and a paired gauge", allowed)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val model = (compose.activity.application as EGaugeApplication).model
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready && model.activeDocument != null &&
                model.displaySettings != null && model.bootIdentity != null &&
                isConfirmedSetup(
                    model.activeDocument?.revision,
                    model.activeDocument?.sha256,
                    model.runtimeIdentity
                )
        }
        assertEquals(OwnerAccess.AUTHENTICATED, model.ownerAccess)
        val document = requireNotNull(model.activeDocument)
        val display = requireNotNull(model.displaySettings)
        val boot = requireNotNull(model.bootIdentity)
        val runtime = requireNotNull(model.runtimeIdentity)
        val profiles = model.profileCollection
        args.getString("expectedFirmwareVersion")?.let { assertEquals(it, boot.version) }
        args.getString("expectedFirmwareElf")?.let { assertEquals(it, boot.elfSha256) }
        assertEquals(2, boot.otaState)
        val device = context.getSystemService(BluetoothManager::class.java).adapter
            .getRemoteDevice(requireNotNull(model.rememberedGaugeId))
        assertEquals(BluetoothDevice.BOND_BONDED, device.bondState)
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "diagnostic-readback")
            .apply { check(isDirectory || mkdirs()) }
        val snapshots = JSONArray()
        val report = JSONObject()
            .put("schema", 1)
            .put("startedAtUnixMs", System.currentTimeMillis())
            .put("startedAtElapsedMs", SystemClock.elapsedRealtime())
            .put("observationSource", "protected gauge diagnostic cache; no forced ECU refresh")
            .put("configurationFile", "running-config.json")
            .put("configurationRevision", document.revision)
            .put("configurationSha256", document.sha256)
            .put("configurationLength", document.length)
            .put(
                "runtime",
                JSONObject()
                    .put("running", runtime.running)
                    .put("usedPreviousGeneration", runtime.usedPreviousGeneration)
                    .put("trial", runtime.trial)
                    .put("revision", runtime.revision)
                    .put("storedRevision", runtime.storedRevision)
                    .put("sha256", runtime.sha256)
            )
            .put(
                "boot",
                JSONObject()
                    .put("version", boot.version)
                    .put("elfSha256", boot.elfSha256)
                    .put("otaState", boot.otaState)
                    .put("partitionSubtype", boot.partitionSubtype)
                    .put("partitionAddress", boot.partitionAddress)
                    .put("secureVersion", boot.secureVersion)
            )
            .put(
                "display",
                JSONObject()
                    .put("rotation", display.rotation)
                    .put("brightness", display.brightness)
                    .put("revision", display.revision)
                    .put("units", display.units.name)
                    .put("version", display.version)
                    .put("cycleSeconds", display.cycleSeconds)
                    .put("smartCycle", display.smartCycle)
                    .put("smartPolicyVersion", display.smartPolicyVersion)
            )
            .put("endpoints", snapshots)
        File(output, "running-config.json").writeText(document.json)
        fun saveReport() = File(output, "readback.json").writeText(report.toString(2))
        saveReport()
        assertFalse("Stop alert monitoring before taking a direct owner read lease", model.alertMonitoring)
        compose.runOnUiThread { model.setConnectionForeground(false) }
        try {
            val client = GaugeConfigTransferClient(context)
            runBlocking {
                withTimeout(180_000) {
                    for (endpoint in 0..1) {
                        val entry = JSONObject()
                            .put("requestedEndpoint", endpoint)
                            .put("requestedSource", if (endpoint == 0) "ECM" else "TCM")
                            .put("requestedAtUnixMs", System.currentTimeMillis())
                            .put("requestedAtElapsedMs", SystemClock.elapsedRealtime())
                        snapshots.put(entry)
                        saveReport()
                        val diagnostic = withTimeout(40_000) {
                            client.readDiagnosticEndpoint(device, endpoint)
                        }
                        entry.put("completedAtUnixMs", System.currentTimeMillis())
                            .put("completedAtElapsedMs", SystemClock.elapsedRealtime())
                            .put("endpointAvailable", diagnostic != null)
                            .put("diagnostics", diagnostic?.json() ?: JSONObject.NULL)
                        saveReport()
                    }
                    val commands = JSONArray()
                    report.put("vehicleCommandStates", commands)
                    for (kind in VehicleCommandKind.supported(model.capabilities?.vehicleCommandsVersion, model.capabilities?.burnoutActionsVersion)) {
                        val requestedAt = System.currentTimeMillis()
                        val state = withTimeout(20_000) { client.readVehicleCommand(device, kind) }
                        commands.put(
                            JSONObject()
                                .put("requestedAtUnixMs", requestedAt)
                                .put("completedAtUnixMs", System.currentTimeMillis())
                                .put("completedAtElapsedMs", SystemClock.elapsedRealtime())
                                .put("kind", state.kind.name).put("phase", state.phase.name)
                                .put("reason", state.reason).put("operation", state.operation)
                                .put("revision", state.revision).put("link", state.link)
                                .put("boot", state.boot).put("sequence", state.sequence)
                                .put("remainingSeconds", state.remainingSeconds)
                                .put("preset", state.preset?.name ?: JSONObject.NULL)
                                .put("qualified", state.qualified).put("restartRequired", state.restartRequired)
                                .put("lastOperation", state.lastOperation).put("timedFallback", state.timedFallback)
                                .put("gaugeStartRequired", state.gaugeStartRequired)
                                .put("coolantUnavailable", state.coolantUnavailable)
                        )
                        saveReport()
                    }
                    val adapters = JSONArray()
                    report.put("adapterStates", adapters)
                    for (endpoint in 0..1) {
                        val requestedAt = System.currentTimeMillis()
                        val state = withTimeout(20_000) { client.readAdapterStatus(device, endpoint) }
                        adapters.put(
                            JSONObject()
                                .put("requestedEndpoint", endpoint)
                                .put("requestedAtUnixMs", requestedAt)
                                .put("completedAtUnixMs", System.currentTimeMillis())
                                .put("completedAtElapsedMs", SystemClock.elapsedRealtime())
                                .put("phase", state.phase).put("result", state.result)
                                .put("bound", state.bound).put("generation", state.generation)
                                .put("evidenceAgeMs", state.evidenceAgeMs).put("ecu", state.ecu)
                                .put("vehicleId", state.vehicleId).put("sourceId", state.sourceId)
                                .put("simulated", state.simulated).put("uptimeMs", state.uptimeMs)
                                .put(
                                    "supportMaps",
                                    JSONObject().apply {
                                        state.supportMaps.forEach { (mode, bitmap) -> put(mode.toString(), bitmap) }
                                    }
                                )
                        )
                        saveReport()
                    }
                }
            }
            assertTrue("Running configuration JSON changed", document.json == model.activeDocument?.json)
            assertEquals(document.revision, model.activeDocument?.revision)
            assertEquals(document.sha256, model.activeDocument?.sha256)
            assertTrue("Display settings changed", display == model.displaySettings)
            assertTrue("Saved profiles changed", profiles == model.profileCollection)
            report.put("setupUnchanged", true).put("completed", true)
        } catch (failure: Throwable) {
            // Keep identifiers and platform exception text out of test output.
            report.put("completed", false).put("failureClass", failure.javaClass.simpleName)
            throw failure
        } finally {
            report.put("finishedAtUnixMs", System.currentTimeMillis())
            saveReport()
            compose.runOnUiThread { model.setConnectionForeground(true) }
        }
    }

    /** All decoded raw fields, without applying the configured CEL filters. */
    private fun GaugeConfigTransferClient.Diagnostics.json(): JSONObject = JSONObject()
        .put("source", source).put("responder", responder)
        .put("revision", revision ?: JSONObject.NULL).put("session", session ?: JSONObject.NULL)
        .put("connected", connected).put("simulated", simulated)
        .put("milFresh", milFresh).put("milKnown", milKnown).put("milOn", milOn)
        .put("milAvailability", milAvailability.name).put("milAgeMs", milAgeMs ?: JSONObject.NULL)
        .put("reportedCount", reportedCount)
        .put("confirmedFresh", confirmedFresh).put("confirmedCount", confirmedCount)
        .put("confirmedFirst", confirmedFirst ?: JSONObject.NULL)
        .put("pendingFresh", pendingFresh).put("pendingCount", pendingCount)
        .put("pendingFirst", pendingFirst ?: JSONObject.NULL)
        .put("permanentFresh", permanentFresh).put("permanentCount", permanentCount)
        .put("permanentFirst", permanentFirst ?: JSONObject.NULL)
        .put(
            "categories",
            categories?.let { values ->
                JSONArray().apply {
                    values.forEach { category ->
                        put(
                            JSONObject()
                                .put("name", category.name).put("availability", category.availability.name)
                                .put("known", category.known).put("fresh", category.fresh)
                                .put("ageMs", category.ageMs ?: JSONObject.NULL)
                                .put("truncated", category.truncated).put("codes", JSONArray(category.codes))
                        )
                    }
                }
            } ?: JSONObject.NULL
        )
}
