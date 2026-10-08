package com.lstepnio.egauge

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class AlertMonitoringService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val model=(application as EGaugeApplication).model
        if(intent?.action=="stop") { model.setPhoneAlertsPolicy(false);model.updateAlertMonitoring(false);stopSelf();return START_NOT_STICKY }
        val bluetoothPermission=if(android.os.Build.VERSION.SDK_INT>=31)android.Manifest.permission.BLUETOOTH_CONNECT else android.Manifest.permission.ACCESS_FINE_LOCATION
        if(!model.phoneAlertsEnabled || checkSelfPermission(bluetoothPermission)!=android.content.pm.PackageManager.PERMISSION_GRANTED) { model.updateAlertMonitoring(false);stopSelf();return START_NOT_STICKY }
        try { startForeground(7001,AlertNotifications(this).monitoring(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) } catch(_: SecurityException) { model.updateAlertMonitoring(false);stopSelf();return START_NOT_STICKY }
        model.updateAlertMonitoring(true)
        return START_STICKY
    }
    override fun onDestroy() {
        (application as EGaugeApplication).model.updateAlertMonitoring(false)
        super.onDestroy()
    }
}
