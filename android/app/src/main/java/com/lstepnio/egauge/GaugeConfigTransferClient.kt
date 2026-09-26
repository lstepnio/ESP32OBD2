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
import java.util.concurrent.atomic.AtomicBoolean

internal class GaugeLinkException(message: String) : IllegalStateException(message)

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
    data class ActiveStatus(val revision: Long, val sha256: String, val transferPhase: Int,
                            val lastResult: Int)
    data class ActiveDocument(val revision: Long, val sha256: String, val length: Int,
                              val vehicleProfileId: String, val definitionCount: Int,
                              val pageCount: Int, val alertCount: Int, val json: String)
    data class Diagnostics(val milFresh: Boolean, val milOn: Boolean, val reportedCount: Int,
                           val confirmedFresh: Boolean, val confirmedCount: Int, val confirmedFirst: String?,
                           val pendingFresh: Boolean, val pendingCount: Int, val pendingFirst: String?,
                           val permanentFresh: Boolean, val permanentCount: Int, val permanentFirst: String?)
    data class BootIdentity(val otaState: Int, val partitionSubtype: Int, val secureVersion: Long,
                            val elfSha256: String, val version: String, val partitionAddress: Long)
    data class UpdateResult(val partitionAddress: Long, val elfSha256: String)
    private data class Status(val phase: Int, val result: Int, val opcode: Int, val sequence: Long,
                              val transferId: Long, val accepted: Long, val revision: Long, val hash: ByteArray)
    private data class OtaStatus(val phase: Int, val result: Int, val opcode: Int, val sequence: Long,
                                 val transferId: Long, val accepted: Long, val total: Long,
                                 val digest: ByteArray)

    @SuppressLint("MissingPermission")
    private suspend fun <T> withGauge(device: BluetoothDevice, operationTimeoutMs: Long = 240_000,
                                      action: suspend Session.() -> T): T {
        val events = Channel<Event>(Channel.UNLIMITED)
        var requestedMtu = 23
        val readySent = AtomicBoolean(false)
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
                else if (readySent.compareAndSet(false, true)) events.trySend(Event.Ready(requestedMtu))
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
            if (ready is Event.Failed) throw GaugeLinkException(ready.reason)
            require(ready is Event.Ready)
            withTimeout(operationTimeoutMs) {
                val session = Session(gatt, events, ready.mtu)
                session.establishOwner()
                session.action()
            }
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
            if (it is Event.Failed) throw GaugeLinkException(it.reason)
        }
        @SuppressLint("MissingPermission")
        private suspend fun readEvent(): Event.Read {
            check(gatt.readCharacteristic(state)) { "Could not request transfer status" }
            val event = next()
            require(event is Event.Read) { "Expected GATT read, received ${event.javaClass.simpleName}" }
            return event
        }
        suspend fun establishOwner() {
            repeat(4) {
                val result = readEvent()
                if (result.status == BluetoothGatt.GATT_SUCCESS &&
                    ((result.bytes.size == 8 && result.bytes[0].toInt() == 2) ||
                     (result.bytes.size == 64 && result.bytes[0].toInt() == 3) ||
                     (result.bytes.size == 56 && result.bytes[0].toInt() == 4) ||
                     (result.bytes.size == 32 && result.bytes[0].toInt() == 5) ||
                     (result.bytes.size == 60 && result.bytes[0].toInt() == 6) ||
                     (result.bytes.size in 52..180 && result.bytes[0].toInt() == 7))) return
                if (result.status != BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION &&
                    result.status != BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION)
                    error("Gauge owner state read failed (${result.status})")
                delay(250)
            }
            error("Gauge owner link did not become authenticated")
        }
        suspend fun readRaw(): ByteArray {
            val event = readEvent()
            if (event.status != BluetoothGatt.GATT_SUCCESS)
                throw GaugeLinkException("Protected read failed (GATT ${event.status})")
            return event.bytes
        }
        suspend fun read(): Status {
            val bytes = readRaw()
            require(bytes.size == 64 && bytes[0].toInt() == 3) { "Gauge returned an unsupported status version" }
            return Status(bytes[1].toInt() and 255, bytes[2].toInt() and 255, bytes[3].toInt() and 255,
                u32(bytes, 4), u32(bytes, 8), u32(bytes, 12), u32(bytes, 20), bytes.copyOfRange(32, 64))
        }
        @SuppressLint("MissingPermission")
        suspend fun writeRaw(bytes: ByteArray) {
            val started = if (Build.VERSION.SDK_INT >= 33)
                gatt.writeCharacteristic(control, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
            else {
                @Suppress("DEPRECATION")
                control.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(control)
            }
            check(started) { "Could not queue protected gauge command" }
            val write = next()
            if (write !is Event.Write) throw GaugeLinkException("Unexpected gauge response during write")
            if (write.status != BluetoothGatt.GATT_SUCCESS)
                throw GaugeLinkException("Protected write failed (GATT ${write.status})")
        }
        suspend fun command(opcode: Int, sequence: Long, payload: ByteArray = byteArrayOf()): Status {
            writeRaw(byteArrayOf(opcode.toByte()) + le32(sequence) + payload)
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
        suspend fun readOta(): OtaStatus {
            val bytes = readRaw()
            require(bytes.size == 56 && bytes[0].toInt() == 4) { "Gauge returned an unsupported update status" }
            return OtaStatus(bytes[1].toInt() and 255, bytes[2].toInt() and 255,
                bytes[3].toInt() and 255, u32(bytes, 4), u32(bytes, 8), u32(bytes, 12),
                u32(bytes, 16), bytes.copyOfRange(24, 56))
        }
        suspend fun otaCommand(opcode: Int, sequence: Long, payload: ByteArray = byteArrayOf(),
                               attempts: Int = 80): OtaStatus {
            writeRaw(byteArrayOf(opcode.toByte()) + le32(sequence) + payload)
            if (opcode == 0x27) return readOta()
            repeat(attempts) {
                val status = readOta()
                if (status.sequence == sequence && status.opcode == opcode) {
                    check(status.result == 0) {
                        "Gauge update command $opcode failed (result ${status.result}, phase ${status.phase}, transfer ${status.transferId})"
                    }
                    return status
                }
                delay(75)
            }
            error("Gauge did not confirm update command $opcode")
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

    suspend fun readActive(device: BluetoothDevice): ActiveStatus {
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Pair this phone as gauge owner first" }
        val status = withGauge(device) { command(0x17, 1) }
        return ActiveStatus(status.revision, status.hash.joinToString("") { "%02x".format(it) },
            status.phase, status.result)
    }

    /** Read only the currently committed document over the authenticated owner link. */
    suspend fun readActiveDocument(device: BluetoothDevice): ActiveDocument? {
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Pair this phone as gauge owner first" }
        return withGauge(device, operationTimeoutMs = 600_000) {
            var offset = 0
            var revision = 0L
            var digest: ByteArray? = null
            var document: ByteArray? = null
            do {
                writeRaw(byteArrayOf(0x32) + le32((offset + 1).toLong()) + le32(offset.toLong()))
                val response = readRaw()
                require(response.size in 52..180 && response[0].toInt() == 7) {
                    "Gauge returned an unsupported active document chunk"
                }
                val present = response[1].toInt() and 255
                if (present == 0) {
                    require(offset == 0 && response.size == 52) { "Gauge document disappeared during read" }
                    return@withGauge null
                }
                require(present == 1 && response.sliceArray(2..3).all { it == 0.toByte() } &&
                    response.sliceArray(17..19).all { it == 0.toByte() }) {
                    "Gauge returned malformed document metadata"
                }
                val receivedRevision = u32(response, 4)
                val length = u32(response, 8)
                val receivedOffset = u32(response, 12)
                val count = response[16].toInt() and 255
                val receivedDigest = response.copyOfRange(20, 52)
                require(receivedRevision > 0 && length in 1..65536 && receivedOffset == offset.toLong() &&
                    count in 1..128 && count <= length - offset && response.size == 52 + count) {
                    "Gauge returned an invalid document range"
                }
                if (document == null) {
                    revision = receivedRevision
                    digest = receivedDigest
                    document = ByteArray(length.toInt())
                } else require(receivedRevision == revision && document.size == length.toInt() &&
                    receivedDigest.contentEquals(requireNotNull(digest))) {
                    "Gauge configuration changed during read"
                }
                response.copyInto(requireNotNull(document), offset, 52, 52 + count)
                offset += count
            } while (offset < requireNotNull(document).size)
            val bytes = requireNotNull(document)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            check(hash.contentEquals(requireNotNull(digest))) { "Gauge document failed SHA-256 readback" }
            val jsonText = bytes.toString(Charsets.UTF_8)
            val json = JSONObject(jsonText)
            require(json.getInt("schemaVersion") == 1 &&
                json.getLong("baseRevision") == revision - 1) {
                "Gauge returned an unsupported document schema"
            }
            ActiveDocument(revision, hash.joinToString("") { "%02x".format(it) }, bytes.size,
                json.getString("vehicleProfileId"), json.getJSONArray("definitions").length(),
                json.getJSONArray("pages").length(), json.getJSONArray("alerts").length(), jsonText)
        }
    }

    suspend fun readDiagnostics(device: BluetoothDevice): Diagnostics {
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Pair this phone as gauge owner first" }
        val bytes = withGauge(device) {
            writeRaw(byteArrayOf(0x30) + le32(1))
            readRaw()
        }
        require(bytes.size == 32 && bytes[0].toInt() == 5) { "Gauge returned an unsupported diagnostic snapshot" }
        val flags = bytes[1].toInt() and 255
        fun code(offset: Int): String? {
            val first = bytes[offset].toInt() and 255
            val second = bytes[offset + 1].toInt() and 255
            if (first == 0 && second == 0) return null
            val system = "PCBU"[first ushr 6]
            return "$system${(first ushr 4) and 3}${(first and 15).toString(16).uppercase()}" +
                "${(second ushr 4).toString(16).uppercase()}${(second and 15).toString(16).uppercase()}"
        }
        return Diagnostics(flags and 1 != 0, flags and 2 != 0, bytes[2].toInt() and 255,
            flags and 4 != 0, bytes[3].toInt() and 255, code(6),
            flags and 8 != 0, bytes[4].toInt() and 255, code(8),
            flags and 16 != 0, bytes[5].toInt() and 255, code(10))
    }

    suspend fun readBootIdentity(device: BluetoothDevice): BootIdentity {
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Pair this phone as gauge owner first" }
        val bytes = withGauge(device) {
            writeRaw(byteArrayOf(0x31) + le32(1))
            readRaw()
        }
        require(bytes.size == 60 && bytes[0].toInt() == 6) { "Gauge returned an unsupported boot identity" }
        val versionBytes = bytes.copyOfRange(40, 56)
        val versionEnd = versionBytes.indexOf(0).let { if (it < 0) versionBytes.size else it }
        return BootIdentity(bytes[1].toInt() and 255, bytes[2].toInt() and 255,
            u32(bytes, 4), bytes.copyOfRange(8, 40).joinToString("") { "%02x".format(it) },
            versionBytes.copyOfRange(0, versionEnd).toString(Charsets.UTF_8), u32(bytes, 56))
    }

    /** Development-only update path. The gauge independently verifies the signed image. */
    suspend fun installUpdate(device: BluetoothDevice, bundle: DevUpdateBundle,
                              progress: (Int) -> Unit): UpdateResult {
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Pair this phone as gauge owner first" }
        val before = readBootIdentity(device)
        val random = SecureRandom()
        val transferId = (random.nextInt().toLong() and 0xffffffffL).coerceAtLeast(1)
        var sequence = (random.nextInt().toLong() and 0xffffffffL).coerceAtLeast(1)
        val id = le32(transferId)
        val expectedElf = bundle.elfSha256.joinToString("") { "%02x".format(it) }
        check(before.elfSha256 != expectedElf) { "This signed firmware image is already running on the gauge" }
        progress(0)
        withGauge(device, 1_200_000) {
            val current = otaCommand(0x27, sequence++)
            if (current.phase in 1..3 && current.transferId != 0L)
                otaCommand(0x26, sequence++, le32(current.transferId))
            else check(current.phase == 0) { "Gauge is already activating an update" }
            otaCommand(0x20, sequence++, id + le32(bundle.image.size.toLong()) + le32(0x31534745))
            try {
                for (part in 0..3) otaCommand(0x21, sequence++, id + byteArrayOf(part.toByte()) +
                    bundle.sha256.copyOfRange(part * 8, part * 8 + 8))
                for (part in 0 until (bundle.signatureDer.size + 7) / 8) {
                    val start = part * 8
                    otaCommand(0x28, sequence++, id + byteArrayOf(part.toByte(), bundle.signatureDer.size.toByte()) +
                        bundle.signatureDer.copyOfRange(start, minOf(start + 8, bundle.signatureDer.size)))
                }
                otaCommand(0x22, sequence++, id)
                val chunkSize = minOf(160, mtu - 16)
                check(chunkSize > 0) { "Gauge MTU is too small for firmware chunks" }
                var offset = 0
                var lastProgress = 0
                while (offset < bundle.image.size) {
                    val chunk = bundle.image.copyOfRange(offset, minOf(offset + chunkSize, bundle.image.size))
                    val status = otaCommand(0x23, sequence++, id + le32(offset.toLong()) + chunk)
                    check(status.accepted == offset.toLong() + chunk.size) { "Gauge accepted an unexpected update offset" }
                    offset += chunk.size
                    val percent = offset * 100 / bundle.image.size
                    if (percent > lastProgress) {
                        lastProgress = percent
                        progress(percent)
                    }
                }
                val verified = otaCommand(0x24, sequence++, id, attempts = 600)
                check(verified.phase == 3 && verified.total == bundle.image.size.toLong() &&
                    verified.digest.contentEquals(bundle.sha256)) { "Gauge did not verify the signed image" }
            } catch (error: Exception) {
                runCatching { otaCommand(0x26, sequence++, id) }
                throw error
            }
            val activated = otaCommand(0x25, sequence++, id)
            check(activated.phase == 4) { "Gauge did not select the update for boot" }
        }
        // Firmware schedules the restart five seconds after activation. Allow its boot and
        // health confirmation to finish before treating the prior slot as a rollback.
        delay(10000)
        repeat(8) {
            val observed = runCatching { readBootIdentity(device) }.getOrNull()
            if (observed != null && observed.partitionAddress != before.partitionAddress &&
                observed.elfSha256 == expectedElf && observed.otaState == 2)
                return UpdateResult(observed.partitionAddress, observed.elfSha256)
            if (observed != null && observed.partitionAddress == before.partitionAddress &&
                observed.elfSha256 == before.elfSha256 && observed.otaState == 2)
                error("Gauge is running the previous valid firmware after the update attempt. The trial image was not confirmed.")
            delay(1500)
        }
        error("Update was sent, but the new image was not confirmed as running. Check the gauge before retrying.")
    }

    suspend fun apply(device: BluetoothDevice, draft: Draft, profileId: String,
                      expectedBaseRevision: Long, expectedBaseSha256: String): Applied {
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Pair this phone as gauge owner first" }
        require(expectedBaseRevision >= 0 && expectedBaseSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Refresh the saved gauge configuration before sending"
        }
        val random = SecureRandom()
        val transferId = (random.nextInt().toLong() and 0xffffffffL).coerceAtLeast(1)
        var sequence = (random.nextInt().toLong() and 0xffffffffL).coerceAtLeast(1)
        lateinit var digest: ByteArray
        var expectedRevision = 0L
        withGauge(device) {
            val current = command(0x17, sequence++)
            check(current.phase == 0) { "A gauge configuration transfer is already in progress" }
            val currentHash = current.hash.joinToString("") { "%02x".format(it) }
            check(current.revision == expectedBaseRevision && currentHash == expectedBaseSha256) {
                "Gauge configuration changed from verified revision $expectedBaseRevision to ${current.revision}. " +
                    "Refresh and review the differences before sending."
            }
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
