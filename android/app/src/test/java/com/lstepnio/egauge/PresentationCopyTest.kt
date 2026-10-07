package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.*
import com.lstepnio.egauge.core.designsystem.StatusTone
import org.junit.Assert.*
import org.junit.Test

class PresentationCopyTest {
    private val digest = "a".repeat(64)
    private fun runtime(revision: Long = 2, hash: String = digest, trial: Boolean = false,
                        previous: Boolean = false, running: Boolean = true) =
        GaugeConfigTransferClient.RuntimeIdentity(running, previous, trial, revision, 2, hash)

    @Test fun successRequiresExactHealthyRuntimeIdentity() {
        assertTrue(isConfirmedSetup(2, digest, runtime()))
        listOf(runtime(trial = true), runtime(previous = true), runtime(running = false),
            runtime(revision = 1), runtime(hash = "b".repeat(64))).forEach {
            assertFalse(isConfirmedSetup(2, digest, it))
        }
        assertFalse(isConfirmedSetup(null, digest, runtime()))
        assertFalse(isConfirmedSetup(2, null, runtime()))
        assertFalse(isConfirmedSetup(2, digest, null))
    }

    @Test fun storedOrTransferredIsNeverPresentedAsRunning() {
        listOf(OperationStage.SAVED, OperationStage.VERIFYING, OperationStage.RESTARTING,
            OperationStage.CHECKING_RUNNING).forEach { stage ->
            val ui = operationUi(OperationState(1, OperationKind.CONFIGURATION, stage, "raw", progressPercent = 100))
            assertNotEquals(StatusTone.Success, ui.status.tone)
            assertTrue(ui.busy)
        }
        val unproven = operationUi(OperationState(1, OperationKind.CONFIGURATION, OperationStage.ACTIVE, "raw", terminal = true))
        assertNotEquals(StatusTone.Success, unproven.status.tone)
    }

    @Test fun failuresAndUnknownOutcomesStayVisibleWithNextStep() {
        listOf(OperationStage.RECOVERED, OperationStage.FAILED, OperationStage.OUTCOME_UNKNOWN).forEach { stage ->
            val ui = operationUi(OperationState(7, OperationKind.CONFIGURATION, stage, "raw", "GATT error 0x10", terminal = true))
            assertTrue(ui.visible); assertTrue(ui.needsCheck); assertFalse(ui.busy)
            assertFalse(ui.status.detail.contains("GATT"))
            assertNotEquals(StatusTone.Success, ui.status.tone)
        }
    }

    @Test fun ordinaryCopyDoesNotLeakProtocolDetails() {
        val forbidden = Regex("(?i)sha-?256|revision|\\bECU\\b|\\bPID\\b|0x[0-9a-f]|catalog|projection|document|evidence|\\bdraft\\b")
        OperationStage.entries.forEach { stage ->
            val ui = operationUi(OperationState(1, OperationKind.CONFIGURATION, stage, "Revision 7", "SHA-256 0xab"))
            assertFalse("Jargon in $stage", forbidden.containsMatchIn(ui.status.title + ui.status.detail))
        }
    }

    @Test fun updateStepperDoesNotReachDoneWhileCheckingTheRestart() {
        listOf(OperationStage.SENDING, OperationStage.VERIFYING, OperationStage.SAVED,
            OperationStage.RESTARTING, OperationStage.CHECKING_RUNNING).forEach { stage ->
            val ui = operationUi(OperationState(1, OperationKind.UPDATE, stage, "raw", progressPercent = 100))
            assertTrue("Done must wait for running proof: $stage", ui.step < 3)
        }
        assertEquals(3, operationUi(OperationState(1, OperationKind.UPDATE, OperationStage.ACTIVE,
            "raw", terminal = true)).step)
    }

    @Test fun pendingAndRolledBackUpdatesNeverClaimSuccess() {
        UpdateRecoveryState.entries.filter { it != UpdateRecoveryState.INSTALLED }.forEach {
            assertNotEquals(StatusTone.Success, updateRecoveryUi(UpdateRecoveryResult(it, "raw", false)).tone)
        }
    }

    @Test fun unavailableReleaseFeedIsNotReportedAsAFailedGaugeUpdate() {
        val status = friendlyFailure(HOSTED_RELEASE_FEED_UNAVAILABLE, update = true)
        assertEquals("Online updates unavailable", status.title)
        assertTrue(status.detail.contains("Choose a signed package"))
        assertEquals(StatusTone.Stale, status.tone)
    }

    @Test fun onlyConfirmedTransferSuccessCanDismissItsBanner() {
        val saved = operationUi(OperationState(1, OperationKind.CONFIGURATION, OperationStage.ACTIVE,
            "raw", terminal = true), confirmedSetup = true)
        val installed = operationUi(OperationState(2, OperationKind.UPDATE, OperationStage.ACTIVE,
            "raw", terminal = true))
        assertTrue(successBannerMayDismiss(saved))
        assertTrue(successBannerMayDismiss(installed))
        val unsafe = listOf(
            operationUi(OperationState(3, OperationKind.CONFIGURATION, OperationStage.ACTIVE, "raw", terminal = true)),
            operationUi(OperationState(4, OperationKind.CONFIGURATION, OperationStage.SENDING, "raw")),
            operationUi(OperationState(5, OperationKind.UPDATE, OperationStage.CHECKING_RUNNING, "raw")),
            operationUi(OperationState(6, OperationKind.UPDATE, OperationStage.OUTCOME_UNKNOWN, "raw", terminal = true)),
            operationUi(OperationState(7, OperationKind.CONFIGURATION, OperationStage.RECOVERED, "raw", terminal = true)),
            operationUi(OperationState(8, OperationKind.CONFIGURATION, OperationStage.FAILED, "raw", terminal = true)),
        )
        unsafe.forEach { assertFalse(successBannerMayDismiss(it)) }
    }
}
