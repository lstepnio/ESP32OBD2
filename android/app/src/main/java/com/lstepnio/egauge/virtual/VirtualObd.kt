package com.lstepnio.egauge.virtual

import com.lstepnio.egauge.BuildConfig
import com.lstepnio.egauge.gearPositions
import java.util.Locale

/** Separate read-only routes for one logical vehicle. */
internal enum class VirtualSource(val label: String, val responder: String) {
    Engine("Main adapter", "7E8"),
    Transmission("Transmission adapter", "7E9");

    val available: Boolean get() = this == Engine || BuildConfig.DEBUG
}

internal class VirtualPid(
    val id: String,
    val source: VirtualSource,
    val request: String,
    prefixHex: String,
    val minPayloadBytes: Int,
    val periodMs: Long,
    staleAfterMs: Long,
    val decoder: VirtualDecoder,
    val gear: Boolean
) {
    val code = request.substring(2).toInt(16)
    val prefix = prefixHex.chunked(2).map { it.toInt(16) }
    val freshnessMs = staleAfterMs.coerceAtLeast(5_000)
}

internal class VirtualDecoder(
    private val offsetBytes: Int,
    private val length: Int,
    private val littleEndian: Boolean,
    private val signed: Boolean,
    private val numerator: Int,
    private val denominator: Int,
    private val offset: Double,
    private val minimum: Double,
    private val maximum: Double
) {
    fun decode(data: List<Int>): Double? {
        if (length !in 1..4 || offsetBytes < 0 || offsetBytes > data.size - length || denominator == 0) return null
        var raw = 0L
        repeat(length) { i ->
            val index = offsetBytes + if (littleEndian) length - 1 - i else i
            raw = (raw shl 8) or data[index].toLong()
        }
        val bits = length * 8
        if (signed && raw and (1L shl (bits - 1)) != 0L) raw -= 1L shl bits
        return (raw.toDouble() * numerator / denominator + offset).takeIf { it.isFinite() && it in minimum..maximum }
    }
}

internal val virtualPidsById = virtualPidCatalog.associateBy { it.id }

internal class VirtualEngineSupport(private val blocks: Map<Int, Long>) {
    fun permits(code: Int): Boolean {
        val base = (code - 1) / 32 * 32
        val mask = blocks[base]
        return if (mask != null) {
            mask and (1L shl (32 - (code - base))) != 0L
        } else {
            // An unreported block is unknown unless an earlier valid block ruled it out.
            blocks.none { (priorBase, priorMask) -> priorBase < base && priorMask and 1L == 0L }
        }
    }
}

internal suspend fun discoverVirtualEngineSupport(exchange: suspend (String) -> String): VirtualEngineSupport {
    val lastPid = virtualPidCatalog.filter { it.source == VirtualSource.Engine }.maxOf { it.code }
    val blocks = mutableMapOf<Int, Long>()
    for (base in 0 until lastPid step 32) {
        val response = exchange(String.format(Locale.ROOT, "01%02X", base))
        val prefix = listOf(0x41, base)
        val payload = virtualPayloads(response, VirtualSource.Engine)
            .filter { it.take(2) == prefix }.singleOrNull()
        if (payload?.size != 6) break
        val mask = payload.drop(2).fold(0L) { value, byte -> (value shl 8) or byte.toLong() }
        blocks[base] = mask
        if (mask and 1L == 0L) break
    }
    return VirtualEngineSupport(blocks)
}

internal fun virtualReadingChoices(support: VirtualEngineSupport?): Set<String> = virtualPidCatalog.filter { pid ->
    pid.source.available && (pid.source != VirtualSource.Engine || support?.permits(pid.code) != false)
}.mapTo(mutableSetOf()) { it.id }

internal data class VirtualSample(val value: Double, val observedAtMs: Long) {
    fun fresh(pid: VirtualPid, now: Long) = (now - observedAtMs) in 0 until pid.freshnessMs
}

/** Parse headered ISO-TP, retaining only complete, unambiguous payloads from the selected ECU. */
private fun virtualPayloads(response: String, source: VirtualSource): List<List<Int>> {
    val payloads = mutableListOf<List<Int>>()
    var remaining = 0
    var sequence = 1
    var partial = mutableListOf<Int>()
    for (raw in response.lineSequence()) {
        val tokens = raw.trim().uppercase(Locale.ROOT).split(Regex("\\s+"))
        if (tokens.first() != source.responder) continue
        if (tokens.size !in 3..9) return emptyList()
        val frame = tokens.drop(1).map {
            if (it.length != 2 || it.any { char -> char !in "0123456789ABCDEF" }) return emptyList()
            it.toInt(16)
        }
        when (frame.first() shr 4) {
            0 -> {
                val length = frame.first()
                if (remaining != 0 || length !in 1 until frame.size) return emptyList()
                payloads += frame.drop(1).take(length)
            }

            1 -> {
                val length = ((frame.first() and 0xf) shl 8) or frame[1]
                if (remaining != 0 || frame.size != 8 || length !in 8..64) return emptyList()
                partial = frame.drop(2).toMutableList()
                remaining = length - partial.size
                sequence = 1
            }

            2 -> {
                if (remaining == 0 || frame.first() and 0xf != sequence) return emptyList()
                val received = frame.drop(1).take(remaining)
                if (received.size < remaining && frame.size != 8) return emptyList()
                partial.addAll(received)
                remaining -= received.size
                sequence = (sequence + 1) and 0xf
                if (remaining == 0) payloads += partial.toList()
            }

            else -> return emptyList()
        }
    }
    return if (remaining == 0) payloads.distinct() else emptyList()
}

internal fun decodeVirtualReply(response: String, pid: VirtualPid): Double? {
    val prefix = pid.prefix
    val payload = virtualPayloads(response, pid.source).filter { it.take(prefix.size) == prefix }.singleOrNull()
        ?: return null
    val data = payload.drop(prefix.size)
    if (data.size < pid.minPayloadBytes) return null
    return pid.decoder.decode(data)?.takeIf { !pid.gear || it in gearPositions }
}

internal fun matchesVirtualTransmission(calibration: String, verification: String): Boolean {
    val source = VirtualSource.Transmission
    val cal = virtualPayloads(calibration, source).singleOrNull() ?: return false
    val cvn = virtualPayloads(verification, source).singleOrNull() ?: return false
    val expectedCal = listOf(0x49, 4, 1) + "68274867AE".map(Char::code)
    return cal.size == 19 && cal.take(expectedCal.size) == expectedCal &&
        cal.drop(expectedCal.size).all { it == 0 || it == 32 } &&
        cvn == listOf(0x49, 6, 1, 0x82, 0x40, 0xda, 0xe8)
}

/** Assemble only the current request, including fragmented notifications. */
internal class ElmPrompt {
    private val buffer = StringBuilder()
    private var complete = false

    fun append(bytes: ByteArray): String? {
        for (byte in bytes) {
            val character = byte.toInt().toChar()
            if (complete) {
                check(character in " \r\n\t") { "Extra adapter response" }
            } else if (character == '>') {
                complete = true
            } else {
                check(buffer.length < 2_048 && (character in ' '..'~' || character in "\r\n\t")) {
                    "Malformed adapter response"
                }
                buffer.append(character)
            }
        }
        return if (complete) buffer.toString() else null
    }
}

internal fun nextVirtualPid(
    pids: Set<VirtualPid>,
    visibleIds: Set<String>,
    dueAt: Map<VirtualPid, Long>,
    now: Long
): VirtualPid? = pids.filter { (dueAt[it] ?: 0L) <= now }
    .minWithOrNull(compareBy<VirtualPid> { dueAt[it] ?: 0L }.thenBy { it.id !in visibleIds })
