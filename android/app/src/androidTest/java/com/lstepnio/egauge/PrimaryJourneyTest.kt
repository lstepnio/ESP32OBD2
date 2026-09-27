package com.lstepnio.egauge

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrimaryJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryNavigationKeepsAdvancedTopologyOutOfDefaultFlow() {
        compose.onNodeWithText("Gauge").assertIsDisplayed()
        compose.onNodeWithText("Readings").assertIsDisplayed()
        compose.onNodeWithText("Vehicle").performClick()
        compose.onNodeWithText("Find nearby gauge").assertIsDisplayed()
        compose.onAllNodes(hasText("TCM", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Gauge connection and maintenance.").assertIsDisplayed()
    }
}
