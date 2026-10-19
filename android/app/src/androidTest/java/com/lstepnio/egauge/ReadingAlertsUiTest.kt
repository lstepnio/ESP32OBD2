package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.customize.AlertEditor
import com.lstepnio.egauge.ui.customize.AlertManager
import com.lstepnio.egauge.ui.customize.DashboardEditorScreen
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native editor fixtures only. No preferences, adapter requests or gauge configuration writes. */
@RunWith(AndroidJUnit4::class)
class ReadingAlertsUiTest {
    @get:Rule val compose = createComposeRule()
    private val state = ScreenFixtures.customize.copy(readings = readingCatalog.map { readingUi(it) }, alerts = emptyList(), editingEnabled = true)
    @Test fun alertSearchFindsTransmissionAndNewCommonReadings() {
        var selected: String? = null
        compose.setContent { EGaugeTheme { AlertManager(state, {}, { selected = it }, ScreenFixtures.customizeActions) } }
        compose.onNodeWithText("Search readings").performTextInput("transmission")
        compose.onNodeWithText("Transmission temperature").performScrollTo().performClick()
        assertEquals("tcmtemp", selected)
        compose.onNodeWithText("Search readings").performTextClearance()
        compose.onNodeWithText("Search readings").performTextInput("voltage")
        compose.onNodeWithText("Control module voltage").performScrollTo().performClick()
        assertEquals("voltage", selected)
    }
    @Test fun fractionalVoltageLimitsCanBeSavedWithoutInventingAnAlertOnOpen() {
        var saved: GaugeAlertDraft? = null
        compose.setContent { EGaugeTheme { AlertEditor(state, readingUi(readingCatalog.first { it.id == "voltage" }), null, {},
            ScreenFixtures.customizeActions.copy(saveAlert = { saved = it })) } }
        compose.onNodeWithText("Save alert").assertIsNotEnabled()
        compose.onNodeWithText("Falls below").performScrollTo().performClick()
        compose.onNodeWithText("Warning").performTextInput("12.2")
        compose.onNodeWithText("Critical").performTextInput("11.7")
        compose.onNodeWithText("Save alert").performClick()
        compose.runOnIdle {
            assertEquals(12.2, saved!!.warning, 1e-9)
            assertEquals(11.7, saved!!.critical, 1e-9)
            assertEquals(AlertDirection.Below, saved!!.direction)
        }
    }
    @Test fun gearAlertUsesNamedPositionsAndZeroResetDistance() {
        var saved: GaugeAlertDraft? = null
        compose.setContent { EGaugeTheme { AlertEditor(state, readingUi(readingCatalog.first { it.id == "tcmgear" }), null, {},
            ScreenFixtures.customizeActions.copy(saveAlert = { saved = it })) } }
        compose.onNodeWithText("Matches position").assertExists()
        compose.onNodeWithText("Rises above").assertDoesNotExist()
        compose.onAllNodesWithText("R")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("P")[1].performScrollTo().performClick()
        compose.onNodeWithText("Save alert").performClick()
        compose.runOnIdle {
            assertEquals(AlertDirection.Equals, saved!!.direction)
            assertEquals(11.0, saved!!.warning, 0.0)
            assertEquals(13.0, saved!!.critical, 0.0)
            assertEquals(0.0, saved!!.hysteresis, 0.0)
        }
    }
    @Test fun aTransmissionPageOffersAnAlertWithoutChangingExpertTransport() {
        val page = pageUi(GaugePageDraft("page.temp", "TRANS TEMP", GaugeLayout.Numeric, listOf("tcmtemp")))
        compose.setContent { EGaugeTheme { DashboardEditorScreen(state.copy(pages = listOf(page)), 0, {}, {}, ScreenFixtures.customizeActions) } }
        compose.onNodeWithText("Transmission temperature alert").performScrollTo().assertIsDisplayed()
    }
}
