package com.lstepnio.egauge

data class Observed<T>(
    val gaugeId: String,
    val sessionGeneration: Long,
    val receivedAtElapsedMs: Long,
    val value: T,
) {
    fun isFresh(nowElapsedMs: Long, maximumAgeMs: Long): Boolean =
        nowElapsedMs >= receivedAtElapsedMs && nowElapsedMs - receivedAtElapsedMs <= maximumAgeMs
}

