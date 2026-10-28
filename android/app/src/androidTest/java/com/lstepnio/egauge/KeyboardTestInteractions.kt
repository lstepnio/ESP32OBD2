package com.lstepnio.egauge

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.Espresso

/** IME resizing runs outside Compose's clock and can move the next touch target. */
internal fun ComposeTestRule.closeKeyboardAndWaitForIdle() {
    Espresso.closeSoftKeyboard()
    waitForIdle()
}
