package com.lstepnio.egauge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticUpdateHoldStoreTest {
    @Test fun aFailedReleaseIsHeldOnlyForItsGaugeAndCanBeClearedAfterConfirmation() {
        val store = AutomaticUpdateHoldStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val first = "test-gauge-first"
        val second = "test-gauge-second"
        try {
            store.hold(first, "a".repeat(64))
            store.hold(second, "b".repeat(64))
            assertEquals("a".repeat(64), store.digestFor(first))
            assertEquals("b".repeat(64), store.digestFor(second))
            store.clear(first)
            assertNull(store.digestFor(first))
            assertEquals("b".repeat(64), store.digestFor(second))
        } finally {
            store.clear(first)
            store.clear(second)
        }
    }
}
