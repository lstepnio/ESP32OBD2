package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class IdleCoolantTest {
    private val alert = defaultAlert("coolant")

    @Test fun existingHighWarningIsTheStopLimitWithoutASecondSetting() {
        assertEquals(105.0, idleCoolantLimitC(listOf(alert))!!, 0.0)
        assertEquals(221.0, MeasurementUnits.value(idleCoolantLimitC(listOf(alert))!!, "°C", MeasurementSystem.Imperial), 0.0)
        assertNull(idleCoolantLimitC(emptyList()))
    }

    @Test fun guardRejectionExplainsTheNextStepWithoutClaimingRestoration() {
        val status = VehicleCommandStatus(VehicleCommandKind.Idle, VehicleCommandPhase.Rejected, reason = 5)
        assertEquals("Idle cannot start. Check coolant and vehicle conditions.", status.message)
        assertEquals("Stopping raised idle. Check coolant and vehicle conditions.", status.copy(phase = VehicleCommandPhase.Stopping).message)
    }

    @Test fun unrelatedLowOrMalformedAlertsAreNotCoolantProtection() {
        listOf(alert.copy(pidId = "tcmtemp"), alert.copy(direction = AlertDirection.Below), alert.copy(direction = AlertDirection.Equals), alert.copy(warning = Double.NaN), alert.copy(critical = Double.POSITIVE_INFINITY), alert.copy(warning = 120.0), alert.copy(warning = -40.0), alert.copy(critical = 216.0)).forEach {
            assertNull(idleCoolantLimitC(listOf(it)))
        }
        assertEquals(100.0, idleCoolantLimitC(listOf(alert, alert.copy(warning = 100.0)))!!, 0.0)
    }
}
