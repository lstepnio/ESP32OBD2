package com.lstepnio.egauge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

/** Protocol-0 public discovery and bounded paired reading selection. */
class BleCapabilityClient(private val context: Context) {
    private val serviceId = UUID.fromString("6f1a0000-9e3b-4f45-a714-69c9d23b6c00")
    private val capabilityId = UUID.fromString("6f1a0001-9e3b-4f45-a714-69c9d23b6c00")
    private val controlId = UUID.fromString("6f1a0002-9e3b-4f45-a714-69c9d23b6c00")
    private val stateId = UUID.fromString("6f1a0003-9e3b-4f45-a714-69c9d23b6c00")
    private var selectedDevice: BluetoothDevice? = null
    fun selectedGauge(): BluetoothDevice = selectedDevice ?: error("Read gauge capabilities first")

    /** Reads the persisted protocol-0 selection after owner authentication. */
    @SuppressLint("MissingPermission")
    suspend fun readSavedSnapshot(): GaugeSavedSnapshot {
        val device = selectedDevice ?: error("Read this gauge's capabilities first")
        val result = CompletableDeferred<ByteArray>()
        val handler = Handler(Looper.getMainLooper())
        var snapshot: BluetoothGattCharacteristic? = null
        var bondStarted = false
        var sawBonding = false
        var authenticatedRetries = 0
        val callback = object : BluetoothGattCallback() {
            private fun fail(message: String) {
                if (!result.isCompleted) result.completeExceptionally(IllegalStateException(message))
            }
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED)
                    fail("Gauge disconnected during saved state read ($status)")
                else if (newState == BluetoothProfile.STATE_CONNECTED && !gatt.requestMtu(185))
                    discover(gatt)
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { discover(gatt) }
            private fun discover(gatt: BluetoothGatt) {
                if (!gatt.discoverServices()) fail("Could not discover gauge snapshot")
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                snapshot = if (status == BluetoothGatt.GATT_SUCCESS)
                    gatt.getService(serviceId)?.getCharacteristic(stateId) else null
                if (snapshot == null || !gatt.readCharacteristic(snapshot))
                    fail("Gauge secure state read is unavailable")
            }
            @Deprecated("Required for Android 10 through 12")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < 33) onRead(gatt, characteristic.uuid, characteristic.value, status)
            }
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                                              value: ByteArray, status: Int) {
                onRead(gatt, characteristic.uuid, value, status)
            }
            private fun onRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
                if (result.isCompleted || uuid != stateId) return
                if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                    status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) { awaitBond(gatt); return }
                if (status == BluetoothGatt.GATT_SUCCESS) result.complete(value.copyOf())
                else fail("Saved gauge state read failed ($status)")
            }
            private fun awaitBond(gatt: BluetoothGatt) {
                if (result.isCompleted) return
                when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        if (++authenticatedRetries > 3)
                            return fail("This phone is bonded but not authorized as the gauge owner")
                        if (!gatt.readCharacteristic(snapshot)) fail("Could not retry saved state read")
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        sawBonding = true
                        handler.postDelayed({ awaitBond(gatt) }, 250)
                    }
                    else -> {
                        if (sawBonding) return fail("Android pairing was rejected or cancelled")
                        if (!bondStarted) {
                            bondStarted = true
                            if (!device.createBond()) return fail("Android could not start gauge pairing")
                        }
                        handler.postDelayed({ awaitBond(gatt) }, 250)
                    }
                }
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Could not connect to the gauge")
        return try {
            val bytes = withTimeout(60_000) { result.await() }
            if (bytes.size != 8 || bytes[0].toInt() != 2) error("Gauge does not offer durable saved state")
            val index = bytes[2].toInt() and 0xff
            val rotation = bytes[3].toInt() and 0xff
            if (index !in 0..4 || rotation !in 0..3) error("Invalid saved gauge state")
            val revision = (4..7).fold(0L) { acc, offset ->
                acc or ((bytes[offset].toLong() and 0xff) shl ((offset - 4) * 8))
            }
            GaugeSavedSnapshot(index, rotation, revision)
        } finally {
            result.cancel()
            handler.removeCallbacksAndMessages(null)
            gatt.disconnect()
            gatt.close()
        }
    }

    /** Protocol-0 owner controls. An ATT write is followed by durable state readback. */
    suspend fun selectNearby(index: Int): Int {
        require(index in 0..4)
        return controlNearby(1, index).readingIndex
    }

    suspend fun rotateNearby(rotation: Int): GaugeSavedSnapshot {
        require(rotation in 0..3)
        return controlNearby(2, rotation)
    }

    @SuppressLint("MissingPermission")
    private suspend fun controlNearby(opcode: Int, value: Int): GaugeSavedSnapshot {
        val device = selectedDevice ?: error("Read this gauge's capabilities before controlling it")
        val result = CompletableDeferred<GaugeSavedSnapshot>()
        val handler = Handler(Looper.getMainLooper())
        var stateCharacteristic: BluetoothGattCharacteristic? = null
        var controlCharacteristic: BluetoothGattCharacteristic? = null
        var writeSent = false
        var baseRevision = 0L
        var readAttempts = 0
        val target = value
        var bondStarted = false
        var sawBonding = false
        val callback = object : BluetoothGattCallback() {
            private fun fail(message: String) {
                if (!result.isCompleted) result.completeExceptionally(IllegalStateException(message))
            }
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED)
                    fail("Gauge disconnected during selection ($status)")
                else if (newState == BluetoothProfile.STATE_CONNECTED && !gatt.requestMtu(185))
                    discover(gatt)
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { discover(gatt) }
            private fun discover(gatt: BluetoothGatt) {
                if (!gatt.discoverServices()) fail("Could not discover gauge control service")
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = if (status == BluetoothGatt.GATT_SUCCESS) gatt.getService(serviceId) else null
                stateCharacteristic = service?.getCharacteristic(stateId)
                controlCharacteristic = service?.getCharacteristic(controlId)
                if (stateCharacteristic == null || controlCharacteristic == null)
                    fail("Gauge firmware does not support paired selection")
                else if (!gatt.readCharacteristic(stateCharacteristic)) fail("Could not start secure state read")
            }
            @Deprecated("Required for Android 10 through 12")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
            ) {
                if (Build.VERSION.SDK_INT < 33) onStateRead(gatt, characteristic.uuid, characteristic.value, status)
            }
            override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                value: ByteArray, status: Int,
            ) { onStateRead(gatt, characteristic.uuid, value, status) }
            private fun onStateRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
                if (result.isCompleted || uuid != stateId) return
                if ((status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                     status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) && !writeSent) {
                    awaitBond(gatt)
                    return
                }
                val legacy = value.size == 4 && value[0].toInt() == 1
                val extended = value.size == 8 && value[0].toInt() == 2
                if (status != BluetoothGatt.GATT_SUCCESS || (!legacy && !extended)) {
                    fail("Secure gauge state read failed ($status). Open pairing on the gauge and accept Android's prompt.")
                    return
                }
                if (extended && ((value[1].toInt() and 0xff) > 4 ||
                                 (value[2].toInt() and 0xff) > 4 ||
                                 (value[3].toInt() and 0xff) > 3)) {
                    fail("Gauge returned invalid saved state")
                    return
                }
                if (!writeSent) {
                    if (opcode == 2 && !extended) return fail("Gauge does not support saved rotation")
                    if (extended) baseRevision = (4..7).fold(0L) { acc, offset ->
                        acc or ((value[offset].toLong() and 0xff) shl ((offset - 4) * 8))
                    }
                    if (opcode == 2 && (value[3].toInt() and 0xff) == target) {
                        result.complete(GaugeSavedSnapshot(value[2].toInt() and 0xff,
                            target, baseRevision))
                        return
                    }
                    writeSent = true
                    val control = controlCharacteristic ?: return fail("Control characteristic missing")
                    val bytes = if (opcode == 1) byteArrayOf(1, target.toByte()) else byteArrayOf(
                        2, target.toByte(), baseRevision.toByte(), (baseRevision shr 8).toByte(),
                        (baseRevision shr 16).toByte(), (baseRevision shr 24).toByte(),
                    )
                    val started = if (Build.VERSION.SDK_INT >= 33)
                        gatt.writeCharacteristic(control, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
                    else {
                        @Suppress("DEPRECATION")
                        control.value = bytes
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(control)
                    }
                    if (!started) fail("Could not start secure selection write")
                } else if (extended) {
                    val revision = (4..7).fold(0L) { acc, offset ->
                        acc or ((value[offset].toLong() and 0xff) shl ((offset - 4) * 8))
                    }
                    val snapshot = GaugeSavedSnapshot(value[2].toInt() and 0xff,
                        value[3].toInt() and 0xff, revision)
                    val confirmed = if (opcode == 1) value[1].toInt() == target &&
                        snapshot.readingIndex == target
                        else snapshot.rotation == target && revision > baseRevision
                    if (confirmed) result.complete(snapshot)
                    else if (opcode == 2 && revision > baseRevision)
                        fail("Gauge state changed before rotation was applied. Read saved state and retry")
                    else retryState(gatt)
                } else if (opcode == 1 && value[1].toInt() == target) {
                    result.complete(GaugeSavedSnapshot(target, 0, 0))
                } else retryState(gatt)
            }
            private fun retryState(gatt: BluetoothGatt) {
                if (++readAttempts >= 10) {
                    fail("Gauge did not confirm the new setting")
                } else {
                    handler.postDelayed({
                        if (!result.isCompleted && !gatt.readCharacteristic(stateCharacteristic))
                            fail("Could not read applied gauge state")
                    }, 200)
                }
            }
            private fun awaitBond(gatt: BluetoothGatt) {
                if (result.isCompleted) return
                when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        val state = stateCharacteristic ?: return fail("State characteristic missing")
                        if (!gatt.readCharacteristic(state)) fail("Could not retry secure state read")
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        sawBonding = true
                        handler.postDelayed({ awaitBond(gatt) }, 250)
                    }
                    else -> {
                        if (sawBonding) return fail("Android pairing was rejected or cancelled")
                        if (!bondStarted) {
                            bondStarted = true
                            if (!device.createBond()) return fail("Android could not start gauge pairing")
                        }
                        handler.postDelayed({ awaitBond(gatt) }, 250)
                    }
                }
            }
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
            ) {
                if (characteristic.uuid != controlId || result.isCompleted) return
                if (status != BluetoothGatt.GATT_SUCCESS) fail("Gauge rejected selection ($status)")
                else handler.postDelayed({
                    if (!result.isCompleted && !gatt.readCharacteristic(stateCharacteristic))
                        fail("Could not confirm applied gauge state")
                }, 200)
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Could not connect to the gauge")
        return try { withTimeout(60_000) { result.await() } }
        finally {
            result.cancel()
            handler.removeCallbacksAndMessages(null)
            gatt.disconnect()
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun readNearby(): CapabilitySnapshot {
        selectedDevice = null
        val device = scanNearby()
        val capabilities = readCapabilities(device)
        selectedDevice = device
        return capabilities
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanNearby(): BluetoothDevice {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("This phone has no Bluetooth adapter")
        if (!adapter.isEnabled) error("Turn on Bluetooth to find the gauge")
        val scanner = adapter.bluetoothLeScanner ?: error("BLE scanning is unavailable")
        val found = CompletableDeferred<BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!found.isCompleted) {
                    found.complete(result.device)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                if (!found.isCompleted) found.completeExceptionally(
                    IllegalStateException("BLE scan failed ($errorCode)"))
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceId)).build()
        scanner.startScan(
            listOf(filter),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        val device = try {
            withTimeout(15_000) { found.await() }
        } finally {
            scanner.stopScan(callback)
        }
        return device
    }

    @SuppressLint("MissingPermission")
    private suspend fun readCapabilities(device: BluetoothDevice): CapabilitySnapshot {
        val result = CompletableDeferred<ByteArray>()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!result.isCompleted) result.completeExceptionally(
                        IllegalStateException("Gauge disconnected ($status)"))
                } else if (newState == BluetoothProfile.STATE_CONNECTED && !gatt.requestMtu(185)) {
                    discover(gatt)
                }
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                discover(gatt)
            }
            private fun discover(gatt: BluetoothGatt) {
                if (!gatt.discoverServices() && !result.isCompleted) {
                    result.completeExceptionally(IllegalStateException("Could not discover gauge services"))
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val value = if (status == BluetoothGatt.GATT_SUCCESS)
                    gatt.getService(serviceId)?.getCharacteristic(capabilityId) else null
                if (value == null || !gatt.readCharacteristic(value)) {
                    if (!result.isCompleted) result.completeExceptionally(
                        IllegalStateException("Gauge capability read is unavailable"))
                }
            }
            @Deprecated("Required for Android 10 through 12")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
            ) {
                if (Build.VERSION.SDK_INT < 33) finish(characteristic.uuid, characteristic.value, status)
            }
            override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                value: ByteArray, status: Int,
            ) {
                finish(characteristic.uuid, value, status)
            }
            private fun finish(uuid: UUID, bytes: ByteArray, status: Int) {
                if (result.isCompleted || uuid != capabilityId) return
                if (status == BluetoothGatt.GATT_SUCCESS) result.complete(bytes.copyOf())
                else result.completeExceptionally(IllegalStateException("Gauge read failed ($status)"))
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Could not connect to the gauge")
        return try {
            val bytes = withTimeout(15_000) { result.await() }
            parseCapabilities(bytes)
        } finally {
            gatt.disconnect()
            gatt.close()
        }
    }

    private fun parseCapabilities(bytes: ByteArray): CapabilitySnapshot {
        val objectValue = JSONObject(bytes.toString(Charsets.UTF_8))
        val protocol = objectValue.getInt("protocolMajor")
        if (protocol != 0) error("Unsupported gauge protocol $protocol")
        val configWrite = objectValue.getBoolean("configWrite")
        val ota = objectValue.getBoolean("ota")
        if (configWrite || ota) error("Unexpected experimental capability flags")
        return CapabilitySnapshot(
            board = objectValue.getString("board"),
            protocolMajor = protocol,
            maxAdapterLinks = objectValue.getInt("maxAdapterLinks").coerceIn(0, 2),
            simultaneousVerified = objectValue.getBoolean("simultaneousAdapterLinksVerified"),
            configWrite = configWrite,
            experimentalNumericConfig = objectValue.optBoolean("experimentalNumericConfig", false),
            savedStateRead = objectValue.optBoolean("savedStateRead", false),
            quickSelect = objectValue.optBoolean("quickSelect", false),
            displayRotationWrite = objectValue.optBoolean("displayRotationWrite", false),
            ota = ota,
        )
    }
}
