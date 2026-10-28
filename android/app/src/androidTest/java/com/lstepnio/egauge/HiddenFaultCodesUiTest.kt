package com.lstepnio.egauge

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.expert.*
import com.lstepnio.egauge.ui.state.ExpertUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HiddenFaultCodesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exactCodeValidationAndDurablePublicationKeepUnsavedInput() {
        var state by mutableStateOf(
            ExpertUiState(
                false,
                false,
                false,
                emptyList(),
                vehicleName = "Jeep",
                canEditVehicle = true,
                vehicleId = "jeep"
            )
        )
        val requests = mutableListOf<DiagnosticCodeFilter>()
        val actions = ExpertActions({}, {}, {}, {}, addCodeFilter = { _, controller, code -> requests += DiagnosticCodeFilters.rule(controller, code) })
        compose.setContent { EGaugeTheme { HiddenFaultCodesScreen(state, actions, {}, {}) } }
        compose.onNodeWithText("Hide a code").performScrollTo().performClick()
        compose.onNodeWithText("Code").performTextInput("P0700*")
        compose.onNodeWithText("Hide code").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Code").performTextClearance()
        compose.onNodeWithText("Code").performTextInput("P0700")
        compose.closeKeyboardAndWaitForIdle()
        compose.onNodeWithText("Hide code").performScrollTo().performClick()
        compose.onNodeWithText("Hide a fault code").assertExists()
        compose.onNodeWithText("Code").assertTextContains("P0700")
        compose.runOnIdle {
            assertEquals(listOf(DiagnosticCodeFilters.rule("ECM", "P0700")), requests)
            state = state.copy(diagnosticFilters = requests.toList())
        }
        compose.onNodeWithText("Hidden fault codes").assertExists()
        compose.onNodeWithText("P0700").assertIsDisplayed()
        compose.onNodeWithText("Code").assertDoesNotExist()
    }
}
