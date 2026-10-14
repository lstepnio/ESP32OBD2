package com.lstepnio.egauge

/** One authenticated, read-only status reset repairs a cached long response on
 * older firmware. It never changes configuration, pairing, faults or firmware. */
internal fun ownerReadResetCommand(gattStatus: Int, alreadyReset: Boolean): ByteArray? =
    if (gattStatus == 7 && !alreadyReset) byteArrayOf(0x17, 1, 0, 0, 0) else null
