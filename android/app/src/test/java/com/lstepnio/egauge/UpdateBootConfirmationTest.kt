package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class UpdateBootConfirmationTest {
    private val old = GaugeConfigTransferClient.BootIdentity(2, 16, 0, "a".repeat(64), "0.2.0-dev.33", 0x60000)
    private val trial = old.copy(otaState = 1, partitionAddress = 0x360000, elfSha256 = "b".repeat(64))
    @Test fun oldHealthyImageBeforeRebootDoesNotLookLikeARollback() {
        assertEquals(UpdateBootDecision.WAIT, updateBootDecision(old, old, trial.elfSha256, false, false))
        assertEquals(UpdateBootDecision.WAIT, updateBootDecision(old, null, trial.elfSha256, false, true))
        assertEquals(UpdateBootDecision.WAIT, updateBootDecision(old, trial, trial.elfSha256, false, false))
        assertEquals(UpdateBootDecision.CONFIRMED, updateBootDecision(old, trial.copy(otaState=2), trial.elfSha256, true, false))
    }
    @Test fun confirmedRollbackAndExpiredOldImageAreReported() {
        assertEquals(UpdateBootDecision.PREVIOUS_IMAGE, updateBootDecision(old, old, trial.elfSha256, true, false))
        assertEquals(UpdateBootDecision.PREVIOUS_IMAGE, updateBootDecision(old, old, trial.elfSha256, false, true))
        assertEquals(UpdateBootDecision.WAIT, updateBootDecision(old, trial.copy(otaState=2,elfSha256="c".repeat(64)), trial.elfSha256, true, true))
    }
}
