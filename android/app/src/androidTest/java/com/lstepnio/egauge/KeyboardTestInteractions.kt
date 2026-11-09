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
        val ime = WindowInsets.Type.ime()
        waitForIdle()
        instrumentation.runOnMainSync {
            // Cancel pending IME requests on the focused dialog, when there is one.
            WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                ?.windowInsetsController?.hide(ime)
        }
        waitUntil(5_000) {
            var hidden = false
            instrumentation.runOnMainSync {
                val roots = WindowInspector.getGlobalWindowViews()
                val visible = roots.filter { it.rootWindowInsets?.isVisible(ime) == true }
                // Synthetic text input can succeed without showing an IME or granting
                // window focus. Only require focus to dismiss an actual visible IME.
                visible.lastOrNull { it.hasWindowFocus() }?.windowInsetsController?.hide(ime)
                hidden = visible.isEmpty() && roots.any { it.rootWindowInsets != null }
            }
            hidden
        }
    } else {
        Espresso.closeSoftKeyboard()
    }
    waitForIdle()
}
