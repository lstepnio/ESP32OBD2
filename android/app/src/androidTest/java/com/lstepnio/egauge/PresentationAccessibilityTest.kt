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
            compose.onNodeWithTag("primary-action").performScrollTo().assertIsDisplayed()
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
}
