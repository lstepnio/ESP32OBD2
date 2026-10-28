package com.lstepnio.egauge

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.ui.state.CarFaultUi
import org.json.JSONArray
import org.json.JSONObject
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

    @Test fun savedPolicyReconcilesPostedFaultsWithoutReplayingOrChangingRawEvidence() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        fun awaitPosted(message: String, stableForMs: Long = 0, predicate: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            var satisfiedAt: Long? = null
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (predicate()) {
                    val since = satisfiedAt ?: now.also { satisfiedAt = it }
                    if (now - since >= stableForMs) return
                } else {
                    satisfiedAt = null
                }
                android.os.SystemClock.sleep(10)
            }
            fail(message)
        }
        val unique = java.util.UUID.randomUUID().toString()
        val vehicle = "policy-car:$unique"
        val gauge = "policy-gauge:$unique"
        val notifications = AlertNotifications(context)
        val tcmEvent = AlertEvent(
            101, 91, 81, 1000, 35, AlertSeverity.Warning, false,
            AlertLifecycle.Active, 1, false, 2f, 0f, "Transmission faults", "codes", 1000, 10
        )
        val ecmEvent = tcmEvent.copy(sequence = 102, episode = 82, key = 34, label = "Engine faults", value = 1f)
        fun recorded(source: String, codes: List<String>): JSONObject = JSONObject().put(
            "diagnostics",
            JSONObject().put(
                source,
                JSONObject().put("milOn", true).put("reportedCount", codes.size)
                    .put(
                        "categories",
                        JSONArray().apply {
                            listOf("Stored", "Pending", "Permanent").forEachIndexed { index, name ->
                                put(
                                    JSONObject().put("name", name).put("availability", "Available")
                                        .put("fresh", true).put("truncated", false)
                                        .put("codes", JSONArray(if (index == 0) codes else emptyList<String>()))
                                )
                            }
                        }
                    )
            )
        )
        val tcm = StoredAlert(tcmEvent.id(gauge), vehicle, gauge, 1, true, tcmEvent, recorded("TCM", listOf("P0700", "U0121")))
        val ecm = StoredAlert(ecmEvent.id(gauge), vehicle, gauge, 2, true, ecmEvent, recorded("ECM", listOf("P0700")))
        val rawTcm = tcm.context.toString()
        val rawEcm = ecm.context.toString()
        fun posted(id: String) = manager.activeNotifications.firstOrNull { it.id == id.hashCode() }?.notification
        manager.cancelAll()
        awaitPosted("Old notifications cleared") { manager.activeNotifications.isEmpty() }
        try {
            notifications.show(
                tcm.event,
                vehicle,
                gauge,
                true,
                false,
                codes = com.lstepnio.egauge.ui.state.recordedFaultCodes(tcm.context)
            )
            notifications.show(
                ecm.event,
                vehicle,
                gauge,
                true,
                false,
                codes = com.lstepnio.egauge.ui.state.recordedFaultCodes(ecm.context)
            )
            awaitPosted("Both independent controllers posted") { manager.activeNotifications.size == 2 }
            assertTrue(posted(tcm.id)!!.extras.getString(Notification.EXTRA_TEXT)!!.contains("P0700"))
            val hideOne = listOf(DiagnosticCodeFilters.rule("TCM", "P0700"))
            notifications.reconcile(vehicle, listOf(tcm, ecm), hideOne)
            awaitPosted("Mixed notification rebuilt with visible remainder") {
                val text = posted(tcm.id)?.extras?.getString(Notification.EXTRA_TEXT).orEmpty()
                "U0121" in text && "P0700" !in text
            }
            assertTrue(posted(tcm.id)!!.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertTrue(posted(ecm.id)!!.extras.getString(Notification.EXTRA_TEXT)!!.contains("P0700"))
            val hideAll = hideOne + DiagnosticCodeFilters.rule("TCM", "U0121")
            notifications.reconcile(vehicle, listOf(tcm, ecm), hideAll)
            awaitPosted("Hidden-only notification cancelled; Engine unaffected") {
                posted(tcm.id) == null && posted(ecm.id) != null && manager.activeNotifications.size == 1
            }
            notifications.reconcile(vehicle, listOf(tcm, ecm), emptyList())
            notifications.show(
                tcm.event,
                vehicle,
                gauge,
                true,
                false,
                codes = com.lstepnio.egauge.ui.state.recordedFaultCodes(tcm.context)
            )
            awaitPosted("Revealing old evidence never replays its transition", stableForMs = 1000) {
                posted(tcm.id) == null && manager.activeNotifications.size == 1
            }
            assertEquals(rawTcm, tcm.context.toString())
            assertEquals(rawEcm, ecm.context.toString())
        } finally {
            notifications.cancelGauge(gauge)
            awaitPosted("Fixture notifications cleared") { manager.activeNotifications.isEmpty() }
        }
    }
}
