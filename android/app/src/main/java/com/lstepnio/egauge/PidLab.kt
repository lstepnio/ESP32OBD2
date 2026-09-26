package com.lstepnio.egauge

sealed interface DecodeResult {
    data class Value(val display: String) : DecodeResult
    data class Error(val message: String) : DecodeResult
}

/** Local Mode 01 examples only. No adapter traffic or manufacturer definitions. */
fun decodeExample(pid: PidExample, input: String): DecodeResult {
    if (pid.request == "Vehicle specific")
        return DecodeResult.Error("A vehicle-specific definition is required for this TCM signal")
    val clean = input.filterNot(Char::isWhitespace)
    if (clean.length !in 6..128 || clean.length % 2 != 0 || !clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })
        return DecodeResult.Error("Enter an even number of hex digits, up to 64 bytes")
    val bytes = clean.chunked(2).map { it.toInt(16) }
    val expectedPid = pid.request.substringAfter(' ').toInt(16)
    if (bytes[0] != 0x41 || bytes[1] != expectedPid)
        return DecodeResult.Error("Response must start with 41 ${pid.request.substringAfter(' ')}")
    val payload = bytes.drop(2)
    val value = when (pid.id) {
        "rpm" -> if (payload.size == 2) ((payload[0] shl 8) or payload[1]) / 4 else null
        "coolant" -> if (payload.size == 1) payload[0] - 40 else null
        "speed" -> if (payload.size == 1) payload[0] else null
        "load", "fuel" -> if (payload.size == 1) payload[0] * 100 / 255 else null
        else -> null
    } ?: return DecodeResult.Error("Payload length or decoder is unsupported for this example")
    return DecodeResult.Value("$value ${pid.unit}")
}
