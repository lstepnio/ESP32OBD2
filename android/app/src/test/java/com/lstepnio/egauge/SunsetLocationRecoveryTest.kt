package com.lstepnio.egauge

import com.lstepnio.egauge.ui.settings.firstSunsetFix
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SunsetLocationRecoveryTest {
    @Test fun missingPrimaryDoesNotDiscardTheNetworkFix() = runBlocking {
        val actual = firstSunsetFix(listOf("fused", "network")) { provider ->
            if (provider == "network") SunsetLocation(3970, -10500) else null
        }
        assertEquals(SunsetLocation(3970, -10500), actual)
        assertNull(firstSunsetFix<String>(listOf("fused", "network")) { null })
    }

    @Test fun firstFixCancelsTheSlowProvider() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val actual = firstSunsetFix(listOf("fused", "network")) { provider ->
            if (provider == "fused") {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    stopped.complete(Unit)
                }
            } else {
                started.await()
                "network"
            }
        }
        assertEquals("network", actual)
        assertTrue(stopped.isCompleted)
    }
}
