package com.lstepnio.egauge.virtual

import com.lstepnio.egauge.BuildConfig
import java.util.Locale

/** Separate read-only routes for one logical vehicle. */
internal enum class VirtualSource(val label: String, val responder: String) {
    Engine("Main adapter", "7E8"),
    Transmission("Transmission adapter", "7E9")
}

internal enum class VirtualPid(
    val id: String,
    val code: Int,
    val bytes: Int = 1,
    val periodMs: Long = 2_000,
    val source: VirtualSource = VirtualSource.Engine,
    val decode: (Int) -> Double
) {
    Rpm("rpm", 0x0c, 2, 500, decode = { it / 4.0 }),
    Speed("speed", 0x0d, periodMs = 500, decode = Int::toDouble),
    Coolant("coolant", 0x05, decode = { it - 40.0 }),
    Intake("intake", 0x0f, decode = { it - 40.0 }),
    Ambient("ambient", 0x46, decode = { it - 40.0 }),
    Oil("oiltemp", 0x5c, decode = { it - 40.0 }),
    Load("load", 0x04, periodMs = 750, decode = { it * 100.0 / 255.0 }),
    Throttle("throttle", 0x11, periodMs = 750, decode = { it * 100.0 / 255.0 }),
    Fuel("fuel", 0x2f, decode = { it * 100.0 / 255.0 }),
    Map("map", 0x0b, periodMs = 750, decode = Int::toDouble),
    Maf("maf", 0x10, 2, 750, decode = { it / 100.0 }),
    Voltage("voltage", 0x42, 2, decode = { it / 1_000.0 }),
    TransmissionTemp("tcmtemp", 0x04fe, 3, source = VirtualSource.Transmission, decode = { (it shr 16) - 40.0 }),
    Gear("tcmgear", 0x5503, periodMs = 500, source = VirtualSource.Transmission, decode = Int::toDouble);

    val available: Boolean get() = source == VirtualSource.Engine || BuildConfig.DEBUG
    val request: String get() = String.format(Locale.ROOT, if (source == VirtualSource.Engine) "01%02X" else "22%04X", code)
    val prefix: List<Int> get() = if (source == VirtualSource.Engine) listOf(0x41, code) else listOf(0x62, code shr 8, code and 0xff)
    val freshnessMs: Long get() = if (periodMs < 1_000) 5_000 else 10_000
}

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
    if (data.size != pid.bytes) return null
    val value = pid.decode(data.fold(0) { total, byte -> total * 256 + byte })
    return when (pid) {
        VirtualPid.Gear -> value.takeIf { it in com.lstepnio.egauge.gearPositions }
        VirtualPid.TransmissionTemp -> value.takeIf { it in 0.0..180.0 }
        else -> value
    }
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
