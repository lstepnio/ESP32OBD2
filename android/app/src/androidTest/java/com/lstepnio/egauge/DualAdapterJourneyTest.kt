package com.lstepnio.egauge

import androidx.compose.ui.test.junit4.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only, explicitly opted-in bench test. Configure two real adapters before running.
 * Optional interruptSource asks the operator to unplug and reconnect that radio during
 * the bounded waits. This test never sends configuration or update commands. */
@RunWith(AndroidJUnit4::class)
class DualAdapterJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowDualGaugeRead") == "true"
    @get:Rule val compose = activityTestRule(automaticConnection = allowed)
    @Test fun independentSourcesResumeAndRecover() {
        assumeTrue("Requires an explicitly prepared dual-adapter bench", allowed)
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready }
        assertTrue("Enable both adapters for this gauge before running", model.bothAdapters)
        assertEquals(1, model.capabilities?.dualAdapterVersion)
        val original = requireNotNull(model.activeDocument)
        fun healthy(after: Long) {
            compose.waitUntil(90_000) { listOf("ECM", "TCM").all { source ->
                val status = model.adapterStatuses[source]
                val diagnostics = model.diagnosticsBySource[source]
                status?.phase == 4 && status.bound && !status.simulated && status.vehicleId == original.vehicleProfileId &&
                    diagnostics?.source == source && diagnostics.connected && !diagnostics.simulated &&
                    diagnostics.revision == original.revision && (model.diagnosticsCheckedAt[source] ?: 0) > after
            } }
        }
        healthy(0)
        val sessions = model.diagnosticsBySource.mapValues { it.value.session }
        val beforeResume = android.os.SystemClock.elapsedRealtime()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        healthy(beforeResume)
        assertEquals(sessions, model.diagnosticsBySource.mapValues { it.value.session })
        val interrupted = args.getString("interruptSource")
        if (interrupted != null) {
            require(interrupted in setOf("ECM", "TCM"))
            val other = if (interrupted == "TCM") "ECM" else "TCM"
            compose.waitUntil(120_000) { model.adapterStatuses[interrupted]?.phase != 4 && model.adapterStatuses[interrupted] != null }
            val lostAt = android.os.SystemClock.elapsedRealtime()
            compose.waitUntil(60_000) { (model.diagnosticsCheckedAt[other] ?: 0) > lostAt && model.diagnosticsBySource[other]?.connected == true }
            assertEquals(sessions[other], model.diagnosticsBySource[other]?.session)
            healthy(lostAt)
            assertEquals(sessions[other], model.diagnosticsBySource[other]?.session)
            assertNotEquals(sessions[interrupted], model.diagnosticsBySource[interrupted]?.session)
        }
        assertEquals(original.revision, model.activeDocument?.revision)
        assertEquals(original.sha256, model.activeDocument?.sha256)
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "dual-adapter-recovery").apply { mkdirs() }
        File(output, "result.txt").writeText("Physical dual-source reads and resume passed; interruption=${interrupted ?: "none"}; configuration unchanged.\n")
    }
}
