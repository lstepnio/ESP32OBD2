package com.lstepnio.egauge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/** MTU is an optimization, not a prerequisite for discovering a reconnected gauge. */
@SuppressLint("MissingPermission")
internal class GattDiscovery(private val failed: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun connect(gatt: BluetoothGatt) {
        handler.postDelayed({ discover(gatt) }, 2_000)
        if (!gatt.requestMtu(185)) discover(gatt)
    }

    fun discover(gatt: BluetoothGatt) {
        if (!closed.get() && started.compareAndSet(false, true) && !gatt.discoverServices()) failed()
    }

    fun rediscover(gatt: BluetoothGatt) {
        handler.removeCallbacksAndMessages(null)
        started.set(false)
        discover(gatt)
    }

    fun close() {
        closed.set(true)
        handler.removeCallbacksAndMessages(null)
    }
}
