package com.lstepnio.egauge

import android.os.Build
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry

/** IME resizing runs outside Compose's clock and can move the next touch target. */
internal fun ComposeTestRule.closeKeyboardAndWaitForIdle() {
    if (Build.VERSION.SDK_INT >= 30) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var focused: android.view.View? = null
        waitUntil(5_000) {
            instrumentation.runOnMainSync {
                focused = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            }
            focused != null
        }
        val root = requireNotNull(focused)
        // Espresso can select the unfocused Activity behind a Compose dialog.
        // Address the actual focused window without a Back press that could dismiss it.
        instrumentation.runOnMainSync { root.windowInsetsController?.hide(WindowInsets.Type.ime()) }
        waitUntil(5_000) {
            var hidden = false
            instrumentation.runOnMainSync { hidden = root.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false }
            hidden
        }
    } else {
        Espresso.closeSoftKeyboard()
    }
    waitForIdle()
}
