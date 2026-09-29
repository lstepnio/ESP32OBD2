package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.customize.DashboardEditorScreen
import com.lstepnio.egauge.ui.preview.ScreenFixtures
import com.lstepnio.egauge.ui.state.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stateful examples only. No ViewModel, preferences, Bluetooth, gauge writes or vehicle requests. */
@RunWith(AndroidJUnit4::class)
class CustomizeJourneyTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(ScreenFixtures.customize)
    private var route by mutableIntStateOf(0)
    private val added = mutableListOf<String>()
    private val removed = mutableListOf<Int>()
    private val saved = mutableListOf<GaugeAlertDraft>()
    private val actions = ScreenFixtures.customizeActions.copy(
        selectPage = { state = state.copy(editingPage = it) },
        layout = { layout ->
            val page = state.pages[state.editingPage]
            val ids = if (layout == GaugeLayout.Dual) listOf(page.readingId, "coolant") else listOf(page.readingId)
            val changed = pageUi(GaugePageDraft(page.id, page.name, layout, ids))
            state = state.copy(pages = state.pages.mapIndexed { index, value -> if (index == state.editingPage) changed else value })
        },
        movePage = { index, delta ->
            val pages = state.pages.toMutableList()
            pages.add(index + delta, pages.removeAt(index))
            state = state.copy(pages = pages, editingPage = index + delta)
        },
        addPage = { id ->
            added += id
            state = state.copy(pages = state.pages + pageUi(GaugePageDraft("new", id, GaugeLayout.Numeric, listOf(id))),
                editingPage = state.pages.size)
        },
        removePage = { index ->
            removed += index
            state = state.copy(pages = state.pages.filterIndexed { i, _ -> i != index }, editingPage = 0)
        },
        saveAlert = { value -> saved += value; state = state.copy(alerts = state.alerts.filterNot { it.readingId == value.pidId } + alertUi(value)) },
        removeAlert = { id -> state = state.copy(alerts = state.alerts.filterNot { it.id == id }) },
    )

    private fun launch() {
        compose.setContent { EGaugeTheme { Surface {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text("Example · UI preview")
                DashboardEditorScreen(state, route, { route = it }, {}, {}, actions)
            }
        } } }
    }

    @Test fun swipeChangesThePageAndKeepsItsControlsTogether() {
        launch()
        compose.onNodeWithTag("page-carousel").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2/3").assertIsDisplayed()
        compose.onNodeWithText("Coolant temperature alert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review and send").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, state.editingPage) }
        compose.onAllNodesWithTag("primary-action").assertCountEquals(1)
        capture("workspace")
    }

    @Test fun addChoosesTheReadingFirstAndRemoveNamesThePage() {
        launch()
        compose.onNodeWithText("Add page").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close choices").performClick()
        assertTrue(added.isEmpty())
        compose.onNodeWithText("Add page").performClick()
        compose.onNodeWithText("Search readings").performTextInput("fuel")
        compose.onNodeWithText("Fuel level").performScrollTo().performClick()
        compose.onNodeWithText("4/4").assertIsDisplayed()
        assertEquals(listOf("fuel"), added)
        compose.onNodeWithText("Manage pages").performScrollTo().performClick()
        capture("pages")
        compose.onNodeWithContentDescription("Options for page 4").performScrollTo().performClick()
        compose.onNodeWithText("Remove page").performClick()
        compose.onNodeWithText("Remove page 4?").assertIsDisplayed()
        assertTrue(removed.isEmpty())
        compose.onNodeWithText("Remove page").performClick()
        assertEquals(listOf(3), removed)
    }

    @Test fun cancellingAndSavingAnRpmAlertHaveDifferentEffects() {
        launch()
        compose.onNodeWithText("Engine speed alert").performScrollTo().performClick()
        compose.onNodeWithText("Save alert").assertIsNotEnabled()
        compose.onNodeWithText("Warning").performTextInput("6000")
        compose.onNodeWithText("Critical").performTextInput("7000")
        compose.onAllNodesWithText("°C").assertCountEquals(0)
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(saved.isEmpty())
        compose.onNodeWithText("Engine speed alert").performScrollTo().performClick()
        compose.onNodeWithText("Save alert").assertIsNotEnabled()
        compose.onNodeWithText("Warning").performTextInput("6000")
        compose.onNodeWithText("Critical").performTextInput("7000")
        compose.onNodeWithText("Save alert").assertIsEnabled().performClick()
        assertEquals(1, saved.size)
        assertEquals("rpm", saved.single().pidId)
        assertEquals(6000, saved.single().warning)
        assertEquals(7000, saved.single().critical)
    }

    @Test fun lowReadingAlertShowsActionableValidationAndPreviewStates() {
        state = state.copy(editingPage = 1)
        launch()
        compose.onNodeWithText("Coolant temperature alert").performScrollTo().performClick()
        compose.onNodeWithText("Falls below").performClick()
        compose.onNodeWithText("Save alert").assertIsNotEnabled()
        compose.onNodeWithText("Set critical lower than warning.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Warning").performTextReplacement("0")
        compose.onNodeWithText("Critical").performTextReplacement("-10")
        // Dismiss the keyboard without leaving the editor.
        compose.onNodeWithText("Critical").performImeAction()
        capture("alert")
        compose.onNodeWithText("Preview alert").performScrollTo().performClick()
        compose.onNodeWithText("Stale").performScrollTo().performClick()
        capture("alert-preview")
        compose.onNodeWithText("Save alert").performClick()
        assertEquals(AlertDirection.Below, saved.single().direction)
        assertEquals(-10, saved.single().critical)
    }

    @Test fun pageLimitAndLastPageRemainProtected() {
        state = state.copy(pages = listOf(state.pages.first()))
        launch()
        compose.onNodeWithText("Manage pages").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Options for page 1").performClick()
        compose.onNodeWithText("Remove page").assertIsNotEnabled()
        compose.onNodeWithText("Move earlier").assertIsNotEnabled()
        compose.onNodeWithText("Move later").assertIsNotEnabled()
    }

    @Test fun choosingDualMakesTheSecondReadingAndBothAlertsReachable() {
        launch()
        compose.onNodeWithText("Layout").performScrollTo().performClick()
        compose.onNodeWithText("Dual").performScrollTo().performClick()
        compose.onNodeWithText("Second reading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Coolant temperature alert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review and send").performClick()
        compose.onNodeWithText("1. Engine speed + Coolant temperature").assertIsDisplayed()
    }

    @Test fun movingAndEditingAPageKeepsTheCounterAndSelectionAligned() {
        launch()
        compose.onNodeWithText("Manage pages").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Options for page 2").performClick()
        compose.onNodeWithText("Move earlier").performClick()
        compose.onNodeWithContentDescription("Edit page 1, Coolant temperature").performClick()
        compose.onNodeWithText("1/3").assertIsDisplayed()
        compose.onNodeWithText("Coolant temperature alert").performScrollTo().assertIsDisplayed()
    }

    @Test fun aSearchWithNoMatchesExplainsHowToContinue() {
        launch()
        compose.onNodeWithText("Reading").performScrollTo().performClick()
        compose.onNodeWithText("Search readings").performTextInput("not a reading")
        compose.onNodeWithText("No matching readings").assertIsDisplayed()
        compose.onNodeWithText("Try a different name or clear your search.").assertIsDisplayed()
    }

    @Test fun busyStateDisablesChangesWithoutHidingTheConfiguration() {
        state = state.copy(busy = true, editingEnabled = false)
        launch()
        compose.onNodeWithText("Reading").assertIsNotEnabled()
        compose.onNodeWithText("Add page").assertIsNotEnabled()
        compose.onNodeWithText("Review and send").assertIsNotEnabled()
    }

    private fun capture(name: String) {
        val variant = InstrumentationRegistry.getArguments().getString("captureScreens") ?: return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "customize-captures/$variant").apply { mkdirs() }
        compose.waitForIdle()
        File(folder, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
