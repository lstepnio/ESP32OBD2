package com.lstepnio.egauge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.AlertDetailContent
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Immutable UI fixtures only, without a phone, gauge or profile-store owner. */
@RunWith(AndroidJUnit4::class)
class AlertDetailUiTest {
    @get:Rule val compose = createComposeRule()

    private val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 108f, 105f, "Coolant temperature", "degC", 0, 1)

    private fun render(entry: StoredAlert, dark: Boolean, fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                EGaugeTheme(dark = dark) {
                    Surface(Modifier.requiredSize(360.dp, 760.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            AlertDetailContent(entry, false, MeasurementSystem.Metric)
                        }
                    }
                }
            }
        }
    }

    @Test fun staleThresholdHasTimestampedAtAlertWithoutCaptureOrPinBoilerplateAtLargeText() {
        val context = JSONObject("""{"coverageGap":true,"capture":{"available":true,"complete":false,"samples":[]}}""")
        render(StoredAlert("one", "car", "gauge", 1000, false, event, context), dark = false, fontScale = 2f)
        compose.onNodeWithText("Current status unavailable. This alert has not been confirmed resolved.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("At alert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Alert limit:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Partial sensor window captured").assertCountEquals(0)
        compose.onAllNodesWithText("Keep report").assertCountEquals(0)
        compose.onAllNodesWithText("Returned to normal", substring = true).assertCountEquals(0)
    }

    @Test fun recordedFaultMeaningIsDirectlyAvailableWithoutNestedCodeSelection() {
        val context = JSONObject("""{"diagnostics":{"TCM":{"categories":[{"name":"Stored","codes":["U0140"]}]}}}""")
        render(StoredAlert("two", "car", "gauge", 1000, false, event.copy(key = 35, unit = "codes"), context), dark = true, fontScale = 1f)
        compose.onNodeWithText("U0140 · Transmission").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What it means").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Back to saved codes").assertCountEquals(0)
    }
}
