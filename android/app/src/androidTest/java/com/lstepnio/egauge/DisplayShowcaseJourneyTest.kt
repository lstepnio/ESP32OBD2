package com.lstepnio.egauge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Owner opt-in. Presentation fixtures never modify the saved vehicle dashboard. */
@RunWith(AndroidJUnit4::class)
class DisplayShowcaseJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowDisplayShowcase") == "true"
    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun temporaryPlaybackPreservesDashboardAndSettings() {
        assumeTrue("Requires owner opt-in for generated gauge screen playback", allowed)
        val minimumFrames = args.getString("showcaseFrames")?.toInt() ?: 2
        require(minimumFrames in 2..21) { "Showcase frames must be between 2 and 21" }
        val model = (compose.activity.application as EGaugeApplication).model
        compose.waitUntil(90_000) { model.connection.phase == ConnectionPhase.Ready &&
            model.activeDocument != null && model.displaySettings != null }
        assertEquals(1, model.capabilities?.displayShowcaseVersion)
        val document = requireNotNull(model.activeDocument)
        val settings = requireNotNull(model.displaySettings)
        val profiles = model.profileCollection.profiles
        val seenFrames = mutableSetOf<Int>()
        compose.runOnUiThread { model.setDisplayShowcase(true) }
        try {
            compose.waitUntil(30_000) { model.displayShowcase?.active == true && model.operation.terminal }
            val first = requireNotNull(model.displayShowcase)
            assertEquals(21, first.count)
            seenFrames += first.frame
            compose.waitUntil(30_000L + (minimumFrames - 2) * 10_000L) {
                model.displayShowcase?.takeIf { it.active }?.let { seenFrames += it.frame }
                seenFrames.size >= minimumFrames
            }
        } finally {
            compose.runOnUiThread { model.setDisplayShowcase(false) }
            compose.waitUntil(30_000) { model.displayShowcase?.active == false && model.operation.terminal }
        }
        assertEquals(document.revision, model.activeDocument?.revision)
        assertEquals(document.sha256, model.activeDocument?.sha256)
        assertEquals(settings, model.displaySettings)
        assertEquals(profiles, model.profileCollection.profiles)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "display-showcase-result.txt").writeText(
            "Physical gauge protocol observed ${seenFrames.size} distinct showcase frames: ${seenFrames.sorted()}\n" +
                "Playback stopped; dashboard revision ${document.revision}, digest, settings and profiles preserved\n" +
                "Protocol observations do not replace owner visual/touch confirmation\n")
        if (args.getString("keepShowcaseRunning") == "true") {
            compose.runOnUiThread { model.setDisplayShowcase(true) }
            compose.waitUntil(30_000) { model.displayShowcase?.active == true && model.operation.terminal }
        }
    }
}
