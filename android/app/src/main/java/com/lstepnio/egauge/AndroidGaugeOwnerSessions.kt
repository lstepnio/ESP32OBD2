package com.lstepnio.egauge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/** Owns one lexical authenticated connection; the existing operation coordinator admits its use. */
internal class AndroidGaugeOwnerSessions(private val context: Context) {
    private val serviceId = UUID.fromString("6f1a0000-9e3b-4f45-a714-69c9d23b6c00")
    private val controlId = UUID.fromString("6f1a0002-9e3b-4f45-a714-69c9d23b6c00")
    private val stateId = UUID.fromString("6f1a0003-9e3b-4f45-a714-69c9d23b6c00")
    private sealed interface Event {
        data class Ready(val mtu: Int, val control: BluetoothGattCharacteristic, val state: BluetoothGattCharacteristic) : Event
        data class Read(val bytes: ByteArray, val status: Int) : Event
        data class Write(val status: Int) : Event
        data class Failed(val reason: String) : Event
    }

    @SuppressLint("MissingPermission")
    suspend fun <T> use(
        device: BluetoothDevice,
        operationTimeoutMs: Long = 240_000,
        highThroughput: Boolean = false,
        action: suspend (GaugeOwnerSession) -> T
    ): T {
        val events = Channel<Event>(16)
        val closing = AtomicBoolean(false)
        val disconnected = CompletableDeferred<Unit>()
        fun emit(event: Event) {
            if (!closing.get() && events.trySend(event).isFailure) {
                events.close(GaugeLinkException("Gauge event queue overflowed"))
            }
        }
        var requestedMtu = 23
        val readySent = AtomicBoolean(false)
        val readyPublished = AtomicBoolean(false)
        val discoveryGeneration = java.util.concurrent.atomic.AtomicInteger(0)
        val handshakeHandler = Handler(Looper.getMainLooper())
        val discovery = GattDiscovery { emit(Event.Failed("Could not discover gauge services")) }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) disconnected.complete(Unit)
                if (closing.get()) return
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    emit(Event.Failed("Gauge disconnected during configuration ($status)"))
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    discovery.connect(gatt)
                }
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (closing.get()) return
                if (status == BluetoothGatt.GATT_SUCCESS) requestedMtu = mtu
                discovery.discover(gatt)
            }
            override fun onServiceChanged(gatt: BluetoothGatt) {
                if (closing.get()) return
                if (readyPublished.get()) {
                    emit(Event.Failed("Gauge services changed. Reconnect to reload its settings."))
                } else {
                    discoveryGeneration.incrementAndGet()
                    handshakeHandler.removeCallbacksAndMessages(null)
                    readySent.set(false)
                    discovery.rediscover(gatt)
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (closing.get()) return
                if (!readySent.compareAndSet(false, true)) return
                val service = if (status == BluetoothGatt.GATT_SUCCESS) gatt.getService(serviceId) else null
                val control = service?.getCharacteristic(controlId)
                val state = service?.getCharacteristic(stateId)
                if (control == null || state == null) {
                    emit(Event.Failed("Gauge transfer service is unavailable"))
                } else {
                    // Cache invalidation can follow discovery success. Only publish the latest database.
                    val generation = discoveryGeneration.get()
                    handshakeHandler.postDelayed({
                        if (discoveryGeneration.get() == generation && readyPublished.compareAndSet(false, true)) {
                            emit(Event.Ready(requestedMtu, control, state))
                        }
                    }, 750)
                }
            }

            @Deprecated("Required for Android 10 through 12")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == stateId) {
                    emit(Event.Read(characteristic.value.copyOf(), status))
                }
            }
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (characteristic.uuid == stateId) emit(Event.Read(value.copyOf(), status))
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (characteristic.uuid == controlId) emit(Event.Write(status))
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Could not connect to the gauge")
        return try {
            val ready = withTimeout(20_000) { events.receive() }
            if (ready is Event.Failed) throw GaugeLinkException(ready.reason)
            require(ready is Event.Ready)
            // Recovery OTA exchanges thousands of acknowledged packets. Ask Android
            // for faster connection timing; refusal never blocks the protected path.
            if (highThroughput) gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            withTimeout(operationTimeoutMs) {
                useGaugeOwnerSession(AndroidOwnerChannel(gatt, events, ready.mtu, ready.control, ready.state), action)
            }
        } catch (failure: Exception) {
            if (BuildConfig.DEBUG) Log.w("eGaugeLink", "Protected session failed (${failure.javaClass.simpleName}): ${failure.message}")
            throw failure
        } finally {
            closing.set(true)
            handshakeHandler.removeCallbacksAndMessages(null)
            discovery.close()
            if (highThroughput) gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
            try {
                val confirmed = closeGattSession({ gatt.disconnect() }, { disconnected.await() }, { gatt.close() })
                if (!confirmed && BuildConfig.DEBUG) Log.w("eGaugeLink", "GATT disconnect callback missed cleanup deadline")
            } finally {
                events.close()
            }
        }
    }

    private class AndroidOwnerChannel(
        private val gatt: BluetoothGatt,
        private val events: Channel<Event>,
        override val mtu: Int,
        private val control: BluetoothGattCharacteristic,
        private val state: BluetoothGattCharacteristic
    ) : GaugeOwnerChannel {
        private suspend fun next(): Event = withTimeout(12_000) { events.receive() }.also {
            if (it is Event.Failed) throw GaugeLinkException(it.reason)
        }

        @SuppressLint("MissingPermission")
        override suspend fun read(): OwnerStateRead {
            check(gatt.readCharacteristic(state)) { "Could not request transfer status" }
            val event = next()
            require(event is Event.Read) { "Expected GATT read, received ${event.javaClass.simpleName}" }
            return OwnerStateRead(event.status, event.bytes)
        }

        @SuppressLint("MissingPermission")
        override suspend fun write(bytes: ByteArray) {
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(control, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                control.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(control)
            }
            check(started) { "Could not queue protected gauge command" }
            val write = next()
            if (write !is Event.Write) throw GaugeLinkException("Unexpected gauge response during write")
            if (write.status != BluetoothGatt.GATT_SUCCESS) {
                throw GaugeCommandRejectedException(write.status)
            }
        }
    }
}
