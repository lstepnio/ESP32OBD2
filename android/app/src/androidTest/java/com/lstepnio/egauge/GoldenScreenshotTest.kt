package com.lstepnio.egauge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Fixed-density, fixed-size fixtures; never connects to a gauge or queries a vehicle. */
@RunWith(AndroidJUnit4::class)
class GoldenScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private data class Shot(val name: String, val dark: Boolean, val width: Int, val height: Int,
                            val component: ComponentFixture? = null) {
        val id get() = "$name-${if (dark) "dark" else "light"}-$width"
    }

    @Test fun allFixturePixelsMatchReviewedGoldens() {
        val shots = buildList {
            ComponentFixtures.all.forEach { fixture -> listOf(false, true).forEach { dark ->
                add(Shot("component-${fixture.id}", dark, 360, 1400, fixture))
            } }
            ScreenFixtures.names.forEach { name -> listOf(false, true).forEach { dark ->
                listOf(390, 1000).forEach { width -> add(Shot(name, dark, width, if (width == 390) 844 else 800)) }
            } }
        }
        var selected by mutableStateOf(shots.first())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shot = selected
            key(shot.id) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    EGaugeTheme(dark = shot.dark) {
                        Surface(Modifier.requiredSize(shot.width.dp, shot.height.dp).testTag("golden"), color = MaterialTheme.colorScheme.background) {
                            if (shot.component != null) ComponentFixtureGallery(shot.component)
                            else ScreenFixtures.Screen(shot.name)
                        }
                    }
                }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val record = InstrumentationRegistry.getArguments().getString("recordGoldens") == "true"
        val failures = mutableListOf<String>()
        for (shot in shots) {
            compose.runOnIdle { selected = shot }
            // Pager and thumbnail rows complete their first layout on separate frames.
            // Capture after both have settled so an intermediate canvas is never reviewed.
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            val foregroundPackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            assertTrue("Keep the fixture app in the foreground during screenshot checks",
                foregroundPackage in setOf(instrumentation.targetContext.packageName, instrumentation.context.packageName))
            val actual = compose.onNodeWithTag("golden").captureToImage().asAndroidBitmap()
            File(output, "${shot.id}.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!record) {
                val expected = try { instrumentation.context.assets.open("goldens/${shot.id}.png").use { BitmapFactory.decodeStream(it) } }
                catch (_: java.io.FileNotFoundException) { null }
                if (expected == null) { failures += "${shot.id}: missing reviewed golden"; continue }
                if (expected.width != actual.width || expected.height != actual.height) {
                    failures += "${shot.id}: dimensions ${actual.width}×${actual.height}, expected ${expected.width}×${expected.height}"
                    continue
                }
                val count = actual.width * actual.height
                val first = IntArray(count); val second = IntArray(count)
                expected.getPixels(first, 0, actual.width, 0, 0, actual.width, actual.height)
                actual.getPixels(second, 0, actual.width, 0, 0, actual.width, actual.height)
                var changed = 0
                val difference = IntArray(count)
                for (i in 0 until count) {
                    val differs = listOf(0, 8, 16, 24).any { shift ->
                        abs(((first[i] ushr shift) and 255) - ((second[i] ushr shift) and 255)) > 3
                    }
                    if (differs) { changed++; difference[i] = 0xffff00ff.toInt() }
                }
                // A tiny antialias allowance; structural, colour and copy changes still fail.
                if (changed.toDouble() / count > .0005) {
                    failures += "${shot.id}: $changed changed pixels of $count"
                    val diff = Bitmap.createBitmap(difference, actual.width, actual.height, Bitmap.Config.ARGB_8888)
                    File(output, "${shot.id}-diff.png").outputStream().use { diff.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
