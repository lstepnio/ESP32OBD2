package com.lstepnio.egauge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WifiBulkSession(
    val address: ByteArray,
    val port: Int,
    val sessionId: Long,
    val key: ByteArray,
    val expiresInSeconds: Long,
    val ssid: String,
    val password: String,
) {
    init {
        require(address.size == 4 && port in 1..65535 && sessionId != 0L &&
            key.size == 32 && expiresInSeconds > 0 && ssid.isNotBlank() && password.length >= 8)
    }

    val addressText: String get() = address.joinToString(".") { (it.toInt() and 255).toString() }
}

/**
 * Short-lived local transport authorized through the encrypted owner BLE link.
 * Frames use AES-256-GCM with separate request and response nonces. Transfer
 * hashes, signatures, offsets and activation remain owned by gauge firmware.
 */
class WifiBulkClient(private val context: Context, private val session: WifiBulkSession) : AutoCloseable {
    private var socket: Socket? = null
    private var network: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var frameSequence = 0L

    suspend fun ota(command: ByteArray): ByteArray = frame(2, command)
    suspend fun otaBatch(commands: List<ByteArray>): ByteArray {
        val response = frame(4, WifiOtaBatchCodec.encode(commands))
        return WifiOtaBatchCodec.decode(response, commands.size)
    }
    suspend fun otaStatus(): ByteArray = frame(3, byteArrayOf(2))
    suspend fun configuration(command: ByteArray): ByteArray = frame(1, command)
    suspend fun configurationStatus(): ByteArray = frame(3, byteArrayOf(1))

    private suspend fun frame(kind: Int, plaintext: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        require(kind in 1..4 && plaintext.isNotEmpty() && plaintext.size <= 8448)
        val active = socket ?: connect().also { socket = it }
        val sequence = ++frameSequence
        val header = ByteArray(16)
        "EGW1".toByteArray(Charsets.US_ASCII).copyInto(header)
        putU32(header, 4, session.sessionId)
        putU32(header, 8, sequence)
        header[12] = kind.toByte()
        putU16(header, 14, plaintext.size)
        val encrypted = crypt(Cipher.ENCRYPT_MODE, header, plaintext, nonce(sequence, 0))
        val ciphertext = encrypted.copyOfRange(0, encrypted.size - 16)
        val tag = encrypted.copyOfRange(encrypted.size - 16, encrypted.size)
        active.getOutputStream().apply {
            write(header)
            write(tag)
            write(ciphertext)
            flush()
        }

        val responseHeader = readExactly(active, 16)
        require(responseHeader.copyOfRange(0, 4).contentEquals("EGW1".toByteArray(Charsets.US_ASCII)) &&
            u32(responseHeader, 4) == session.sessionId && u32(responseHeader, 8) == sequence &&
            (responseHeader[12].toInt() and 255) == (kind or 0x80)) {
            "Gauge returned an invalid Wi-Fi frame"
        }
        val length = u16(responseHeader, 14)
        require(length in 1..1040) { "Gauge returned an invalid Wi-Fi payload length" }
        val responseTag = readExactly(active, 16)
        val responseCiphertext = readExactly(active, length)
        val combined = responseCiphertext + responseTag
        val response = crypt(Cipher.DECRYPT_MODE, responseHeader, combined, nonce(sequence, 1))
        check(responseHeader[13].toInt() == 0) { "Gauge rejected the Wi-Fi bulk command" }
        response
    }

    private suspend fun connect(): Socket {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val selected = network ?: requestMaintenanceNetworkWithRetry(manager).also { network = it }
        return Socket().also { value ->
            selected.bindSocket(value)
            value.soTimeout = READ_TIMEOUT_MS
            value.tcpNoDelay = true
            value.connect(InetSocketAddress(InetAddress.getByAddress(session.address), session.port), 12_000)
        }
    }

    private suspend fun requestMaintenanceNetworkWithRetry(manager: ConnectivityManager): Network {
        var lastFailure: Exception? = null
        repeat(2) { attempt ->
            try {
                return requestMaintenanceNetwork(manager)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastFailure = error
                releaseNetworkRequest(manager)
                if (attempt == 0) delay(500)
            }
        }
        throw requireNotNull(lastFailure)
    }

    private suspend fun requestMaintenanceNetwork(manager: ConnectivityManager): Network =
        suspendCancellableCoroutine { continuation ->
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(session.ssid)
                .setWpa2Passphrase(session.password)
                .build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(value: Network) {
                    if (continuation.isActive) continuation.resume(value)
                }
                override fun onUnavailable() {
                    if (continuation.isActive)
                        continuation.resumeWithException(IllegalStateException(
                            "Android did not approve the temporary gauge network"))
                }
                override fun onLost(value: Network) {
                    if (network == value) {
                        runCatching { socket?.close() }
                        socket = null
                        network = null
                    }
                }
            }
            networkCallback = callback
            continuation.invokeOnCancellation { runCatching { manager.unregisterNetworkCallback(callback) } }
            manager.requestNetwork(request, callback, 30_000)
        }

    private fun crypt(mode: Int, header: ByteArray, input: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(session.key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return cipher.doFinal(input)
    }

    private fun nonce(sequence: Long, direction: Int) = ByteArray(12).also {
        putU32(it, 0, session.sessionId)
        putU32(it, 4, sequence)
        it[8] = direction.toByte()
    }

    private fun readExactly(socket: Socket, count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = socket.getInputStream().read(result, offset, count - offset)
            if (read < 0) throw EOFException("Gauge closed the Wi-Fi bulk connection")
            offset += read
        }
        return result
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        releaseNetworkRequest(context.getSystemService(ConnectivityManager::class.java))
        network = null
        session.key.fill(0)
    }

    private fun releaseNetworkRequest(manager: ConnectivityManager) {
        networkCallback?.let { callback -> runCatching { manager.unregisterNetworkCallback(callback) } }
        networkCallback = null
    }

    companion object {
        // Healthy hardware responds to an eight-command OTA batch within a few seconds.
        // Bound a lost gauge reset without making the foreground operation appear stuck.
        private const val READ_TIMEOUT_MS = 20_000

        private fun putU16(value: ByteArray, offset: Int, number: Int) {
            value[offset] = number.toByte()
            value[offset + 1] = (number ushr 8).toByte()
        }
        private fun u16(value: ByteArray, offset: Int) =
            (value[offset].toInt() and 255) or ((value[offset + 1].toInt() and 255) shl 8)
        private fun putU32(value: ByteArray, offset: Int, number: Long) {
            repeat(4) { value[offset + it] = (number ushr (it * 8)).toByte() }
        }
        private fun u32(value: ByteArray, offset: Int) = (0..3).fold(0L) { result, index ->
            result or ((value[offset + index].toLong() and 255) shl (index * 8))
        }
    }
}

internal object WifiOtaBatchCodec {
    private const val MAX_COMMANDS = 8
    private const val MAX_COMMAND_SIZE = 1040

    fun encode(commands: List<ByteArray>): ByteArray {
        require(commands.size in 1..MAX_COMMANDS)
        require(commands.all { it.size in 13..MAX_COMMAND_SIZE && it[0].toInt() == 0x23 })
        val result = ByteArray(1 + commands.sumOf { 2 + it.size })
        result[0] = commands.size.toByte()
        var offset = 1
        commands.forEach { command ->
            result[offset] = command.size.toByte()
            result[offset + 1] = (command.size ushr 8).toByte()
            command.copyInto(result, offset + 2)
            offset += 2 + command.size
        }
        return result
    }

    fun decode(response: ByteArray, expectedCount: Int): ByteArray {
        require(response.size == 57 && (response[0].toInt() and 255) == expectedCount) {
            "Gauge returned an invalid Wi-Fi OTA batch response"
        }
        return response.copyOfRange(1, response.size)
    }
}
