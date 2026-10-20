package com.lstepnio.egauge

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.ui.state.CarFaultUi
import org.junit.Assert.*
import org.junit.Test

class AlertNotificationTest {
    @Test fun replayForegroundSimulationAndLossNeverProduceDuplicateVehicleWarnings() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // Posting is intentionally dropped while permission is absent, so a
            // fire-and-forget shell grant can race and lose the fixture's event.
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
            assertEquals(
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        fun awaitCount(count: Int) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (manager.activeNotifications.size != count &&
                android.os.SystemClock.elapsedRealtime() < deadline
            ) {
                android.os.SystemClock.sleep(10)
            }
            assertEquals(count, manager.activeNotifications.size)
        }
        manager.cancelAll()
        awaitCount(0)
        val notifications = AlertNotifications(context)
        val event =
            AlertEvent(1, 9, 1, 1000, 0, AlertSeverity.Critical, false, AlertLifecycle.Active, 1, false, 120f, 115f, "Coolant", "degC", 1000, 1)
        notifications.show(event, "car", "gauge", false, false)
        notifications.show(event, "car", "gauge", true, true)
        notifications.show(event.copy(simulated = true), "car", "gauge", true, false)
        awaitCount(0)
        notifications.show(event, "car", "gauge", true, false)
        awaitCount(1)
        assertEquals(
            "car",
            manager.activeNotifications.single().notification.extras.getString("egauge_vehicle")
        )
        notifications.show(event.copy(kind = 5, acknowledged = true), "car", "gauge", true, false)
        awaitCount(0)
        notifications.show(event.copy(sequence = 2, kind = 2), "car", "gauge", true, false)
        awaitCount(1)
        notifications.cancelGauge("gauge")
        awaitCount(0)
        val diagnostic = event.copy(sequence = 3, episode = 2, key = 35, severity = AlertSeverity.Warning, label = "TCM faults", unit = "codes")
        notifications.show(diagnostic, "car", "gauge", true, false, codes = listOf(CarFaultUi("U0121", "", "Transmission", "Stored")))
        awaitCount(1)
        val posted = manager.activeNotifications.single().notification
        assertEquals("Transmission fault codes", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("U0121 · ABS communication lost", posted.extras.getString(Notification.EXTRA_TEXT))
        notifications.show(diagnostic, "car", "gauge", true, false, filtered = true)
        awaitCount(0)
    }
}
