package com.lstepnio.egauge

import com.lstepnio.egauge.connection.*
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OptimizationPolicyTest {
    @Test fun anOffWarningLampDoesNotProveAnUnavailableFaultCategoryWasChecked() {
        val packet = javaClass.getResourceAsStream("/diagnostics-tcm-v15.hex")!!.bufferedReader()
            .readText().trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val snapshot = GaugeProtocolCodec.diagnostics(packet).copy(milFresh = true, milOn = false, milAgeMs = 0)
        val failed = snapshot.copy(categories = snapshot.categories!!.map {
            it.copy(availability = DiagnosticAvailability.Unavailable, fresh = false)
        })
        assertEquals(StatusTone.Stale, diagnosticSources(failed, 0, "TCM").last().status.tone)
        assertTrue(diagnosticSources(snapshot.copy(categories = null), 0, "TCM").last().categories.all { it.incomplete })
    }
    @Test fun onlineFailuresDoNotBlameTheGaugeOrHideItsSavedState() {
        assertEquals("Online updates unavailable", friendlyFailure("Online update check timed out", true).title)
        assertEquals("Online updates unavailable", friendlyFailure("Firmware download timed out", true).title)
        assertEquals("Update service is busy", friendlyFailure("GitHub update check was rate limited", true).title)
    }
    @Test fun updateTrustFailuresCannotBePresentedAsOrdinaryInternetLoss() {
        for (reason in listOf("GitHub redirected to an untrusted host",
            "Firmware bundle URL is not trusted", "Downloaded firmware bundle failed catalog verification",
            "Development update signature is invalid")) {
            val status = friendlyFailure(reason, true)
            assertEquals("This update could not be verified", status.title)
            assertEquals(StatusTone.Error, status.tone)
        }
        assertEquals("This update could not be verified", friendlyFailure(
            "GitHub redirected to an untrusted host").title)
        assertEquals("No compatible update available", friendlyFailure(
            "No compatible development firmware is published for this gauge", true).title)
    }
    @Test fun failingChildCannotHideAWarningOrBorrowItsParentsHealthyFaultState() {
        val healthy = FaultSourceUi("Engine", StatusUi("Engine warning is off", tone = StatusTone.Success), emptyList())
        val unavailable = FaultSourceUi("Transmission", StatusUi("Warning unavailable", tone = StatusTone.Stale), emptyList())
        val warning = unavailable.copy(status = StatusUi("Transmission warning", tone = StatusTone.Critical))
        assertEquals(unavailable.status, vehicleFaultStatus(listOf(healthy, unavailable)))
        assertEquals(warning.status, vehicleFaultStatus(listOf(healthy, warning)))
        assertEquals(warning.status, vehicleFaultStatus(listOf(warning, unavailable)))
    }
    @Test fun independentRetryJitterHasABoundedFloorAndCeiling() {
        for (failures in 1..20) {
            val base = reconnectDelayMs(failures)
            assertEquals((base * .8).toLong(), jitteredRetryDelay(base, 0.0))
            assertEquals(base, jitteredRetryDelay(base, 1.0))
            assertTrue(jitteredRetryDelay(base, .5) in 1_000..30_000)
        }
    }
    @Test fun adapterRetryTicksDoNotRequireRepeatedProtectedGaugeReads() {
        val gauge = VehiclePollSchedule { it }
        var gaugeReads = 0
        val fastAdapterTicks = (0L until 60_000L step 2_000).toList()
        for (now in fastAdapterTicks) if (gauge.due("same-gauge", now)) {
            gaugeReads++; gauge.completed(now, true)
        }
        assertEquals(30, fastAdapterTicks.size)
        assertEquals(3, gaugeReads)
        gauge.reset(); assertTrue(gauge.due("same-gauge", 60_001))
        gauge.completed(60_001, true)
        assertTrue(gauge.due("another-gauge", 60_002))
    }
    @Test fun unexpectedServiceFailureDoesNotTerminateForegroundRecovery() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var attempts = 0; var recorded = 0
        val controller = ForegroundConnectionController(scope, OperationCoordinator(), { recorded++ }) {
            attempts++
            if (attempts == 1) throw IOExceptionForTest()
            20_000L
        }
        try {
            controller.setForeground(true)
            withTimeout(1_000) { while (recorded == 0) delay(2) }
            delay(20); assertEquals(1, attempts)
            controller.retrySoon()
            withTimeout(1_000) { while (attempts < 2) delay(2) }
            assertEquals(1, recorded)
        } finally { scope.cancel() }
    }
    private class IOExceptionForTest : Exception("Platform service restarted")
}
