package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.ui.state.isConfirmedSetup
import com.lstepnio.egauge.ui.state.sameSettings
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in indoor add-only round trip. No injected readings, vehicle commands or firmware writes. */
@RunWith(AndroidJUnit4::class)
class TenPageDisplayJourneyTest {
    private val allowed = InstrumentationRegistry.getArguments().getString("allowGaugeWrite") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun tenAppendedPagesHaveExactReadbackAndRestoreOriginalSetup() {
        assumeTrue("Requires explicit indoor gauge-write authorization", allowed)
        val holdArgument = InstrumentationRegistry.getArguments().getString("visualHoldMs")
        val visualHoldMs = if (holdArgument == null) {
            0L
        } else {
            requireNotNull(holdArgument.toLongOrNull()) {
                "visualHoldMs must be an integer between 0 and 180000"
            }
        }
        require(visualHoldMs in 0L..180_000L) { "visualHoldMs must be between 0 and 180000" }
        val model = (compose.activity.application as EGaugeApplication).model
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ten-page-display").apply { mkdirs() }
        val report = StringBuilder("Ten-page indoor configuration round trip\n")
        fun record(message: String) {
            report.append(message).append('\n')
            File(output, "result.txt").writeText(report.toString())
        }
        fun operation(action: () -> Unit) {
            val before = model.operation.id
            compose.runOnUiThread(action)
            compose.waitUntil(150_000) { model.operation.id > before && model.operation.terminal }
            assertEquals(model.operation.detail, OperationStage.ACTIVE, model.operation.stage)
        }
        fun local(action: () -> Unit) {
            compose.waitUntil(15_000) { !model.localSaveBusy }
            compose.runOnUiThread(action)
            compose.waitUntil(15_000) { !model.localSaveBusy }
            assertNull(model.profileError)
            assertNull(model.gaugeAssociationError)
            assertNull(model.pageEditError)
        }
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(output, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready && model.activeDocument != null &&
                model.runtimeIdentity != null && model.displaySettings != null && !model.localSaveBusy
        }
        operation(model::checkGaugeForReview)
        val original = requireNotNull(model.activeDocument)
        val profiles = model.profileCollection
        val draft = model.draft
        val editor = model.editorDraft
        val transmitted = model.transmittedDraft
        val gauges = model.knownGauges
        val gaugeId = model.rememberedGaugeId
        val settings = requireNotNull(model.displaySettings)
        val selectedGauge = gauges.single { it.id == gaugeId }
        assertEquals(profiles.activeId, selectedGauge.vehicleId)
        assertEquals(draft.source, selectedGauge.source)
        assertEquals("Selected gauge assignment must survive ordinary send", draft.source, transmitted.source)
        val cursor = model.editingPageIndex
        val capabilities = requireNotNull(model.capabilities)
        assertEquals("Requires mp:10", 10, capabilities.maxPages)
        assertTrue("Requires dx:1", GaugeLayout.DualArc in capabilities.supportedRenderers)
        assertTrue(isConfirmedSetup(original.revision, original.sha256, model.runtimeIdentity))
        assertEquals(original.vehicleProfileId, profiles.activeId)
        assertTrue("Requires 1..7 original pages", editor.pages.size in 1..7)
        assertTrue("Reconcile unsent setup changes first", sameSettings(GaugeDraftComparison.savedDraft(original, profiles.activeId), transmitted))
        val matching = editor.pages.indexOfFirst { it.pidIds.first() == draft.pidId && it.layout == draft.layout }
        assertTrue("Original legacy selection must be recoverable without editing original pages", matching >= 0)
        val available = ConfigurationProjector.pagePidIds(editor.source)
        assertTrue(available.containsAll(listOf("rpm", "coolant")))
        val gear = "tcmgear".takeIf { it in available }
        val layouts = listOf(
            GaugeLayout.DualArc, GaugeLayout.DualArc, GaugeLayout.Dual, GaugeLayout.Arc,
            GaugeLayout.Bar, GaugeLayout.Trend, GaugeLayout.Numeric, GaugeLayout.Arc, GaugeLayout.Bar
        ).take(10 - editor.pages.size)
        assertTrue(capabilities.supportedRenderers.containsAll(layouts))
        // Pure storage-normalization proof before any phone mutation.
        val simulatedPages = editor.pages + layouts.mapIndexed { index, layout ->
            GaugePageDraft(
                "page.ten.fixture.$index",
                "Engine speed",
                layout,
                if (layout.isDual) listOf("rpm", if (index == 0) gear ?: "coolant" else "coolant") else listOf("rpm"),
                if (index == 1) 1 else 0
            )
        }
        val simulated = profiles.active.withDashboard(editor.copy(pages = simulatedPages, pidId = "rpm", layout = layouts.last()))
        assertEquals(
            "Storage normalization must be reversible",
            profiles.active,
            simulated.withDashboard(editor.copy(pidId = draft.pidId, layout = draft.layout))
        )
        fun assertLocalRestored() {
            assertEquals(profiles, model.profileCollection)
            assertEquals(draft, model.draft)
            assertEquals(editor, model.editorDraft)
            assertEquals(transmitted, model.transmittedDraft)
            assertEquals(gauges, model.knownGauges)
            assertEquals(gaugeId, model.rememberedGaugeId)
        }
        fun removeAppended() {
            assertEquals(editor.pages, model.editorDraft.pages.take(editor.pages.size))
            compose.runOnUiThread { model.selectPage(matching) }
            while (model.editorDraft.pages.size > editor.pages.size) {
                val count = model.editorDraft.pages.size
                local { model.removePage(count - 1) }
                assertEquals(count - 1, model.editorDraft.pages.size)
            }
            compose.runOnUiThread { model.selectPage(cursor) }
            assertLocalRestored()
        }
        fun append() {
            assertLocalRestored()
            layouts.forEachIndexed { index, layout ->
                val count = model.editorDraft.pages.size
                local { model.addPage("rpm") }
                assertEquals(count + 1, model.editorDraft.pages.size)
                local { model.selectLayout(layout) }
                if (layout.isDual) {
                    val secondary = if (index == 0) gear ?: "coolant" else "coolant"
                    local { model.selectSecondaryPid(demoCatalog.single { it.id == secondary }) }
                    if (layout == GaugeLayout.DualArc) local { model.selectArcMetric(if (index == 1) 1 else 0) }
                }
                val page = model.editorDraft.pages.last()
                assertEquals(layout, page.layout)
                assertEquals(if (layout.isDual) listOf("rpm", if (index == 0) gear ?: "coolant" else "coolant") else listOf("rpm"), page.pidIds)
                assertEquals(if (index == 1) 1 else 0, page.arcMetricIndex)
            }
            assertEquals(10, model.editorDraft.pages.size)
            assertEquals(editor.pages, model.editorDraft.pages.take(editor.pages.size))
            assertEquals(
                transmitted.copy(
                    pages = model.transmittedDraft.pages,
                    pidId = model.transmittedDraft.pidId,
                    layout = model.transmittedDraft.layout
                ),
                model.transmittedDraft
            )
            assertEquals(gauges, model.knownGauges)
        }
        var writeStarted = false
        var confirmed = false
        var restored = false
        var failure: Throwable? = null
        var sentDigest: String? = null
        record("Original revision ${original.revision}; digest ${original.sha256}")
        try {
            try {
                append()
            } finally {
                removeAppended()
            }
            record("Local add/remove dry run preserved complete profiles, drafts and assignments.")
            append()
            val expected = model.transmittedDraft
            compose.onNodeWithText("Gauge", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Customize").performScrollTo().performClick()
            compose.onNodeWithText("Manage pages").performScrollTo().performClick()
            capture("ten-pages-editor")
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Review and send").performClick()
            capture("ten-pages-review")
            operation(model::checkGaugeForReview)
            assertEquals(original, model.activeDocument)
            assertTrue(isConfirmedSetup(original.revision, original.sha256, model.runtimeIdentity))
            val sendStartedAt = android.os.SystemClock.elapsedRealtime()
            writeStarted = true
            operation(model::sendNumericConfiguration)
            assertTrue(isConfirmedSetup(model.activeConfigRevision, model.expectedSentDigest, model.runtimeIdentity))
            confirmed = true
            sentDigest = model.expectedSentDigest
            operation(model::checkGaugeForReview)
            val ten = requireNotNull(model.activeDocument)
            assertEquals(original.revision + 1, ten.revision)
            assertEquals(sentDigest, ten.sha256)
            assertTrue(isConfirmedSetup(ten.revision, ten.sha256, model.runtimeIdentity))
            assertTrue(sameSettings(expected, requireNotNull(GaugeDraftComparison.savedDraft(ten, profiles.activeId))))
            assertEquals(10, JSONObject(ten.json).getJSONArray("pages").length())
            val before = JSONObject(original.json)
            val after = JSONObject(ten.json)
            listOf("sources", "alerts", "actions", "diagnosticFilters").forEach { key ->
                assertEquals("Preserved $key", canonical(before.opt(key)), canonical(after.opt(key)))
            }
            compose.waitUntil(60_000) {
                (model.settingsObservedAtElapsedMs ?: 0) > sendStartedAt && !model.settingsCheckFailed && model.displaySettings != null
            }
            assertEquals(settings, model.displaySettings)
            assertEquals(gauges, model.knownGauges)
            capture("ten-pages-confirmed")
            record("Ten pages confirmed at revision ${ten.revision}, digest ${ten.sha256}; trial cleared.")
            record("Both arc indices and ${if (gear != null) "gear in non-arc slot" else "numeric fallback (gear unsupported)"} read back.")
            record("No App protected quick-selection API: all-index tap/cycling and physical appearance require owner observation. No physical rendering claim.")
            // Emit only after exact healthy readback and fresh settings proof. No device identifiers.
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                android.os.Bundle().apply {
                    putString("checkpoint", "TEN_PAGE_LAYOUT_READY")
                    putString("stream", "TEN_PAGE_LAYOUT_READY\n")
                }
            )
            if (visualHoldMs > 0) {
                record("Owner observation window: $visualHoldMs ms; automatic restoration follows.")
                // Instrumentation thread only. The App main thread and ordinary connection owner stay free.
                android.os.SystemClock.sleep(visualHoldMs)
            }
        } catch (problem: Throwable) {
            failure = problem
            record("Failure: ${problem.javaClass.simpleName}: ${problem.message}")
            throw problem
        } finally {
            try {
                removeAppended()
                if (confirmed) {
                    operation(model::checkGaugeForReview)
                    val current = requireNotNull(model.activeDocument)
                    assertTrue(isConfirmedSetup(current.revision, current.sha256, model.runtimeIdentity))
                    assertEquals(original.revision + 1, current.revision)
                    assertEquals(sentDigest, current.sha256)
                    val restoreStartedAt = android.os.SystemClock.elapsedRealtime()
                    operation(model::sendNumericConfiguration)
                    assertTrue(isConfirmedSetup(model.activeConfigRevision, model.expectedSentDigest, model.runtimeIdentity))
                    operation(model::checkGaugeForReview)
                    val final = requireNotNull(model.activeDocument)
                    assertTrue(isConfirmedSetup(final.revision, final.sha256, model.runtimeIdentity))
                    assertEquals(original.revision + 2, final.revision)
                    assertEquals(
                        canonical(JSONObject(original.json).also { it.remove("baseRevision") }),
                        canonical(JSONObject(final.json).also { it.remove("baseRevision") })
                    )
                    assertLocalRestored()
                    compose.waitUntil(60_000) {
                        (model.settingsObservedAtElapsedMs ?: 0) > restoreStartedAt && !model.settingsCheckFailed && model.displaySettings != null
                    }
                    assertEquals(settings, model.displaySettings)
                    restored = true
                    capture("original-setup-restored")
                    record("Original complete document restored at revision ${final.revision}, digest ${final.sha256}.")
                } else if (writeStarted) {
                    record("Gauge restoration skipped: uncertain send. Local state restored; inspect protected running state before further writes.")
                }
            } catch (cleanup: Throwable) {
                record("Restoration not confirmed: ${cleanup.message}")
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            } finally {
                record("Write started: $writeStarted; temporary confirmed: $confirmed; gauge restored: $restored")
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
