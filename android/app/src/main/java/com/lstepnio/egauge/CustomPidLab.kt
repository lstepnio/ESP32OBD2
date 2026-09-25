package com.lstepnio.egauge

sealed interface ReadRequestPreview {
    data class Valid(val request: String, val responsePrefix: String, val description: String) : ReadRequestPreview
    data class Invalid(val reason: String) : ReadRequestPreview
}

/** Syntax preview only. It never issues a vehicle command or claims PID support. */
fun previewReadRequest(raw: String): ReadRequestPreview {
    val compact = raw.filterNot(Char::isWhitespace).uppercase()
    if (compact.length !in 4..6 || compact.length % 2 != 0 ||
        compact.any { it !in '0'..'9' && it !in 'A'..'F' }) {
        return ReadRequestPreview.Invalid("Enter two or three hex bytes, such as 01 0C or 22 F1 90.")
    }
    val bytes = compact.chunked(2).map { it.toInt(16) }
    val description = when (bytes[0]) {
        0x01 -> if (bytes.size == 2) "Current data" else null
        0x09 -> if (bytes.size == 2) "Vehicle information" else null
        0x22 -> if (bytes.size == 3) "Manufacturer read" else null
        else -> null
    } ?: return ReadRequestPreview.Invalid("This lab accepts read requests 01, 09, or 22 only.")
    val request = bytes.joinToString(" ") { "%02X".format(it) }
    val response = (listOf(bytes[0] + 0x40) + bytes.drop(1))
        .joinToString(" ") { "%02X".format(it) }
    return ReadRequestPreview.Valid(request, response, description)
}
