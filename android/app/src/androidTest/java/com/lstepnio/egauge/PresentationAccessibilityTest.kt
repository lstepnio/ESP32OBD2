package com.lstepnio.egauge

import android.content.ClipboardManager
import android.content.Context
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
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.car.ClearCodesDialog
import com.lstepnio.egauge.ui.preview.ScreenFixtures
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

    @Test fun fullDetailsAreCopyableWithoutScrollingPastTechnicalFields() {
        val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var previous: android.content.ClipData? = null
        compose.runOnUiThread { previous = clipboard.primaryClip }
        try {
            compose.setContent { EGaugeTheme {
                DetailsSheet("Details", (1..50).map { DetailUi("Example field $it", "Example value $it") }, {})
            } }
            compose.onNodeWithText("Copy details").assertIsDisplayed().performClick()
            compose.onNodeWithText("Copied").assertIsDisplayed()
            compose.waitUntil(5_000) {
                var complete = false
                compose.runOnUiThread {
                    complete = clipboard.primaryClip?.getItemAt(0)?.text?.contains(
                        "Example field 50\nExample value 50") == true
                }
                complete
            }
        } finally {
            compose.runOnUiThread { previous?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip() }
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
}
