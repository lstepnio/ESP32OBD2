package com.lstepnio.egauge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Owner opt-in. Presentation fixtures never modify the saved vehicle dashboard. */
@RunWith(AndroidJUnit4::class)
class DisplayShowcaseJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowDisplayShowcase") == "true"
    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun temporaryPlaybackPreservesDashboardAndSettings() {
        assumeTrue("Requires owner opt-in for generated gauge screen playback", allowed)
        val model = (compose.activity.application as EGaugeApplication).model
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready &&
            model.activeDocument != null && model.displaySettings != null }
        assertEquals(1, model.capabilities?.displayShowcaseVersion)
        val document = requireNotNull(model.activeDocument)
        val settings = requireNotNull(model.displaySettings)
        val profiles = model.profileCollection.profiles
        compose.runOnUiThread { model.setDisplayShowcase(true) }
        compose.waitUntil(30_000) { model.displayShowcase?.active == true && model.operation.terminal }
        val first = requireNotNull(model.displayShowcase)
        assertEquals(21, first.count)
        compose.waitUntil(30_000) { model.displayShowcase?.let { it.active && it.frame != first.frame } == true }
        compose.runOnUiThread { model.setDisplayShowcase(false) }
        compose.waitUntil(30_000) { model.displayShowcase?.active == false && model.operation.terminal }
        assertEquals(document.revision, model.activeDocument?.revision)
        assertEquals(document.sha256, model.activeDocument?.sha256)
        assertEquals(settings, model.displaySettings)
        assertEquals(profiles, model.profileCollection.profiles)
        if (args.getString("keepShowcaseRunning") == "true") {
            compose.runOnUiThread { model.setDisplayShowcase(true) }
            compose.waitUntil(30_000) { model.displayShowcase?.active == true && model.operation.terminal }
        }
    }
}
