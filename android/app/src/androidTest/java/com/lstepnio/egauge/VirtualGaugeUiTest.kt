package com.lstepnio.egauge

import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

/** The normal App path must reach and edit a dashboard without a paired display. */
class VirtualGaugeUiTest {
    @get:Rule val compose = activityTestRule()

    @Test fun opensAndEditsWithoutAHardwareGauge() {
        compose.onNodeWithText("Virtual gauge").performScrollTo().performClick()
        compose.onNodeWithText("Virtual gauge").assertIsDisplayed()
        compose.onNodeWithContentDescription("Engine speed, unavailable.").assertExists()
        compose.onNodeWithText("Customize pages").performScrollTo().performClick()
        compose.onNodeWithText("Done").assertExists()
        compose.onAllNodesWithText("Review and send").assertCountEquals(0)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Virtual gauge").assertIsDisplayed()
    }
}
