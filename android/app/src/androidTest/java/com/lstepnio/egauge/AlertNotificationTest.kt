package com.lstepnio.egauge

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class AlertNotificationTest {
    @Test fun replayForegroundSimulationAndLossNeverProduceDuplicateVehicleWarnings() {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=context.getSystemService(NotificationManager::class.java)
        fun awaitCount(count: Int) {
            val deadline=android.os.SystemClock.elapsedRealtime()+2000
            while(manager.activeNotifications.size!=count && android.os.SystemClock.elapsedRealtime()<deadline)android.os.SystemClock.sleep(10)
            assertEquals(count,manager.activeNotifications.size)
        }
        manager.cancelAll();awaitCount(0)
        val notifications=AlertNotifications(context)
        val event=AlertEvent(1,9,1,1000,0,AlertSeverity.Critical,false,AlertLifecycle.Active,1,false,120f,115f,"Coolant","degC",1000,1)
        notifications.show(event,"car","gauge",false,false)
        notifications.show(event,"car","gauge",true,true)
        notifications.show(event.copy(simulated=true),"car","gauge",true,false)
        awaitCount(0)
        notifications.show(event,"car","gauge",true,false)
        awaitCount(1)
        assertEquals("car",manager.activeNotifications.single().notification.extras.getString("egauge_vehicle"))
        notifications.show(event.copy(kind=5,acknowledged=true),"car","gauge",true,false)
        awaitCount(0)
        notifications.show(event.copy(sequence=2,kind=2),"car","gauge",true,false)
        awaitCount(1);notifications.cancelGauge("gauge");awaitCount(0)
    }
}
