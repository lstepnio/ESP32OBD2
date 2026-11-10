package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.virtual.*
import java.io.File
import org.junit.Rule
import org.junit.Test

class VirtualGaugeFaceUiTest {
    @get:Rule val compose = createComposeRule()

    private fun render(fontScale: Float, live: Boolean) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                EGaugeTheme {
                    Box(Modifier.width(360.dp)) {
                        val pages = defaultGaugePages(layout = GaugeLayout.Arc)
                        val readings = if (live) {
                            mapOf(virtualPidsById.getValue("rpm") to listOf(VirtualSample(675.0, 1_000)))
                        } else {
                            emptyMap()
                        }
                        VirtualGaugeContent(
                            pages, MeasurementSystem.Imperial,
                            rememberPagerState(pageCount = { pages.size }),
                            readings, 1_000,
                            if (live) VirtualStatus.Live else VirtualStatus.Unselected,
                            null, live,
                            {}, {}, {}
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        val args = InstrumentationRegistry.getArguments()
        val path = args.getString("additionalTestOutputDir") ?: return
        File(path, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun compactLiveFaceShowsMeasuredRpm() {
        render(1f, live = true)
        compose.onNodeWithContentDescription("Engine speed, 675 rpm.").assertIsDisplayed()
        compose.onNodeWithText("Live readings").assertIsDisplayed()
        compose.onNodeWithText("Adapters").performScrollTo().assertIsDisplayed()
        capture("virtual-gauge-compact")
    }

    @Test fun largeTextFaceKeepsUnavailableStateAndSetupReachable() {
        render(2f, live = false)
        compose.onNodeWithContentDescription("Engine speed, unavailable.").assertExists()
        compose.onNodeWithText("Set up adapters").performScrollTo().assertIsDisplayed()
        capture("virtual-gauge-large-text")
    }

    @Test fun transmissionSetupRejectsTheAlreadyBoundMainAdapter() {
        var selected: Pair<VirtualSource, String>? = null
        val main = "AA:BB:CC:DD:11:22"
        val child = "AA:BB:CC:DD:33:44"
        compose.setContent {
            EGaugeTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    var picker by remember { mutableStateOf<VirtualSource?>(null) }
                    VirtualAdapterSetup(
                        VirtualBindings(main, null), mapOf(VirtualSource.Engine to VirtualStatus.Live), null,
                        picker, false,
                        listOf(VirtualCandidate(main, "Vgate main"), VirtualCandidate(child, "Vgate child")),
                        MeasurementSystem.Imperial,
                        {}, { picker = it }, { role, address -> selected = role to address },
                        {}, {}, null, {}, {}
                    )
                }
            }
        }
        compose.onNodeWithText("Add transmission adapter").performScrollTo().performClick()
        capture("virtual-transmission-setup-large-text")
        compose.onAllNodesWithText("Vgate main").assertCountEquals(0)
        compose.onNodeWithText("Vgate child").performClick()
        org.junit.Assert.assertEquals(VirtualSource.Transmission to child, selected)
    }
}
