package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.customize.CustomizeActions
import com.lstepnio.egauge.ui.customize.DashboardEditorScreen
import com.lstepnio.egauge.ui.state.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Combined native UI fixtures, no AppModel, Bluetooth or vehicle writes. */
@RunWith(AndroidJUnit4::class)
class UnifiedDashboardUiTest {
    @get:Rule val compose = createComposeRule()
    private val vehicle =
        VehicleProfile(
            "jeep",
            "Jeep",
            Draft(),
            AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"),
            TransmissionConnection(AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"))
        )
    private val dashboard = vehicle.dashboardDraft()
    private fun state(index: Int = 0) = CustomizeUiState(
        dashboard.pages.map { pageUi(it) }, index,
        demoCatalog.filter {
            it.id in ConfigurationProjector.pagePidIds("BOTH")
        }.map { readingUi(it) },
        emptyList(), emptyList(), false, false, false, false, true, false, GaugeLayout.entries.toSet(), emptyList()
    )
    private fun actions(add: (String) -> Unit = {}) = CustomizeActions({}, {}, {}, add, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})

    @Test fun dualArcEditorOffersOnlyNumericArcReadings() {
        val page = GaugePageDraft("page.arc", "TRANSMISSION", GaugeLayout.DualArc, listOf("tcmgear", "tcmtemp"), 1)
        var selected: Int? = null
        compose.setContent {
            EGaugeTheme {
                DashboardEditorScreen(
                    state().copy(pages = listOf(pageUi(page))),
                    0,
                    {},
                    {},
                    actions().copy(selectArc = { selected = it })
                )
            }
        }
        compose.onNodeWithText("Arc reading").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Transmission temperature") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onAllNodes(hasText("Transmission gear") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertCountEquals(0)
    }

    @Test fun oneAddPickerIncludesEngineAndTransmissionReadings() {
        var selected: String? = null
        compose.setContent {
            EGaugeTheme {
                DashboardEditorScreen(
                    state(),
                    0,
                    {},
                    {},
                    actions {
                        selected =
                            it
                    }
                )
            }
        }
        compose.onNodeWithText("Add page").performScrollTo().performClick()
        compose.onNode(
            hasText("Engine speed") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).assertExists()
        compose.onNode(
            hasText("Transmission temperature") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).performScrollTo().assertIsDisplayed()
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "unified-dashboard"
        ).apply {
            mkdirs()
        }
        // The Compose modal is the tested subject. Global accessibility roots can be absent
        // on a managed emulator; capture the unique modal root containing the reading search.
        val pickerRoot = compose.onNode(isRoot() and hasAnyDescendant(hasText("Search readings")), useUnmergedTree = true)
        val bitmap = pickerRoot.assertIsDisplayed().captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        File(output, "combined-reading-picker.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNode(
            hasText("Transmission temperature") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).performClick()
        assertEquals("tcmtemp", selected)
    }
    private fun singleState(profile: VehicleProfile): CustomizeUiState {
        val value = profile.dashboardDraft()
        return CustomizeUiState(
            value.pages.map { pageUi(it) }, 0,
            demoCatalog.filter {
                it.id in ConfigurationProjector.pagePidIds(value.source)
            }.map { readingUi(it) },
            emptyList(), emptyList(), false, false, false, false, true, false, GaugeLayout.entries.toSet(), emptyList()
        )
    }

    @Test fun legacyTcmVehicleCanChooseAnEngineReadingWithoutAChild() {
        val profile = vehicle.copy(draft = TransmissionSetup.draft(), transmission = null)
        compose.setContent {
            EGaugeTheme { DashboardEditorScreen(singleState(profile), 0, {}, {}, actions()) }
        }
        compose.onNodeWithText("Add page").performScrollTo().performClick()
        compose.onNode(
            hasText("Engine speed") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).assertExists()
        compose.onNode(
            hasText("Transmission temperature") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).assertExists()
    }

    @Test fun ordinaryVehicleCanChooseTransmissionReadingsWithoutAddingAnotherAdapter() {
        val profile = vehicle.copy(transmission = null)
        compose.setContent {
            EGaugeTheme { DashboardEditorScreen(singleState(profile), 0, {}, {}, actions()) }
        }
        compose.onNodeWithText("Add page").performScrollTo().performClick()
        compose.onNode(
            hasText("Engine speed") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).assertExists()
        compose.onNode(
            hasText("Transmission temperature") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).assertExists()
    }

    @Test fun secondReadingIncludesBothControllersWithoutAnExpertSourceSwitch() {
        val index = dashboard.pages.indexOfFirst { "tcmgear" in it.pidIds }
        compose.setContent {
            EGaugeTheme { DashboardEditorScreen(state(index), 0, {}, {}, actions()) }
        }
        compose.onNodeWithText("Second reading").performScrollTo().performClick()
        compose.onNode(
            hasText("Transmission temperature") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).assertExists()
        compose.onNode(
            hasText("Engine speed") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        ).assertExists()
    }
}
