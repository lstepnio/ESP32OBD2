package com.lstepnio.egauge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.customize.AlertManager
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production UI with isolated episodes; no repository, phone settings or vehicle operations. */
@RunWith(AndroidJUnit4::class)
class AlertWorkflowUiTest {
    @get:Rule val compose = createComposeRule()
    private val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 108f, 105f, "Coolant", "degC", 0, 1)
    private fun item(label: String = "Coolant", current: Boolean = true, event: AlertEvent = this.event) = AlertListItem(StoredAlert(label, "vehicle", "gauge", 1000, true, event.copy(label = label), JSONObject()), current, MeasurementSystem.Metric)
    private fun frame(scale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                EGaugeTheme(dark = true) { Surface(Modifier.requiredSize(390.dp, 900.dp).testTag("alerts-frame")) { content() } }
            }
        }
    }
    private fun capture(name: String) {
        val path = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = java.io.File(path, "alerts-workflow/$name.png")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.outputStream().use { check(compose.onNodeWithTag("alerts-frame").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun activeAlertShowsValueAndOneTapActionsWithoutHistoryNoise() {
        var seen = 0
        var snoozed = 0
        var opened = 0
        frame { Column { AlertsCard(listOf(item(), item("Past alert", false)), onOpen = { opened++ }, onSeen = { seen++ }, onSnooze = { snoozed++ }, onHistory = {}, onManage = {}) } }
        compose.onNodeWithText("Past alert").assertDoesNotExist()
        compose.onNodeWithText("108 °C · Your limit: 105 °C", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Mark Coolant seen").performClick()
        compose.onNodeWithContentDescription("Snooze Coolant for 5 minutes").performClick()
        assertEquals(1, seen)
        assertEquals(1, snoozed)
        assertEquals(0, opened)
        capture("active-alert")
    }

    @Test fun staleAndCriticalEpisodesNeverOfferInappropriateActions() {
        frame { Column { AlertsCard(listOf(item(event = event.copy(severity = AlertSeverity.Critical)), item("Last check", false)), onOpen = {}, onSeen = {}, onSnooze = {}, onHistory = {}, onManage = {}) } }
        compose.onNodeWithText("Snooze 5 min").assertDoesNotExist()
        compose.onNodeWithText("Mark seen").assertIsDisplayed()
        compose.onNodeWithText("Last check").assertDoesNotExist()
    }

    @Test fun busyAttentionControlsRemainVisibleButDisabled() {
        frame { Column { AlertsCard(listOf(item().copy(busy = true)), onOpen = {}, onSeen = { error("Busy operation must not send") }, onSnooze = { error("Busy operation must not send") }, onHistory = {}, onManage = {}) } }
        compose.onNodeWithText("Mark seen").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Snooze 5 min").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun allHistoryIsScrollableWithoutAShowAllStepAndDeletionStillConfirms() {
        var deleted = false
        frame { AlertHistoryContent((1..12).map { item("Alert $it", false) }, true, null, {}, {}, { deleted = true }) }
        compose.onNodeWithTag("alert-history-list").performScrollToNode(hasText("Alert 12"))
        compose.onNodeWithText("Alert 12").assertIsDisplayed()
        compose.onNodeWithText("Show all history").assertDoesNotExist()
        compose.onNodeWithContentDescription("History options").performClick()
        compose.onNodeWithText("Delete history").performClick()
        compose.onNodeWithText("Delete history from this phone?").assertIsDisplayed()
        assertFalse(deleted)
        compose.onNodeWithText("Keep history").performClick()
        assertFalse(deleted)
    }

    @Test fun contextStartsWithConditionAndAttentionAndCollapsesSensorEvidence() {
        val entry = item().entry.copy(context = JSONObject("""{"capture":{"available":true,"complete":false,"samples":[]}}"""))
        frame(2f) { Column(Modifier.verticalScroll(rememberScrollState())) { AlertContextContent(AlertListItem(entry, true, MeasurementSystem.Metric), {}, {}, {}) } }
        compose.onNodeWithText("108 °C").assertIsDisplayed()
        compose.onNodeWithText("Your limit: 105 °C").assertIsDisplayed()
        compose.onNodeWithText("Mark seen").assertIsDisplayed()
        compose.onNodeWithText("Some readings are missing.").assertDoesNotExist()
        capture("context-large-text")
        compose.onNodeWithText("Recorded readings").performScrollTo().performClick()
        compose.onNodeWithText("Some readings are missing.").performScrollTo().assertIsDisplayed()
    }

    @Test fun ruleListShowsSavedRulesAndAddsAReadingWithoutOffRows() {
        var selected: String? = null
        var review = false
        frame { AlertManager(ScreenFixtures.customize, {}, { selected = it }, { review = true }) }
        compose.onNodeWithText("Search readings").assertDoesNotExist()
        compose.onNodeWithText("Off").assertDoesNotExist()
        capture("rules")
        compose.onNodeWithText("Add alert").performClick()
        compose.onNodeWithText("Search readings").performTextInput("voltage")
        compose.onNodeWithText("Control module voltage").performScrollTo().performClick()
        assertEquals("voltage", selected)
        compose.onNodeWithText("Review and send").performClick()
        assertTrue(review)
    }
}
