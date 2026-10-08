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
    private val delivered=linkedSetOf<String>()
    private val postedIds=mutableMapOf<String,MutableSet<Int>>()
    private val manager=context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel("vehicle-critical","Critical alerts",NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel("vehicle-warning","Vehicle warnings",NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null,null) })
        manager.createNotificationChannel(NotificationChannel("vehicle-monitor","Gauge monitoring",NotificationManager.IMPORTANCE_LOW))
    }
    fun show(event: AlertEvent, vehicle: String, gauge: String, live: Boolean, foreground: Boolean) {
        val id=event.id(gauge).hashCode()
        if(event.lifecycle!=AlertLifecycle.Active || event.acknowledged || event.snoozed || event.unavailable) { manager.cancel(id);return }
        if(!shouldNotifyAlert(event,live,foreground)) return
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val transition=event.transitionId(gauge)
        if(transition in delivered || manager.activeNotifications.any { it.id==id && it.notification.extras.getString("egauge_transition")==transition })return
        val intent=Intent(context,MainActivity::class.java).putExtra("alert_id",event.id(gauge))
            .putExtra("alert_vehicle",vehicle).putExtra("alert_gauge",gauge)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending=PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,if(event.severity==AlertSeverity.Critical)"vehicle-critical" else "vehicle-warning")
            .setSmallIcon(R.drawable.ic_alert_notification).setContentTitle(event.label)
            .setContentText("${event.severity.name} · Tap for context")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setExtras(android.os.Bundle().apply { putString("egauge_gauge",gauge);putString("egauge_vehicle",vehicle);putString("egauge_transition",transition) }).setGroup("vehicle:$vehicle").setCategory(Notification.CATEGORY_STATUS).build()
        postedIds.getOrPut(gauge) { mutableSetOf() }.add(id)
        manager.notify(id,notification)
        delivered.add(transition);while(delivered.size>256)delivered.remove(delivered.first())
    }
    fun cancelGauge(gauge: String) {
        val ids=postedIds.remove(gauge).orEmpty()+manager.activeNotifications.filter { it.notification.extras.getString("egauge_gauge")==gauge }.map { it.id }
        ids.forEach { manager.cancel(it) }
    }
    fun monitoring(): Notification = Notification.Builder(context,"vehicle-monitor")
        .setSmallIcon(R.drawable.ic_alert_notification).setContentTitle("eGauge monitoring")
        .setContentText("Vehicle alerts while your gauge is connected")
        .setContentIntent(PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
        .addAction(Notification.Action.Builder(null,"Stop",PendingIntent.getService(context,0,
            Intent(context,AlertMonitoringService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)).build())
        .setOngoing(true).build()
}
