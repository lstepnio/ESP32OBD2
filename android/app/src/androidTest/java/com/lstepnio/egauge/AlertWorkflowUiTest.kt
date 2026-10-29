package com.lstepnio.egauge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.car.AddAlertReadingPicker
import com.lstepnio.egauge.ui.customize.AlertManager
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production composables and immutable fixtures only; no transport or saved-profile owner. */
@RunWith(AndroidJUnit4::class)
class AlertWorkflowUiTest {
    @get:Rule val compose = createComposeRule()
    private val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 108f, 105f, "Coolant", "degC", 0, 1)
    private fun entry(id: String = "one", event: AlertEvent = this.event) = StoredAlert(id, "car", "gauge", 1000, true, event, JSONObject())
    private fun frame(scale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                EGaugeTheme(dark = true) { Surface(Modifier.requiredSize(360.dp, 760.dp).testTag("alerts-frame")) { content() } }
            }
        }
    }
    private fun capture(name: String) {
        val path = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = java.io.File(path, "alerts-workflow/$name.png")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.outputStream().use { check(compose.onNodeWithTag("alerts-frame").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun currentOffersDirectAttentionWithoutOpeningDetailAtLargeText() {
        var seen = 0
        var snoozed = 0
        var opened = 0
        frame(2f) {
            AlertWorkspaceList(listOf(AlertWorkspaceRow(entry())), false, rememberLazyListState(), Modifier.fillMaxSize(), { true }, { alertAttentionState(it, true, true) }, { opened++ }, { seen++ }, { snoozed++ })
        }
        compose.onNodeWithText("108 °C · Your limit: 105 °C", substring = true).assertExists()
        compose.onNodeWithText("Got it").performScrollTo().performClick()
        compose.onNodeWithText("Snooze 5 min").performScrollTo().performClick()
        assertEquals(1, seen)
        assertEquals(1, snoozed)
        assertEquals(0, opened)
        capture("current-large-text")
    }

    @Test fun busyCurrentActionsAreVisibleAndDisabled() {
        frame { Column { AlertAttentionActions(alertAttentionState(entry(), true, true, busy = true), { error("Busy attention command") }, { error("Busy attention command") }) } }
        compose.onNodeWithText("Got it").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Snooze 5 min").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun criticalOffersSeenButNeverSnooze() {
        frame { Column { AlertAttentionActions(alertAttentionState(entry(event = event.copy(severity = AlertSeverity.Critical)), true, true), {}, { error("Critical snooze") }) } }
        compose.onNodeWithText("Got it").assertIsDisplayed()
        compose.onNodeWithText("Snooze 5 min").assertDoesNotExist()
    }

    @Test fun historyReachesBeyondOldFiveHundredLimitAndHasNoLiveActions() {
        var opened: String? = null
        frame {
            AlertWorkspaceList((1..510).map { AlertWorkspaceRow(entry("$it", event.copy(label = "Alert $it"))) }, true, rememberLazyListState(), Modifier.fillMaxSize(), { false }, { error("History must not request live actions") }, { opened = it.entry.id }, { error("History seen") }, { error("History snooze") })
        }
        compose.onNodeWithTag("alert-history-list").performScrollToNode(hasText("Alert 510"))
        compose.onNodeWithText("Alert 510").assertIsDisplayed().performClick()
        assertEquals("510", opened)
        compose.onNodeWithText("Got it").assertDoesNotExist()
        compose.onNodeWithText("Show all history").assertDoesNotExist()
        capture("history")
    }

    @Test fun configuredRulesKeepDisabledLimitsAndAddOnlyUnconfiguredReadings() {
        var selected: String? = null
        var review = false
        val state = ScreenFixtures.customize.let { it.copy(alerts = it.alerts.map { alert -> alert.copy(enabled = false) }) }
        frame { AlertManager(state, {}, { selected = it }, { review = true }) }
        compose.onNodeWithText("Search readings").assertDoesNotExist()
        compose.onNodeWithText("Off ·", substring = true).assertExists()
        capture("rules")
        compose.onNodeWithText("Add alert").performScrollTo().performClick()
        compose.onNodeWithText("Search readings").performTextInput("voltage")
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Control module voltage").performScrollTo().performClick()
        assertEquals("voltage", selected)
        compose.onNodeWithText("Review and send").performClick()
        assertTrue(review)
    }

    @Test fun carAddPickerExcludesConfiguredDisabledRules() {
        var selected: String? = null
        val state = ScreenFixtures.customize.let { it.copy(alerts = it.alerts.map { alert -> alert.copy(enabled = false) }) }
        val configuredName = state.readings.first { it.id == state.alerts.single().readingId }.name
        frame { AddAlertReadingPicker(state, {}, { selected = it }) }
        compose.onNodeWithText("Search readings").performTextInput(configuredName)
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("No matching readings").assertIsDisplayed()
        compose.onNodeWithText("Search readings").performTextClearance()
        compose.onNodeWithText("Search readings").performTextInput("voltage")
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Control module voltage").performScrollTo().performClick()
        assertEquals("voltage", selected)
    }

    @Test fun currentCompactKeepsConditionValueAndActionsTogether() {
        frame {
            AlertWorkspaceList(listOf(AlertWorkspaceRow(entry())), false, rememberLazyListState(), Modifier.fillMaxSize(), { true }, { alertAttentionState(it, true, true) }, {}, {}, {})
        }
        compose.onNodeWithText("108 °C · Your limit: 105 °C", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Got it").assertIsDisplayed()
        compose.onNodeWithText("Snooze 5 min").assertIsDisplayed()
        capture("current-compact")
    }

    @Test fun ruleManagementPrimaryActionsRemainReachableAtLargeText() {
        var review = false
        frame(2f) { AlertManager(ScreenFixtures.customize, {}, {}, { review = true }) }
        compose.onNodeWithText("Add alert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review and send").assertIsDisplayed()
        capture("rules-large-text")
        compose.onNodeWithText("Review and send").performClick()
        assertTrue(review)
    }
    private fun groupedUnknownHistory(scale: Float) {
        val entries = (1..24).map { n -> entry("warning-$n", event.copy(key = if (n % 2 == 0) 33 else 35)) }
        val rows = alertWorkspaceRows(entries, true) { false }
        var opened: AlertWorkspaceRow? = null
        frame(scale) {
            AlertWorkspaceList(rows, true, rememberLazyListState(), Modifier.fillMaxSize(), { false }, { error("History attention") }, { opened = it }, {}, {})
        }
        compose.onAllNodesWithText("Transmission warning").assertCountEquals(1)
        compose.onNodeWithText("Codes not recorded", substring = true).assertExists()
        compose.onNodeWithText("Transmission warning").performScrollTo().performClick()
        assertEquals(24, opened!!.occurrences.size)
        assertNull(opened!!.fault)
        capture(if (scale > 1f) "grouped-warning-large-text" else "grouped-warning-compact")
    }

    @Test fun repeatedWarningsShowOneHonestHistoryRow() = groupedUnknownHistory(1f)

    @Test fun repeatedWarningsRemainReadableAtLargeText() = groupedUnknownHistory(2f)
}
