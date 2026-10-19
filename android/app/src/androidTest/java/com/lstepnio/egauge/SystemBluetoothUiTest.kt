package com.lstepnio.egauge

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit operator UI control. Resolve the live node instead of stale scan-row coordinates. */
@RunWith(AndroidJUnit4::class)
class SystemBluetoothUiTest {
    @Test fun clickExactCurrentSettingsControl() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowSystemBluetoothUi") == "true")
        val label = requireNotNull(args.getString("exactLabel"))
        val description = args.getString("matchDescription") == "true"
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var clicked = false
        while (!clicked && SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString() == "com.android.settings") {
                fun matches(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
                    val value = if (description) node.contentDescription else node.text
                    val here = if (value?.toString() == label &&
                        node.isVisibleToUser
                    ) {
                        listOf(node)
                    } else {
                        emptyList()
                    }
                    return here + (0 until node.childCount).flatMap { index ->
                        node.getChild(index)?.let(::matches) ?: emptyList()
                    }
                }
                val match = matches(root).singleOrNull()
                if (match != null && match.refresh() &&
                    (if (description) match.contentDescription else match.text)?.toString() == label
                ) {
                    var current: AccessibilityNodeInfo? = match
                    while (current != null && !current.isClickable) current = current.parent
                    if (current?.refresh() == true && matches(current).size == 1) {
                        clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
            }
            if (!clicked) SystemClock.sleep(100)
        }
        assertTrue("Exact current Bluetooth Settings control unavailable", clicked)
    }
}
