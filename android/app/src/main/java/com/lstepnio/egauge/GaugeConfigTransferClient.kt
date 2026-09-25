package com.lstepnio.egauge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Experimental protocol-0 owner transaction for the firmware's numeric ECM subset. */
class GaugeConfigTransferClient(private val context: Context) {
    private val serviceId = UUID.fromString("6f1a0000-9e3b-4f45-a714-69c9d23b6c00")
    private val controlId = UUID.fromString("6f1a0002-9e3b-4f45-a714-69c9d23b6c00")
    private val stateId = UUID.fromString("6f1a0003-9e3b-4f45-a714-69c9d23b6c00")
    private sealed interface Event {
        data class Ready(val mtu: Int) : Event
        data class Read(val bytes: ByteArray, val status: Int) : Event
        data class Write(val status: Int) : Event
        data class Failed(val reason: String) : Event
    }
    data class Applied(val revision: Long, val sha256: String)
    private data class Status(val phase: Int, val result: Int, val opcode: Int, val sequence: Long,
                              val transferId: Long, val accepted: Long, val revision: Long, val hash: ByteArray)

    @SuppressLint("MissingPermission")
    private suspend fun <T> withGauge(device: BluetoothDevice, action: suspend Session.() -> T): T {
        val events = Channel<Event>(Channel.UNLIMITED)
        var requestedMtu = 23
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED)
                    events.trySend(Event.Failed("Gauge disconnected during configuration ($status)"))
                else if (newState == BluetoothProfile.STATE_CONNECTED && !gatt.requestMtu(185))
                    gatt.discoverServices()
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) requestedMtu = mtu
                if (!gatt.discoverServices()) events.trySend(Event.Failed("Could not discover gauge services"))
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS ||
                    gatt.getService(serviceId)?.getCharacteristic(controlId) == null ||
                    gatt.getService(serviceId)?.getCharacteristic(stateId) == null)
                    events.trySend(Event.Failed("Gauge transfer service is unavailable"))
                else events.trySend(Event.Ready(requestedMtu))
            }
            @Deprecated("Required for Android 10 through 12")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == stateId)
                    events.trySend(Event.Read(characteristic.value.copyOf(), status))
            }
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
                                              value: ByteArray, status: Int) {
                if (characteristic.uuid == stateId) events.trySend(Event.Read(value.copyOf(), status))
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (characteristic.uuid == controlId) events.trySend(Event.Write(status))
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: error("Could not connect to the gauge")
        return try {
            val ready = withTimeout(20_000) { events.receive() }
            if (ready is Event.Failed) error(ready.reason)
            require(ready is Event.Ready)
            withTimeout(240_000) { Session(gatt, events, ready.mtu).action() }
        } finally {
            gatt.disconnect()
            gatt.close()
            events.close()
        }
    }

    private inner class Session(private val gatt: BluetoothGatt, private val events: Channel<Event>, val mtu: Int) {
        private val control = gatt.getService(serviceId).getCharacteristic(controlId)
        private val state = gatt.getService(serviceId).getCharacteristic(stateId)
        private suspend fun next(): Event = withTimeout(12_000) { events.receive() }.also {
            if (it is Event.Failed) error(it.reason)
        }
        @SuppressLint("MissingPermission")
        suspend fun read(): Status {
            check(gatt.readCharacteristic(state)) { "Could not request transfer status" }
            val event = next()
            require(event is Event.Read && event.status == BluetoothGatt.GATT_SUCCESS) {
                "Owner status read failed; check the gauge bond"
            }
            val bytes = event.bytes
            require(bytes.size == 64 && bytes[0].toInt() == 3) { "Gauge returned an unsupported status version" }
            return Status(bytes[1].toInt() and 255, bytes[2].toInt() and 255, bytes[3].toInt() and 255,
                u32(bytes, 4), u32(bytes, 8), u32(bytes, 12), u32(bytes, 20), bytes.copyOfRange(32, 64))
        }
        @SuppressLint("MissingPermission")
        suspend fun command(opcode: Int, sequence: Long, payload: ByteArray = byteArrayOf()): Status {
            val bytes = byteArrayOf(opcode.toByte()) + le32(sequence) + payload
            val started = if (Build.VERSION.SDK_INT >= 33)
                gatt.writeCharacteristic(control, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
            else {
                @Suppress("DEPRECATION")
                control.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(control)
            }
            check(started) { "Could not queue configuration command $opcode" }
            val write = next()
            require(write is Event.Write && write.status == BluetoothGatt.GATT_SUCCESS) {
                "Gauge rejected configuration command $opcode"
            }
            if (opcode == 0x17) return read()
            repeat(80) {
                val status = read()
                if (status.sequence == sequence && status.opcode == opcode) {
                    check(status.result == 0) { "Gauge configuration command $opcode failed (result ${status.result})" }
                    return status
                }
                delay(75)
            }
            error("Gauge did not confirm configuration command $opcode")
        }
    }

    /** Generates only the schema subset that config_runtime currently executes. */
    private fun document(draft: Draft, profileId: String, baseRevision: Long): ByteArray {
        require(draft.source == "ECM" && draft.pidId != "tcm")
        require(draft.warning in -40..215 && draft.critical in -40..215 &&
                draft.warning + draft.hysteresis < draft.critical &&
                draft.warning - draft.hysteresis >= -40)
        val template = context.assets.open("numeric_config_template.json").bufferedReader().use { it.readText() }
        val json = JSONObject(template)
        json.put("baseRevision", baseRevision)
        json.put("vehicleProfileId", profileId)
        val pages = json.getJSONArray("pages")
        val ids = listOf("rpm" to "page.engine", "coolant" to "page.thermal", "speed" to "page.speed")
        val selected = ids.firstOrNull { it.first == draft.pidId }?.second
        if (selected != null) {
            for (index in 0 until pages.length()) if (pages.getJSONObject(index).getString("id") == selected) {
                val first = pages.getJSONObject(0)
                pages.put(0, pages.getJSONObject(index))
                pages.put(index, first)
                break
            }
        }
        val alert = json.getJSONArray("alerts").getJSONObject(0)
        alert.put("warning", draft.warning).put("critical", draft.critical)
            .put("hysteresis", draft.hysteresis).put("triggerDwellMs", draft.triggerDwellMs)
            .put("clearDwellMs", draft.clearDwellMs)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun apply(device: BluetoothDevice, draft: Draft, profileId: String): Applied {
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Pair this phone as gauge owner first" }
        val random = SecureRandom()
        val transferId = (random.nextInt().toLong() and 0xffffffffL).coerceAtLeast(1)
        var sequence = (random.nextInt().toLong() and 0xffffffffL).coerceAtLeast(1)
        lateinit var digest: ByteArray
        var expectedRevision = 0L
        withGauge(device) {
            val current = command(0x17, sequence++)
            check(current.phase == 0) { "A gauge configuration transfer is already in progress" }
            val bytes = document(draft, profileId, current.revision)
            expectedRevision = current.revision + 1
            digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val id = le32(transferId)
            command(0x10, sequence++, id + le32(current.revision) + le32(bytes.size.toLong()))
            try {
                for (part in 0..3) command(0x11, sequence++, id + byteArrayOf(part.toByte()) + digest.copyOfRange(part * 8, part * 8 + 8))
                command(0x12, sequence++, id)
                val chunkSize = minOf(160, mtu - 16)
                check(chunkSize > 0) { "Gauge MTU is too small for configuration chunks" }
                var offset = 0
                while (offset < bytes.size) {
                    val chunk = bytes.copyOfRange(offset, minOf(bytes.size, offset + chunkSize))
                    val status = command(0x13, sequence++, id + le32(offset.toLong()) + chunk)
                    check(status.accepted == offset.toLong() + chunk.size) { "Gauge accepted an unexpected offset" }
                    offset += chunk.size
                }
                command(0x14, sequence++, id)
            } catch (error: Exception) {
                runCatching { command(0x16, sequence++, id) }
                throw error
            }
            // A disconnect during COMMIT is ambiguous. Reconnect and inspect the durable slot.
            val commit = runCatching { command(0x15, sequence++, id) }.getOrNull()
            if (commit != null) check(commit.phase == 4 && commit.revision == expectedRevision &&
                commit.hash.contentEquals(digest)) { "Gauge commit readback did not match the document" }
        }
        delay(6500)
        var confirmed: Status? = null
        repeat(5) {
            if (confirmed == null) {
                val observed = runCatching { withGauge(device) { command(0x17, sequence++) } }.getOrNull()
                if (observed?.phase in 1..3 && observed?.transferId == transferId &&
                    observed.opcode == 0x15 && observed.result != 0) {
                    runCatching { withGauge(device) { command(0x16, sequence++, le32(transferId)) } }
                    error("Gauge rejected configuration commit (result ${observed.result})")
                }
                if (observed?.phase == 0) confirmed = observed
                if (confirmed == null) delay(1200)
            }
        }
        val durable = confirmed ?: error("Gauge commit succeeded, but reboot readback is unavailable")
        if (durable.revision != expectedRevision || !durable.hash.contentEquals(digest)) {
            if (durable.phase in 1..3 && durable.transferId == transferId)
                runCatching { withGauge(device) { command(0x16, sequence++, le32(transferId)) } }
            error("Gauge did not activate the sent configuration (result ${durable.result}); active revision ${durable.revision}")
        }
        return Applied(durable.revision, digest.joinToString("") { "%02x".format(it) })
    }

    companion object {
        private fun le32(value: Long) = ByteArray(4) { ((value ushr (it * 8)) and 255).toByte() }
        private fun u32(value: ByteArray, offset: Int) = (0..3).fold(0L) { result, index ->
            result or ((value[offset + index].toLong() and 255) shl (index * 8))
        }
    }
}
