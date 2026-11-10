package com.lstepnio.egauge.virtual

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import com.lstepnio.egauge.closeGattSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private fun assignedUuid(id: String) = UUID.fromString("0000$id-0000-1000-8000-00805f9b34fb")
private val serviceId = assignedUuid("18f0")
private val writeId = assignedUuid("2af1")
private val notifyId = assignedUuid("2af0")
private val cccdId = assignedUuid("2902")
internal val virtualReadLease = Mutex()
private val virtualScanLease = Mutex()

internal val virtualPermissions: Array<String> get() = if (Build.VERSION.SDK_INT >= 31) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

internal fun Context.hasVirtualPermissions(): Boolean = virtualPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

internal data class VirtualCandidate(val address: String, val name: String)

@SuppressLint("MissingPermission")
internal class VirtualAdapter(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    val available: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null
    val enabled: Boolean get() = context.hasVirtualPermissions() && adapter?.isEnabled == true

    suspend fun scan(): List<VirtualCandidate> = virtualScanLease.withLock {
        val scanner = checkNotNull(adapter?.bluetoothLeScanner)
        val candidates = ConcurrentHashMap<String, VirtualCandidate>()
        val result = CompletableDeferred<Unit>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, scan: ScanResult) {
                val address = scan.device.address
                if (candidates.size < 16) {
                    candidates[address] = VirtualCandidate(
                        address,
                        scan.scanRecord?.deviceName?.take(48)?.takeIf { it.isNotBlank() } ?: "OBD-II adapter"
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                result.completeExceptionally(IllegalStateException("Adapter search failed"))
            }
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceId)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback
        )
        try {
            withTimeoutOrNull(4_000) { result.await() }
        } finally {
            scanner.stopScan(callback)
        }
        candidates.values.sortedWith(compareBy(VirtualCandidate::name, VirtualCandidate::address))
    }

    suspend fun read(address: String, source: VirtualSource, action: suspend VirtualElmSession.() -> Unit) {
        val device = checkNotNull(adapter).getRemoteDevice(address)
        val session = VirtualElmSession(source)
        val gatt = checkNotNull(device.connectGatt(context, false, session, BluetoothDevice.TRANSPORT_LE))
        try {
            session.open(gatt)
            session.action()
        } finally {
            session.close(gatt)
        }
    }
}

@SuppressLint("MissingPermission")
internal class VirtualElmSession(private val source: VirtualSource) : BluetoothGattCallback() {
    private enum class Ack { Connected, Services, Subscribed, Written }
    private val events = Channel<Any>(32)
    private val disconnected = CompletableDeferred<Unit>()
    private lateinit var gatt: BluetoothGatt
    private lateinit var tx: BluetoothGattCharacteristic

    private fun offer(value: Any) {
        val result = events.trySend(value)
        if (result.isFailure && !result.isClosed) {
            events.close(IllegalStateException("Adapter response overflow"))
        }
    }

    private fun acknowledge(status: Int, ack: Ack) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            offer(ack)
        } else {
            events.close(IllegalStateException("Adapter connection failed"))
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            disconnected.complete(Unit)
            events.close(IllegalStateException("Adapter disconnected"))
        } else if (newState == BluetoothProfile.STATE_CONNECTED) {
            acknowledge(status, Ack.Connected)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = acknowledge(status, Ack.Services)

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (descriptor.uuid == cccdId && descriptor.characteristic.uuid == notifyId) {
            acknowledge(status, Ack.Subscribed)
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        if (characteristic.uuid == writeId) acknowledge(status, Ack.Written)
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid == notifyId) offer(value.copyOf())
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT < 33) {
            characteristic.value?.let { onCharacteristicChanged(gatt, characteristic, it) }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun open(connection: BluetoothGatt) = withTimeout(15_000) {
        gatt = connection
        check(events.receive() == Ack.Connected)
        check(gatt.discoverServices())
        check(events.receive() == Ack.Services)
        val service = gatt.getService(serviceId) ?: throw UnsupportedVirtualAdapter()
        tx = service.getCharacteristic(writeId) ?: throw UnsupportedVirtualAdapter()
        val rx = service.getCharacteristic(notifyId) ?: throw UnsupportedVirtualAdapter()
        val descriptor = rx.getDescriptor(cccdId) ?: throw UnsupportedVirtualAdapter()
        val canWrite = tx.properties and (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            ) != 0
        if (!canWrite || rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
            throw UnsupportedVirtualAdapter()
        }
        check(gatt.setCharacteristicNotification(rx, true))
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        check(
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, enable) == BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = enable
                gatt.writeDescriptor(descriptor)
            }
        )
        check(events.receive() == Ack.Subscribed)
        // Reset volatile interpreter settings left by a previous client. CAN 11-bit / 500 kbps only.
        val header = if (source == VirtualSource.Engine) "ATSH7DF" else "ATSH7E1"
        for (command in listOf("ATD", "ATE0", "ATL0", "ATS1", "ATH1", "ATCAF1", "ATM0", "ATTP6", header)) {
            if (exchange(command).lineSequence().none { it.trim().equals("OK", ignoreCase = true) }) {
                throw UnsupportedVirtualAdapter()
            }
        }
        if (source == VirtualSource.Transmission &&
            !matchesVirtualTransmission(exchange("0904"), exchange("0906"))
        ) {
            // This may be an ignition-off port. Retry with backoff, without requesting enhanced data.
            throw VirtualTransmissionUnverified()
        }
    }

    suspend fun sample(pid: VirtualPid): Double? {
        require(pid.source == source)
        return decodeVirtualReply(exchange(pid.request), pid)
    }

    suspend fun engineSupport(): VirtualEngineSupport {
        check(source == VirtualSource.Engine)
        return discoverVirtualEngineSupport(::exchange)
    }

    suspend fun waitForDisconnect() {
        disconnected.await()
    }

    @Suppress("DEPRECATION")
    private suspend fun exchange(command: String): String = withTimeout(3_000) {
        val waiting = events.tryReceive()
        waiting.exceptionOrNull()?.let { throw it }
        check(!waiting.isSuccess) { "Unsolicited adapter response" }
        val bytes = "$command\r".encodeToByteArray()
        val writeType = if (tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        check(
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(tx, bytes, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                tx.writeType = writeType
                tx.value = bytes
                gatt.writeCharacteristic(tx)
            }
        )
        val prompt = ElmPrompt()
        var written = false
        var response: String? = null
        while (!written || response == null) {
            when (val event = events.receive()) {
                Ack.Written -> {
                    check(!written)
                    written = true
                }

                is ByteArray -> response = prompt.append(event)

                else -> error("Unexpected adapter callback")
            }
        }
        response
    }

    suspend fun close(connection: BluetoothGatt) {
        events.cancel()
        closeGattSession(connection::disconnect, { disconnected.await() }, connection::close)
    }
}

internal class UnsupportedVirtualAdapter : Exception()

internal class VirtualTransmissionUnverified : Exception()
