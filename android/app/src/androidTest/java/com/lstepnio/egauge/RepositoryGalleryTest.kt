package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import com.lstepnio.egauge.ui.ConnectionStatusWidget
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import com.lstepnio.egauge.ui.settings.SettingsScreen
import com.lstepnio.egauge.ui.state.*
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** README captures of production composables with fixed fixtures. No radios or saved-state access. */
@RunWith(AndroidJUnit4::class)
class RepositoryGalleryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureRepositoryScreens() {
        var selected by mutableStateOf("gauge")
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
                LocalConnectionStatus provides {
                    ConnectionStatusWidget(
                        "Gauge ready",
                        true,
                        listOf(ConnectionLinkUi("engine", "Car", StatusUi("Car connected", tone = StatusTone.Success))),
                        UpdateNotice.None,
                        {}
                    )
                }
            ) {
                EGaugeTheme(dark = true) {
                    Surface(Modifier.requiredSize(390.dp, 844.dp).testTag("gallery"), color = MaterialTheme.colorScheme.background) {
                        Column {
                            Box(Modifier.weight(1f)) {
                                key(selected) {
                                    if (selected == "settings") {
                                        SettingsScreen(
                                            SettingsUiState(
                                                "eGauge", true, 0, true, false, false, false,
                                                "0.2.0-dev.51", emptyList(), displaySettingsVersion = 3,
                                                brightness = 80, cycleSeconds = 10
                                            ),
                                            {}, {}, {}, {}, { _, _ -> }, {}, {}, {}
                                        )
                                    } else if (selected == "car") {
                                        CarScreen(
                                            CarUiState(
                                                "Jeep Wrangler", listOf(VehicleUi("jeep", "Jeep Wrangler")), "jeep",
                                                StatusUi("Engine warning", tone = StatusTone.Warning), emptyList(), true, false, false, emptyList(),
                                                gaugeKnown = true,
                                                faultSources = listOf(
                                                    FaultSourceUi(
                                                        "Engine",
                                                        StatusUi("Engine warning", tone = StatusTone.Warning),
                                                        listOf(FaultCategoryUi("Stored", "Checked just now", listOf(FaultUi("P0300", "", "Stored"))))
                                                    )
                                                )
                                            ),
                                            {},
                                            {},
                                            {},
                                            currentAlerts = listOf(CarAttentionAlert("coolant", "Coolant above your limit", "248 °F · Limit 230 °F", 3))
                                        )
                                    } else {
                                        ScreenFixtures.Screen(selected, showPreviewLabel = false)
                                    }
                                }
                            }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                                listOf("gauge", "car", "settings").forEach { name ->
                                    val icon = when (name) {
                                        "gauge" -> GaugeIcon.Gauge
                                        "car" -> GaugeIcon.Car
                                        else -> GaugeIcon.Settings
                                    }
                                    NavigationBarItem(
                                        selected = selected == name,
                                        onClick = {},
                                        icon = { EGaugeIcon(icon) },
                                        label = { Text(name.replaceFirstChar(Char::uppercase)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val args = InstrumentationRegistry.getArguments()
        // Gradle Managed Devices pulls this runner-owned directory into test artifacts.
        val output = File(requireNotNull(args.getString("additionalTestOutputDir")), "repository-gallery")
        check(output.mkdirs() || output.isDirectory)
        for (name in listOf("gauge", "car", "settings")) {
            compose.runOnIdle { selected = name }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            File(output, "$name.png").outputStream().use {
                check(
                    compose.onNodeWithTag("gallery").captureToImage().asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                )
            }
        }
    }
}
