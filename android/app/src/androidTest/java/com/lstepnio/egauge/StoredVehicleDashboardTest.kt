package com.lstepnio.egauge

import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.ui.state.presentationState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Read stored phone state only, with automatic discovery disabled. Does not edit or send. */
@RunWith(AndroidJUnit4::class)
class StoredVehicleDashboardTest {
    @get:Rule val compose = activityTestRule()
    @Test fun childReadingsAndEngineReadingsShareTheActualVehicleEditor() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowStoredVehicleRead") == "true")
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        assumeTrue(model.profileCollection.active.transmission != null)
        compose.runOnIdle {
            assertEquals("BOTH", model.editorDraft.source)
            val state = model.presentationState(android.os.SystemClock.elapsedRealtime())
            assertTrue(state.customize.readings.map { it.id }.containsAll(setOf("rpm", "coolant", "tcmtemp", "tcmgear")))
            assertEquals(model.editorDraft.pages, state.car.actionPages)
            assertEquals(model.transmittedDraft.pages.map { it.id }, state.customize.reviewPages.map { it.id })
            assertEquals(model.transmittedDraft.alerts.size, state.customize.reviewAlerts!!.size)
            if (!model.bothAdapters) assertNotNull(state.customize.reviewNotice)
        }
    }
}
