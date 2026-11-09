package com.lstepnio.egauge.virtual

import java.util.Locale

/** Read-only Mode 01 requests for the first direct-adapter profile. */
internal enum class VirtualPid(
    val id: String,
    val code: Int,
    val bytes: Int = 1,
    val periodMs: Long = 2_000,
    val decode: (Int) -> Double
) {
    Rpm("rpm", 0x0c, 2, 500, { it / 4.0 }),
    Speed("speed", 0x0d, periodMs = 500, decode = Int::toDouble),
    Coolant("coolant", 0x05, decode = { it - 40.0 }),
    Intake("intake", 0x0f, decode = { it - 40.0 }),
    Ambient("ambient", 0x46, decode = { it - 40.0 }),
    Oil("oiltemp", 0x5c, decode = { it - 40.0 }),
    Load("load", 0x04, periodMs = 750, decode = { it * 100.0 / 255.0 }),
    Throttle("throttle", 0x11, periodMs = 750, decode = { it * 100.0 / 255.0 }),
    Fuel("fuel", 0x2f, decode = { it * 100.0 / 255.0 }),
    Map("map", 0x0b, periodMs = 750, decode = Int::toDouble),
    Maf("maf", 0x10, 2, 750, { it / 100.0 }),
    Voltage("voltage", 0x42, 2, decode = { it / 1_000.0 });

    val request: String get() = String.format(Locale.ROOT, "01%02X", code)
    val freshnessMs: Long get() = if (periodMs < 1_000) 5_000 else 10_000
}

internal data class VirtualSample(val value: Double, val observedAtMs: Long) {
    fun fresh(pid: VirtualPid, now: Long) = (now - observedAtMs) in 0 until pid.freshnessMs
}

/** One complete ISO-TP single frame from the primary 11-bit CAN ECU. Reject conflicts. */
internal fun decodeVirtualReply(response: String, pid: VirtualPid): Double? {
    val values = response.lineSequence().mapNotNull { raw ->
        val line = raw.trim().uppercase(Locale.ROOT).split(Regex("\\s+"))
        if (line.size !in (4 + pid.bytes)..9 || line.first() != "7E8") return@mapNotNull null
        val data = line.drop(1).map {
            if (it.length != 2 || it.any { char -> char !in "0123456789ABCDEF" }) return@mapNotNull null
            it.toIntOrNull(16)?.takeIf { byte -> byte in 0..255 } ?: return@mapNotNull null
        }
        if (data[0] != 2 + pid.bytes || data[1] != 0x41 || data[2] != pid.code) {
            return@mapNotNull null
        }
        pid.decode(data.drop(3).take(pid.bytes).fold(0) { value, byte -> value * 256 + byte })
    }.distinct().toList()
    return values.singleOrNull()
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
