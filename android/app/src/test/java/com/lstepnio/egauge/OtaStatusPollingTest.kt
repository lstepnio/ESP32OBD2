package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class OtaStatusPollingTest {
    @Test fun earlyPollsRetainTheExistingMaximumWaitBudget() {
        for (legacyAttempts in listOf(1, 80, 600)) {
            val delays = (0 until otaStatusPollCount(legacyAttempts)).map(::otaStatusPollDelay)
            assertEquals(legacyAttempts * 75L, delays.sum())
            assertTrue(delays.all { it in 1..75 })
            assertTrue(delays.zipWithNext().all { (a, b) -> a <= b })
            assertTrue(delays.first() < 75L)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun noUnboundedPolling() {
        otaStatusPollCount(0)
    }
}
