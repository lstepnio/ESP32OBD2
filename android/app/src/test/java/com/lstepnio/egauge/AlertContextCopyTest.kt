package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.alertContextReading
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertContextCopyTest {
    @Test fun contextUsesTheSelectedUnitSystem() {
        assertEquals("212 °F", alertContextReading(100.0, "°C", MeasurementSystem.Imperial))
        assertEquals("100 °C", alertContextReading(100.0, "°C", MeasurementSystem.Metric))
        assertEquals("100 °C", alertContextReading(100.0, "degC", MeasurementSystem.Metric))
        assertEquals("212 °F", alertContextReading(100.0, "degC", MeasurementSystem.Imperial))
    }

    @Test fun gearIsANameAndMissingValuesStayUnavailable() {
        assertEquals(
            gearPositions[0.0] ?: "Unavailable",
            alertContextReading(0.0, "gear", MeasurementSystem.Metric)
        )
        assertEquals("Unavailable", alertContextReading(123.0, "gear", MeasurementSystem.Metric))
        assertEquals(
            "Unavailable",
            alertContextReading(Double.NaN, "°C", MeasurementSystem.Imperial)
        )
    }
}
