package com.lstepnio.egauge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.car.ClearCodesDialog
import com.lstepnio.egauge.ui.home.HomeScreen
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import com.lstepnio.egauge.ui.state.HomeAction
import com.lstepnio.egauge.ui.state.HomeUiState
import com.lstepnio.egauge.ui.state.UpdateNotice
import com.lstepnio.egauge.ui.state.pageUi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextKeepsTheSinglePrimaryActionReachableOnEveryScreen() {
        var screen by mutableStateOf(ScreenFixtures.names.first())
        compose.setContent {
            key(screen) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    EGaugeTheme { Surface(Modifier.requiredSize(390.dp, 844.dp)) { ScreenFixtures.Screen(screen) } }
                }
            }
        }
        ScreenFixtures.names.forEach { name ->
            compose.runOnIdle { screen = name }
            compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
            val primary = compose.onNodeWithTag("primary-action")
            if (compose.onAllNodes(hasTestTag("primary-action") and hasAnyAncestor(hasScrollAction()))
                    .fetchSemanticsNodes().isNotEmpty()) primary.performScrollTo()
            primary.assertIsDisplayed()
            val bounds = compose.onNodeWithTag("primary-action").fetchSemanticsNode().boundsInRoot
            assertTrue("$name primary target is too small", bounds.height >= 48 && bounds.width >= 48)
        }
    }

    @Test fun exampleClearingRequiresAnExplicitConsequenceDialog() {
        var clearCount = 0
        compose.setContent { EGaugeTheme { ClearCodesDialog({}, example = true, onExampleClear = { clearCount++ }) } }
        compose.onNodeWithText("Example: clear fault codes?").assertIsDisplayed()
        compose.onNodeWithText("Clearing codes erases diagnostic information", substring = true).assertIsDisplayed()
        assertEquals(0, clearCount)
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Clear example codes").performClick()
        assertEquals(1, clearCount)
    }

    @Test fun everydayScreensHideTechnicalFactsButExpertKeepsThemReachable() {
        var screen by mutableStateOf("gauge")
        compose.setContent { key(screen) { EGaugeTheme { ScreenFixtures.Screen(screen) } } }
        listOf("setup", "gauge", "readings", "review", "car", "updates", "settings").forEach { name ->
            compose.runOnIdle { screen = name }
            compose.onAllNodesWithText("Details").assertCountEquals(0)
            compose.onAllNodesWithText("Example SHA-256").assertCountEquals(0)
        }
        compose.runOnIdle { screen = "expert-data" }
        compose.onNodeWithText("Example SHA-256").assertExists()
    }

    @Test fun holdingTheVisiblePreviewOpensThatPageForEditing() {
        val pages = listOf(
            GaugePageDraft("one", "Engine speed", GaugeLayout.Arc, listOf("rpm")),
            GaugePageDraft("two", "Coolant temperature", GaugeLayout.Numeric, listOf("coolant")),
        ).map(::pageUi)
        var edited = -1
        compose.setContent { EGaugeTheme { Surface(Modifier.requiredSize(390.dp, 844.dp)) {
            HomeScreen(HomeUiState("eGauge", "Gauge ready", true,
                StatusUi("Saved & running on gauge", "Your gauge confirmed these settings.", StatusTone.Success),
                pages, false, "Customize", HomeAction.Customize, false, emptyList()), {}, {}, { edited = it })
        } } }
        compose.onNodeWithText("Swipe between pages · Hold to edit").assertIsDisplayed()
        compose.onNodeWithTag("page-carousel").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Page 2 of 2, Coolant temperature. Swipe to change page. Hold to edit.")
            .performTouchInput { longClick(center) }
        compose.runOnIdle { assertEquals(1, edited) }
    }

    @Test fun routineSuccessStaysInThePillWhileWarningsRemainVisible() {
        val pages = listOf(GaugePageDraft("one", "Engine speed", GaugeLayout.Arc, listOf("rpm"))).map(::pageUi)
        var status by mutableStateOf(StatusUi("Saved & running on gauge", "Confirmed.", StatusTone.Success))
        var notice by mutableStateOf(UpdateNotice.None)
        var openedUpdates = 0
        compose.setContent { EGaugeTheme { Surface(Modifier.requiredSize(390.dp, 844.dp)) {
            HomeScreen(HomeUiState("eGauge", if (notice == UpdateNotice.Ready) "Update ready" else "Gauge ready", true,
                status, pages, false, "Customize", HomeAction.Customize, false, emptyList(), notice),
                {}, {}, onUpdates = { openedUpdates++ })
        } } }
        compose.onNodeWithText("Gauge ready").assertIsDisplayed()
        compose.onAllNodesWithText("Saved & running on gauge").assertCountEquals(0)
        compose.runOnIdle { notice = UpdateNotice.Ready }
        compose.onNodeWithText("Update ready").performClick()
        compose.runOnIdle { assertEquals(1, openedUpdates) }
        compose.runOnIdle {
            status = StatusUi("Update needs attention", "Check your gauge before trying again.", StatusTone.Stale)
            notice = UpdateNotice.NeedsCheck
        }
        compose.onNodeWithText("Update needs attention").assertIsDisplayed()
    }
}
