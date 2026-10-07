package com.lstepnio.egauge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GaugeCandidate internal constructor(
    val id: String,
    val name: String,
    val signalDbm: Int,
    internal val device: BluetoothDevice,
)

/** Protocol-0 public discovery and bounded paired reading selection. */
class BleCapabilityClient(private val context: Context) {
    private companion object { const val TAG = "EGaugePairing" }
    private val serviceId = UUID.fromString("6f1a0000-9e3b-4f45-a714-69c9d23b6c00")
    private val capabilityId = UUID.fromString("6f1a0001-9e3b-4f45-a714-69c9d23b6c00")
    private val controlId = UUID.fromString("6f1a0002-9e3b-4f45-a714-69c9d23b6c00")
    private val stateId = UUID.fromString("6f1a0003-9e3b-4f45-a714-69c9d23b6c00")
    private val pairingStatusId = UUID.fromString("6f1a0004-9e3b-4f45-a714-69c9d23b6c00")
    private var selectedDevice: BluetoothDevice? = null
    fun selectedGauge(): BluetoothDevice = selectedDevice ?: error("Read gauge capabilities first")

    /** Reads the public, optional pairing-window state. Older firmware has no such characteristic. */
    @SuppressLint("MissingPermission")
    suspend fun readPairingWindowStatus(): GaugePairingWindow? {
        val device = selectedDevice ?: error("Read this gauge's capabilities first")
        val result = CompletableDeferred<ByteArray?>()
        val service = serviceId
        val statusId = pairingStatusId
        val discoveryStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        val servicesHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val callback = object : BluetoothGattCallback() {
            private fun fail(message: String) {
                if (!result.isCompleted) result.completeExceptionally(IllegalStateException(message))
            }
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED)
                    fail("Gauge disconnected while checking pairing readiness ($status)")
                else if (newState == BluetoothProfile.STATE_CONNECTED &&
                    discoveryStarted.compareAndSet(false, true) && !gatt.discoverServices())
                    fail("Could not check gauge pairing readiness")
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (!servicesHandled.compareAndSet(false, true)) return
                if (result.isCompleted) return
                val characteristic = if (status == BluetoothGatt.GATT_SUCCESS)
                    gatt.getService(service)?.getCharacteristic(statusId) else null
                if (characteristic == null) result.complete(null)
                else if (!gatt.readCharacteristic(characteristic)) fail("Could not read gauge pairing readiness")
            }
            @Deprecated("Required for Android 10 through 12")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                                              status: Int) {
                if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == statusId) {
                    if (status == BluetoothGatt.GATT_SUCCESS) result.complete(characteristic.value?.copyOf())
                    else fail("Gauge pairing readiness read failed ($status)")
                }
            }
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                                              value: ByteArray, status: Int) {
                if (characteristic.uuid != statusId || result.isCompleted) return
                if (status == BluetoothGatt.GATT_SUCCESS) result.complete(value.copyOf())
                else fail("Gauge pairing readiness read failed ($status)")
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Could not connect to the gauge")
        return try {
            val bytes = withTimeout(10_000) { result.await() } ?: return null
            if (bytes.size != 4 || bytes[0].toInt() != 1) error("Invalid gauge pairing readiness")
            val state = when (bytes[1].toInt() and 0xff) {
                0 -> PairingWindowState.CLOSED
                1 -> PairingWindowState.READY
                2 -> PairingWindowState.CODE_DISPLAYED
                3 -> PairingWindowState.OWNER_PRESENT
                else -> error("Invalid gauge pairing readiness")
            }
            val seconds = (bytes[2].toInt() and 0xff) or ((bytes[3].toInt() and 0xff) shl 8)
            Log.i(TAG, "pairing_window_state=$state seconds_remaining=$seconds")
            GaugePairingWindow(state, seconds)
        } finally {
            result.cancel()
            gatt.disconnect()
            gatt.close()
        }
    }

    /** Reads the persisted selection without initiating Android pairing. */
    @SuppressLint("MissingPermission")
    suspend fun readSavedSnapshot(): GaugeSavedSnapshot = readSavedSnapshotInternal()

    /** Starts Android bonding from the explicit Pair action, then verifies protected gauge access. */
    @SuppressLint("MissingPermission")
    suspend fun pairOwner(onProgress: (PairingProgress) -> Unit = {}): GaugeSavedSnapshot {
        val device = selectedDevice ?: error("Read this gauge's capabilities first")
        Log.i(TAG, "pair_attempt_started android_bond=${device.bondState}")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            onProgress(PairingProgress.WAITING_FOR_ANDROID)
            if (device.bondState != BluetoothDevice.BOND_BONDING) {
                val started = device.createBond()
                Log.i(TAG, "android_bond_request_started=$started")
                if (!started && device.bondState != BluetoothDevice.BOND_BONDING &&
                    device.bondState != BluetoothDevice.BOND_BONDED) {
                    error("android_pair_start_failed")
                }
            }
            awaitAndroidBond(device)
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) error("android_bond_incomplete")
        onProgress(PairingProgress.CHECKING_GAUGE_ACCESS)
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
        var attempt = 0
        while (true) {
            if (device.bondState != BluetoothDevice.BOND_BONDED) error("android_bond_incomplete")
            attempt++
            try {
                val remaining = (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)
                val snapshot = readSavedSnapshotInternal(timeoutMs = minOf(6_000L, remaining))
                if (device.bondState != BluetoothDevice.BOND_BONDED) error("android_bond_incomplete")
                Log.i(TAG, "owner_verification=passed attempt=$attempt")
                return snapshot
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (device.bondState != BluetoothDevice.BOND_BONDED) error("android_bond_incomplete")
                if (!retryableOwnerReadFailure(failure)) throw failure
                Log.w(TAG, "owner_verification_retry attempt=$attempt reason=${failure.message}")
                val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0) error("owner_verification_pending")
                delay(minOf(500L, remaining))
            }
        }
    }

    private fun retryableOwnerReadFailure(failure: Exception): Boolean {
        val reason = failure.message.orEmpty()
        return reason.startsWith("Gauge disconnected during saved state read") ||
            reason.startsWith("Could not connect to the gauge") ||
            reason.startsWith("Could not discover gauge snapshot") ||
            reason.startsWith("Gauge secure state read is unavailable") ||
            reason.startsWith("Saved gauge state read failed") ||
            reason.startsWith("Could not retry saved state read") ||
            reason == "bonded_not_owner" || reason == "gauge_owner_read_timeout"
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitAndroidBond(device: BluetoothDevice) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        var sawBonding = device.bondState == BluetoothDevice.BOND_BONDING
        var previousState = device.bondState
        while (android.os.SystemClock.elapsedRealtime() - startedAt < 60_000) {
            val state = device.bondState
            if (state != previousState) {
                Log.i(TAG, "android_bond_state=$state")
                previousState = state
            }
            when (state) {
                BluetoothDevice.BOND_BONDED -> return
                BluetoothDevice.BOND_BONDING -> sawBonding = true
                else -> {
                    if (sawBonding) error("pairing_failed")
                    if (android.os.SystemClock.elapsedRealtime() - startedAt >= 10_000)
                        error("android_pair_start_failed")
                }
            }
            delay(250)
        }
        error("pairing_timeout")
    }

    @SuppressLint("MissingPermission")
    private suspend fun readSavedSnapshotInternal(timeoutMs: Long = 60_000): GaugeSavedSnapshot {
        val device = selectedDevice ?: error("Read this gauge's capabilities first")
        val result = CompletableDeferred<ByteArray>()
        val handler = Handler(Looper.getMainLooper())
        var snapshot: BluetoothGattCharacteristic? = null
        var sawBonding = false
        var authenticatedRetries = 0
        val discovery = GattDiscovery {
            if (!result.isCompleted) result.completeExceptionally(IllegalStateException("Could not discover gauge snapshot"))
        }
        val servicesHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val callback = object : BluetoothGattCallback() {
            private fun fail(message: String) {
                if (!result.isCompleted) result.completeExceptionally(IllegalStateException(message))
            }
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED)
                    fail("Gauge disconnected during saved state read ($status)")
                else if (newState == BluetoothProfile.STATE_CONNECTED)
                    discovery.connect(gatt)
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { discovery.discover(gatt) }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (!servicesHandled.compareAndSet(false, true)) return
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
                Log.i(TAG, "saved_state_read status=$status android_bond=${device.bondState}")
                if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                    status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) { awaitBond(gatt); return }
                if (status == BluetoothGatt.GATT_SUCCESS) result.complete(value.copyOf())
                else fail("Saved gauge state read failed ($status)")
            }
            private fun awaitBond(gatt: BluetoothGatt) {
                if (result.isCompleted) return
                val bondState = device.bondState
                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        if (++authenticatedRetries > 20)
                            return fail("bonded_not_owner")
                        handler.postDelayed({
                            if (!result.isCompleted && !gatt.readCharacteristic(snapshot))
                                fail("Could not retry saved state read")
                        }, 250)
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        sawBonding = true
                        handler.postDelayed({ awaitBond(gatt) }, 250)
                    }
                    else -> {
                        if (sawBonding) return fail("pairing_cancelled")
                        return fail("pairing_required")
                    }
                }
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Could not connect to the gauge")
        return try {
            val bytes = withTimeoutOrNull(timeoutMs) { result.await() }
                ?: error("gauge_owner_read_timeout")
            if (bytes.size != 8 || bytes[0].toInt() != 2) error("Gauge does not offer durable saved state")
            val index = bytes[2].toInt() and 0xff
            val rotation = bytes[3].toInt() and 0xff
            if (index !in 0..4 || rotation !in 0..3) error("Invalid saved gauge state")
            val revision = (4..7).fold(0L) { acc, offset ->
                acc or ((bytes[offset].toLong() and 0xff) shl ((offset - 4) * 8))
            }
            Log.i(TAG, "saved_state_read=passed")
            GaugeSavedSnapshot(index, rotation, revision)
        } finally {
            result.cancel()
            handler.removeCallbacksAndMessages(null)
            gatt.disconnect()
            discovery.close()
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
        var sawBonding = false
        val discovery = GattDiscovery {
            if (!result.isCompleted) result.completeExceptionally(IllegalStateException("Could not discover gauge control service"))
        }
        val servicesHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val callback = object : BluetoothGattCallback() {
            private fun fail(message: String) {
                if (!result.isCompleted) result.completeExceptionally(IllegalStateException(message))
            }
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED)
                    fail("Gauge disconnected during selection ($status)")
                else if (newState == BluetoothProfile.STATE_CONNECTED)
                    discovery.connect(gatt)
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { discovery.discover(gatt) }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (!servicesHandled.compareAndSet(false, true)) return
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
                        gatt.writeCharacteristic(control, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
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
                        if (sawBonding) return fail("pairing_cancelled")
                        fail("pairing_required")
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
            discovery.close()
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun readNearby(): CapabilitySnapshot {
        selectedDevice = null
        val candidates = scanNearbyCandidates()
        if (candidates.size != 1) {
            error("More than one gauge is nearby. Choose the gauge you want to manage.")
        }
        return readCandidate(candidates.single())
    }

    @SuppressLint("MissingPermission")
    suspend fun readCandidate(candidate: GaugeCandidate): CapabilitySnapshot {
        selectedDevice = null
        val device = candidate.device
        Log.i(TAG, "candidate_selected short_id=${device.address.filter(Char::isLetterOrDigit).takeLast(6)} android_bond=${device.bondState}")
        val capabilities = try {
            readCapabilities(device)
        } catch (error: IllegalStateException) {
            if (error.message?.startsWith("Gauge disconnected (") != true) throw error
            delay(400)
            readCapabilities(device)
        }
        selectedDevice = device
        return capabilities
    }

    @SuppressLint("MissingPermission")
    suspend fun scanNearbyCandidates(): List<GaugeCandidate> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("This phone has no Bluetooth adapter")
        if (!adapter.isEnabled) error("Turn on Bluetooth to find the gauge")
        val scanner = adapter.bluetoothLeScanner ?: error("BLE scanning is unavailable")
        val found = CompletableDeferred<Unit>()
        val candidates = ConcurrentHashMap<String, GaugeCandidate>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val id = device.address
                candidates[id] = GaugeCandidate(
                    id = id,
                    name = result.scanRecord?.deviceName?.takeIf(String::isNotBlank)
                        ?: device.name?.takeIf(String::isNotBlank) ?: "eGauge",
                    signalDbm = result.rssi,
                    device = device,
                )
                if (!found.isCompleted) found.complete(Unit)
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
        try {
            withTimeout(15_000) { found.await() }
            delay(1_200)
        } finally {
            scanner.stopScan(callback)
        }
        val sorted = candidates.values.sortedByDescending(GaugeCandidate::signalDbm)
        Log.i(TAG, "scan_completed candidate_count=${sorted.size}")
        return sorted
    }

    @SuppressLint("MissingPermission")
    private suspend fun readCapabilities(device: BluetoothDevice): CapabilitySnapshot {
        val result = CompletableDeferred<ByteArray>()
        val discovery = GattDiscovery {
            if (!result.isCompleted) result.completeExceptionally(IllegalStateException("Could not discover gauge services"))
        }
        val servicesHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!result.isCompleted) result.completeExceptionally(
                        IllegalStateException("Gauge disconnected ($status)"))
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    discovery.connect(gatt)
                }
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                discovery.discover(gatt)
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (!servicesHandled.compareAndSet(false, true)) return
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
            GaugeProtocolCodec.capabilities(bytes)
        } finally {
            gatt.disconnect()
            discovery.close()
            gatt.close()
        }
    }

}
