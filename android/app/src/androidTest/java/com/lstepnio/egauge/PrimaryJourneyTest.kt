package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, no device writes and no vehicle queries. Local pages are only viewed. */
@RunWith(AndroidJUnit4::class)
class PrimaryJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun primaryNavigationKeepsTechnicalTermsOutOfDefaultFlow() {
        compose.onNodeWithText("Gauge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Car", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No car readings yet").assertIsDisplayed()
        assertNoJargon()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Show advanced tools").performScrollTo().assertIsDisplayed()
        assertNoJargon()
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
    }

    @Test fun customizeIsAGuidedFlowWithOnePrimaryAction() {
        compose.onNodeWithText("Customize").performScrollTo().performClick()
        compose.onNodeWithText("Choose readings").assertIsDisplayed()
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        assertNoJargon()
        compose.onNodeWithText("Choose layouts").performScrollTo().performClick()
        compose.onNodeWithText("Set limits").performScrollTo().performClick()
        compose.onNodeWithText("Warn above").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review pages").performScrollTo().performClick()
        compose.onNodeWithText("Review your gauge").assertIsDisplayed()
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        assertNoJargon()
    }

    private fun assertNoJargon() {
        listOf("SHA-256", "revision", "ECM", "TCM", "PID", "Hysteresis", "PHONE DRAFT", "GitHub", "projection", "evidence").forEach { word ->
            compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }
}
