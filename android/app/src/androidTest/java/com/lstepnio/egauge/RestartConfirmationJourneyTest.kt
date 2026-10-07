package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import com.lstepnio.egauge.ui.state.isConfirmedSetup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in bench write: resend the already confirmed exact setup, then observe the same warm app. */
@RunWith(AndroidJUnit4::class)
class RestartConfirmationJourneyTest {
    private val allowed = InstrumentationRegistry.getArguments().getString("allowGaugeWrite") == "true"
    @get:Rule val compose = activityTestRule(automaticConnection = allowed)
    @Test fun setupRestartConfirmsInTheSameAppWithoutRefreshOrAnotherWrite() {
        assumeTrue("Requires explicit bench write authorization", allowed)
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready && model.activeDocument != null && model.runtimeIdentity != null }
        val original = requireNotNull(model.activeDocument)
        assertTrue(isConfirmedSetup(original.revision,original.sha256,model.runtimeIdentity))
        assertEquals(original.vehicleProfileId,model.profileCollection.activeId)
        assertNotNull(model.sentDraft)
        val started = android.os.SystemClock.elapsedRealtime()
        val previous = model.operation.id
        compose.runOnUiThread { model.sendNumericConfiguration() }
        compose.waitUntil(150_000) { model.operation.id > previous && model.operation.terminal }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"restart-confirmation").apply { mkdirs() }
        File(output,"result.txt").writeText("Warm-app configuration confirmation\nStage: ${model.operation.stage}\nDetail: ${model.operation.detail}\nElapsed: ${android.os.SystemClock.elapsedRealtime()-started} ms\nRevision: ${model.activeConfigRevision}\nDigest: ${model.expectedSentDigest}\n")
        assertEquals(model.operation.detail,OperationStage.ACTIVE,model.operation.stage)
        assertEquals(original.revision+1,model.activeConfigRevision)
        assertTrue(isConfirmedSetup(model.activeConfigRevision,model.expectedSentDigest,model.runtimeIdentity))
        assertFalse(model.configurationNeedsReview)
        compose.waitUntil(30_000) { model.activeDocument?.revision == original.revision+1 }
        val saved = org.json.JSONObject(requireNotNull(model.activeDocument).json).also { it.remove("baseRevision") }
        val before = org.json.JSONObject(original.json).also { it.remove("baseRevision") }
        assertEquals(before.toString(),saved.toString())
        File(output,"verified-active.json").writeText(requireNotNull(model.activeDocument).json)
    }
}
