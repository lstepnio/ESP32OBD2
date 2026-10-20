package com.lstepnio.egauge

import android.bluetooth.BluetoothManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.ui.state.isConfirmedSetup
import com.lstepnio.egauge.ui.state.sameSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit owner opt-in: replace only this gauge's gesture with unqualified indoor action pages. */
@RunWith(AndroidJUnit4::class)
class ActionPagesConfigurationJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowActionPageSetup") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun configureIndoorPagesWithoutEnablingVehicleCommands() {
        assumeTrue("Requires owner authorization to replace the gauge shortcut", allowed)
        val model = (compose.activity.application as EGaugeApplication).model
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready && model.activeDocument != null &&
                model.displaySettings != null && model.bootIdentity != null &&
                model.vehicleCommands.keys.containsAll(VehicleCommandKind.entries)
        }
        assertEquals(args.getString("expectedFirmwareVersion"), model.bootIdentity?.version)
        assertEquals(2, model.capabilities?.pageActionsVersion)
        assertTrue(model.vehicleCommands.values.none { it.qualified || it.busy })
        val original = requireNotNull(model.activeDocument)
        val originalDraft = requireNotNull(GaugeDraftComparison.savedDraft(original, model.profileCollection.activeId))
        // Optional owner-specified reconciliation of a previously saved gauge filter.
        val restore = args.getString("restoreTcmCode")?.let { DiagnosticCodeFilters.rule("TCM", it) }
        val preservedDraft = if (restore == null) {
            originalDraft
        } else {
            originalDraft.copy(
                diagnosticFilters = (originalDraft.diagnosticFilters + restore).distinct()
            )
        }
        if (restore != null) {
            compose.runOnUiThread { model.addDiagnosticFilter(model.profileCollection.activeId, restore.controller, restore.code) }
            compose.waitUntil(10_000) { restore in model.editorDraft.diagnosticFilters }
        }
        val settings = model.displaySettings
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "action-pages-configuration")
            .apply { mkdirs() }
        File(output, "before.json").writeText(original.json)
        val action = PageAction(type = PageActionType.VehicleActions, airUpSeconds = 300, winchSeconds = 60)
        assertTrue("Unrelated draft changes must be reconciled before sending", sameSettings(preservedDraft, model.transmittedDraft.copy(actions = originalDraft.actions)))
        val otherGauges = model.knownGauges.filter { it.id != model.rememberedGaugeId }
        compose.runOnUiThread { model.saveGaugeActionPages(action, model.editorDraft.idlePresets) }
        compose.waitUntil(10_000) { model.transmittedDraft.actions == listOf(action) }
        fun runOperation(action: () -> Unit) {
            val previous = model.operation.id
            compose.runOnUiThread(action)
            compose.waitUntil(120_000) { model.operation.id > previous && model.operation.terminal }
            assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
        }
        runOperation(model::checkGaugeForReview)
        runOperation(model::sendNumericConfiguration)
        runOperation(model::checkGaugeForReview)
        val after = requireNotNull(model.activeDocument)
        val afterDraft = requireNotNull(GaugeDraftComparison.savedDraft(after, model.profileCollection.activeId))
        assertTrue(isConfirmedSetup(after.revision, after.sha256, model.runtimeIdentity))
        File(output, "after.json").writeText(after.json)
        assertTrue(sameSettings(preservedDraft.copy(actions = listOf(action)), afterDraft))
        assertEquals(settings, model.displaySettings)
        assertEquals(otherGauges, model.knownGauges.filter { it.id != model.rememberedGaugeId })
        // Isolate the final read instead of relying on the background polling cadence.
        compose.runOnUiThread { model.setConnectionForeground(false) }
        try {
            runOperation(model::readRunningFirmware)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val device = context.getSystemService(BluetoothManager::class.java).adapter
                .getRemoteDevice(requireNotNull(model.rememberedGaugeId))
            val client = GaugeConfigTransferClient(context)
            val commands = runBlocking {
                withTimeout(45_000) { VehicleCommandKind.entries.map { client.readVehicleCommand(device, it) } }
            }
            assertTrue(commands.all { it.revision == after.revision && !it.qualified && !it.busy })
        } finally {
            compose.runOnUiThread { model.setConnectionForeground(true) }
        }
        File(output, "result.txt").writeText(
            "Per-gauge action pages saved and confirmed at revision ${after.revision}: Air up 5:00, Winch 1:00, ABS/TCS.\n" +
                "Three swipes up within five seconds opens pages. Original dashboard, limits, bindings, filters and display settings retained.\n" +
                "Both command kinds remain unqualified/inactive. No vehicle command invoked.\n"
        )
    }
}
