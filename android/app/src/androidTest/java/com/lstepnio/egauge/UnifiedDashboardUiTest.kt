package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.customize.CustomizeActions
import com.lstepnio.egauge.ui.customize.DashboardEditorScreen
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Combined native UI fixtures, no AppModel, Bluetooth or vehicle writes. */
@RunWith(AndroidJUnit4::class)
class UnifiedDashboardUiTest {
    @get:Rule val compose = createComposeRule()
    private val vehicle = VehicleProfile("jeep","Jeep",Draft(),AdapterBinding("ecm","AA:BB:CC:DD:EE:01","public"),
        TransmissionConnection(AdapterBinding("tcm","AA:BB:CC:DD:EE:02","public")))
    private val dashboard = vehicle.combinedDraft()
    private fun state(index: Int = 0) = CustomizeUiState(dashboard.pages.map { pageUi(it) },index,
        demoCatalog.filter { it.id in ConfigurationProjector.pagePidIds("BOTH") }.map { readingUi(it) },
        emptyList(),emptyList(),false,false,false,false,true,false,GaugeLayout.entries.toSet(),emptyList())
    private fun actions(add: (String)->Unit = {}) = CustomizeActions({}, {}, {}, add, {}, { _,_-> }, {}, {}, {}, {}, {}, {})
    @Test fun oneAddPickerIncludesEngineAndTransmissionReadings() {
        var selected: String? = null
        compose.setContent { EGaugeTheme { DashboardEditorScreen(state(),0,{}, {},actions { selected=it }) } }
        compose.onNodeWithText("Add page").performScrollTo().performClick()
        compose.onNode(hasText("Engine speed") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertExists()
        compose.onNode(hasText("Transmission temperature") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performScrollTo().assertIsDisplayed()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"unified-dashboard").apply { mkdirs() }
        File(output,"combined-reading-picker.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNode(hasText("Transmission temperature") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        assertEquals("tcmtemp",selected)
    }
    @Test fun secondReadingKeepsCompatibleChoicesWithoutAnExpertSourceSwitch() {
        val index = dashboard.pages.indexOfFirst { "tcmgear" in it.pidIds }
        compose.setContent { EGaugeTheme { DashboardEditorScreen(state(index),0,{}, {},actions()) } }
        compose.onNodeWithText("Second reading").performScrollTo().performClick()
        compose.onNode(hasText("Transmission temperature") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertExists()
        compose.onNode(hasText("Engine speed") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertDoesNotExist()
    }
}
