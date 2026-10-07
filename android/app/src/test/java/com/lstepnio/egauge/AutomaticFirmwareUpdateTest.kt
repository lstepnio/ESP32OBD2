package com.lstepnio.egauge

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticFirmwareUpdateTest {
    @Test fun onlyANewerUnheldReleaseCanBecomeReady() {
        assertEquals(AutomaticUpdateDecision.READY,
            automaticUpdateDecision("0.2.0-dev.23", 2, "0.2.0-dev.24", "new", null, false))
        assertEquals(AutomaticUpdateDecision.CURRENT,
            automaticUpdateDecision("0.2.0-dev.24", 2, "0.2.0-dev.24", "same", null, false))
        assertEquals(AutomaticUpdateDecision.CURRENT,
            automaticUpdateDecision("0.2.0-dev.24", 2, "0.2.0-dev.23", "older", null, false))
        assertEquals(AutomaticUpdateDecision.HELD,
            automaticUpdateDecision("0.2.0-dev.23", 2, "0.2.0-dev.24", "failed", "failed", false))
        assertEquals(AutomaticUpdateDecision.READY,
            automaticUpdateDecision("0.2.0-dev.23", 2, "0.2.0-dev.25", "newer", "failed", false))
    }

    @Test fun aTrialOrUnknownOutcomeNeverPromptsAnInstall() {
        assertEquals(AutomaticUpdateDecision.WAIT_FOR_GAUGE,
            automaticUpdateDecision("0.2.0-dev.23", 1, "0.2.0-dev.24", "new", null, false))
        assertEquals(AutomaticUpdateDecision.WAIT_FOR_GAUGE,
            automaticUpdateDecision("0.2.0-dev.23", 2, "0.2.0-dev.24", "new", null, true))
    }
}
