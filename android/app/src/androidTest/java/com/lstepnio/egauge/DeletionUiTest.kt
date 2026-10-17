package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.customize.CustomizeActions
import com.lstepnio.egauge.ui.customize.DashboardEditorScreen
import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI callbacks only. Never removes phone profiles or sends to a gauge. */
@RunWith(AndroidJUnit4::class)
class DeletionUiTest {
    @get:Rule val compose = createComposeRule()
    private fun customize(pages: List<GaugePageDraft>) = CustomizeUiState(pages.map { pageUi(it) }, 0,
        demoCatalog.filter { it.id in ConfigurationProjector.supportedPidIds }.map { readingUi(it) },
        emptyList(), emptyList(), false, false, false, false, true, false, GaugeLayout.entries.toSet(), emptyList())
    private fun actions(remove: (Int) -> Unit) = CustomizeActions({}, {}, {}, {}, remove, { _, _ -> }, {}, {}, {}, {}, {}, {})
    @Test fun visiblePageDeleteCanBeCancelledAndConfirmed() {
        var deleted: Int? = null
        compose.setContent { EGaugeTheme { DashboardEditorScreen(customize(Draft().pages), 0, {}, {}, actions { deleted = it }) } }
        compose.onNodeWithText("Delete page").performScrollTo().performClick()
        compose.onNodeWithText("Keep page").performClick()
        assertNull(deleted)
        compose.onNodeWithText("Delete page").performScrollTo().performClick()
        compose.onAllNodesWithText("Delete page").onLast().performClick()
        assertEquals(0, deleted)
    }
    @Test fun lastPageDeleteIsDisabled() {
        compose.setContent { EGaugeTheme { DashboardEditorScreen(customize(Draft().pages.take(1)), 0, {}, {}, actions {}) } }
        compose.onNodeWithText("Delete page").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Keep at least one page.").assertExists()
    }
    @Test fun vehicleDeleteDoesNotSelectTheVehicleAndNeedsConfirmation() {
        var deleted: String? = null
        var selected: String? = null
        val state = CarUiState("Jeep", listOf(VehicleUi("jeep", "Jeep"), VehicleUi("bench", "Bench")), "jeep",
            StatusUi("Waiting", ""), emptyList(), false, false, false, emptyList())
        compose.setContent { EGaugeTheme { CarScreen(state, {}, { selected = it }, {}, onDeleteProfile = { deleted = it }) } }
        compose.onNodeWithText("Your car").performScrollTo().performClick()
        compose.onAllNodesWithText("Delete").onFirst().performClick()
        compose.onNodeWithText("Keep car").performClick()
        assertNull(deleted)
        assertNull(selected)
        compose.onAllNodesWithText("Delete").onFirst().performClick()
        compose.onNodeWithText("Delete car").performClick()
        assertEquals("jeep", deleted)
        assertNull(selected)
    }
}
