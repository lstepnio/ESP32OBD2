package com.lstepnio.egauge

/** Fit four early reads into one legacy 75 ms pause; preserve the total wait budget. */
internal fun otaStatusPollCount(legacyAttempts: Int): Int {
    require(legacyAttempts in 1..10_000)
    return legacyAttempts + 3
}

internal fun otaStatusPollDelay(retry: Int): Long = when (retry) {
    0 -> 5L
    1 -> 10L
    2 -> 20L
    3 -> 40L
    else -> 75L
}
