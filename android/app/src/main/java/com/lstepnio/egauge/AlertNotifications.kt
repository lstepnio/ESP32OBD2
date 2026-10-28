package com.lstepnio.egauge

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class AlertNotifications(private val context: Context) {
    private val delivered = linkedMapOf<String, Boolean>() // true: filtered, false: notified
    private val postedIds = mutableMapOf<String, MutableSet<Int>>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(
            NotificationChannel(
                "vehicle-critical",
                "Critical alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "vehicle-warning",
                "Vehicle warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "vehicle-monitor",
                "Gauge monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
    fun show(
        event: AlertEvent,
        vehicle: String,
        gauge: String,
        live: Boolean,
        foreground: Boolean,
        filtered: Boolean = false,
        system: MeasurementSystem = MeasurementSystem.Metric,
        codes: List<com.lstepnio.egauge.ui.state.CarFaultUi> = emptyList(),
        reconcileOnly: Boolean = false
    ) {
        val id = event.id(gauge).hashCode()
        if (filtered) {
            val transition = event.transitionId(gauge)
            if (delivered[transition] == true) return
            manager.cancel(id)
            delivered[transition] = true
            while (delivered.size > 256)delivered.remove(delivered.keys.first())
            return
        }
        if (event.lifecycle != AlertLifecycle.Active || event.acknowledged || event.snoozed ||
            event.unavailable
        ) {
            manager.cancel(id)
            return
        }
        val alreadyPosted = manager.activeNotifications.any { it.id == id }
        if (reconcileOnly && !alreadyPosted) return
        if (!reconcileOnly && !shouldNotifyAlert(event, live, foreground)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val transition = event.transitionId(gauge)
        if (!reconcileOnly && (
                transition in delivered ||
                    manager.activeNotifications.any {
                        it.id == id &&
                            it.notification.extras.getString("egauge_transition") == transition
                    }
                )
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).putExtra("alert_id", event.id(gauge))
            .putExtra("alert_vehicle", vehicle).putExtra("alert_gauge", gauge)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(
            context,
            if (event.severity ==
                AlertSeverity.Critical
            ) {
                "vehicle-critical"
            } else {
                "vehicle-warning"
            }
        )
            .setOnlyAlertOnce(reconcileOnly)
            .setSmallIcon(R.drawable.ic_alert_notification)
            .setContentTitle(com.lstepnio.egauge.ui.state.alertTitle(event))
            .setSubText(event.severity.name)
            .setContentText(com.lstepnio.egauge.ui.state.alertNotificationText(event, system, codes))
            .setStyle(Notification.BigTextStyle().bigText(com.lstepnio.egauge.ui.state.alertNotificationText(event, system, codes)))
            .setContentIntent(
                pending
            ).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setExtras(
                android.os.Bundle().apply {
                    putString("egauge_gauge", gauge)
                    putString("egauge_vehicle", vehicle)
                    putString("egauge_transition", transition)
                }
            ).setGroup("vehicle:$vehicle").setCategory(Notification.CATEGORY_STATUS).build()
        postedIds.getOrPut(gauge) { mutableSetOf() }.add(id)
        manager.notify(id, notification)
        delivered[transition] = false
        while (delivered.size >
            256
        ) {
            delivered.remove(delivered.keys.first())
        }
    }

    /** Policy edits reconcile posted content only. Revealing history never posts notifications. */
    fun reconcile(
        vehicle: String,
        entries: List<StoredAlert>,
        rules: List<DiagnosticCodeFilter>,
        system: MeasurementSystem = MeasurementSystem.Metric
    ) {
        manager.activeNotifications.filter {
            it.notification.extras.getString("egauge_vehicle") == vehicle
        }.forEach { posted ->
            val entry = entries.firstOrNull { it.vehicle == vehicle && it.event.id(it.gauge).hashCode() == posted.id }
            val projected = entry?.let { AlertVisibility.project(it, rules) }
            if (projected == null) {
                manager.cancel(posted.id)
                posted.notification.extras.getString("egauge_transition")?.let { delivered[it] = true }
            } else {
                show(
                    projected.event,
                    projected.vehicle,
                    projected.gauge,
                    live = false,
                    foreground = false,
                    system = system,
                    codes = com.lstepnio.egauge.ui.state.recordedFaultCodes(projected.context),
                    reconcileOnly = true
                )
            }
        }
        while (delivered.size > 256) delivered.remove(delivered.keys.first())
    }
    fun cancelGauge(gauge: String) {
        val ids =
            postedIds.remove(gauge).orEmpty() +
                manager.activeNotifications.filter {
                    it.notification.extras.getString("egauge_gauge") ==
                        gauge
                }.map { it.id }
        ids.forEach { manager.cancel(it) }
    }
    fun monitoring(): Notification = Notification.Builder(context, "vehicle-monitor")
        .setSmallIcon(R.drawable.ic_alert_notification).setContentTitle("eGauge monitoring")
        .setContentText("Vehicle alerts while your gauge is connected")
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            Notification.Action.Builder(
                null,
                "Stop",
                PendingIntent.getService(
                    context,
                    0,
                    Intent(context, AlertMonitoringService::class.java).setAction("stop"),
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        )
        .setOngoing(true).build()
}
