package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.StatusUi
import com.lstepnio.egauge.ui.car.PageActionsSheet
import com.lstepnio.egauge.ui.state.CarUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Simulated native controls only; never creates AppViewModel or opens Bluetooth. */
@RunWith(AndroidJUnit4::class)
class ProfileActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val draft = Draft()
    private fun state(supported: Boolean = true) = CarUiState("Jeep", emptyList(), "jeep",
        StatusUi("Example", "Simulated"), emptyList(), false, false, false, emptyList(),
        actionPages = draft.pages, canEditActions = true, actionsSupported = supported)
    @Test fun defaultsCanBeEditedAndSavedWithoutSendingAnything() {
        var saved: PageAction? = null
        compose.setContent { EGaugeTheme { PageActionsSheet(state(), {}, { saved = it }) } }
        compose.onNodeWithText(draft.pages.first().name).performScrollTo().performClick()
        compose.onNodeWithText("3 swipes up within 5 seconds").performScrollTo().assertIsDisplayed()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"profile-actions").also { it.mkdirs() }
        File(folder,"action-sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithText("More swipes").performScrollTo().performClick()
        compose.onNodeWithText("More time").performScrollTo().performClick()
        compose.onNodeWithText("Save action").performScrollTo().performClick()
        assertEquals(PageAction(draft.pages.first().id,4,6000),saved)
    }
    @Test fun oldFirmwareAllowsLocalDraftAndOffRemovesTheAction() {
        var called = false
        var saved: PageAction? = PageAction(draft.pages.first().id)
        compose.setContent { EGaugeTheme { PageActionsSheet(state(false), {}, { saved=it; called=true }) } }
        compose.onNodeWithText("You can save this choice now. Update the gauge before sending it.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save action").performScrollTo().performClick()
        assertTrue(called); assertNull(saved)
    }
    @Test fun busyProfileCannotSave() {
        compose.setContent { EGaugeTheme { PageActionsSheet(state().copy(canEditActions=false), {}, {}) } }
        compose.onNodeWithText("Save action").performScrollTo().assertIsNotEnabled()
    }
}
