package com.lstepnio.egauge

import com.lstepnio.egauge.ui.customize.AlertForm
import com.lstepnio.egauge.ui.state.alertUi
import org.junit.Assert.*
import org.junit.Test

class AlertFormTest {
    @Test fun legacyRpmCeilingRemainsReadableButMustBeCorrectedBeforeSending() {
        val draft = Draft(alerts = listOf(defaultAlert("rpm").copy(critical = 16384.0)))
        val stored = ProfileCollection("default", listOf(VehicleProfile("default", "My vehicle", draft)))
        val restored = ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(stored))
        assertEquals(stored, restored)
        assertTrue(ConfigurationProjector.blockers(restored.active.draft).any { "outside its supported range" in it })
        val corrected = AlertForm.from(alertUi(restored.active.draft.alerts.single())).copy(critical = "16383")
        assertNotNull(corrected.saved("rpm", readingRange("rpm"), alertUi(draft.alerts.single())))
    }
    @Test fun rpmWholeNumberBoundsNeverExceedTheActualDecoderRange() {
        val definitions = org.json.JSONObject(java.io.File("src/main/assets/numeric_config_template.json").readText())
            .getJSONArray("definitions")
        val rpm = (0 until definitions.length()).map { definitions.getJSONObject(it) }.first { it.getString("id") == "engine.rpm" }
        assertTrue(readingRange("rpm").last <= rpm.getJSONObject("range").getDouble("max"))
        assertNull(AlertForm(warning = "16000", critical = "16384").saved("rpm", readingRange("rpm"), null))
    }
    @Test fun newAlertHasNoInventedLimitsAndCannotSave() {
        assertNull(AlertForm().saved("rpm", readingRange("rpm"), null))
    }

    @Test fun rpmLimitsUseFullReadingRangeAndPreserveExistingBehavior() {
        val original = defaultAlert("rpm").copy(priority = 12, triggerDwellMs = 1250, clearDwellMs = 2250)
        val form = AlertForm.from(alertUi(original)).copy(warning = "6000", critical = "7000")
        assertEquals(original.copy(warning = 6000.0, critical = 7000.0), form.saved("rpm", readingRange("rpm"), alertUi(original)))
    }

    @Test fun lowFuelAndNegativeTemperaturesAreValidWithoutClamping() {
        val fuel = AlertForm(AlertDirection.Below, "20", "10")
        assertNotNull(fuel.saved("fuel", readingRange("fuel"), null))
        val cold = AlertForm(AlertDirection.Below, "0", "-10")
        assertEquals(-10.0, cold.saved("coolant", readingRange("coolant"), null)!!.critical, 1e-9)
    }

    @Test fun invalidLimitsAndDirectionChangesMustBeResolvedBeforeSaving() {
        listOf(AlertForm(warning = "Infinity", critical = "90"), AlertForm(warning = "80.5", critical = "90"),
            AlertForm(warning = "80", critical = "101"), AlertForm(warning = "80", critical = "80"),
            AlertForm(AlertDirection.Below, "80", "90"), AlertForm(warning = "80", critical = "90", reset = "10"),
            AlertForm(warning = "80", critical = "90", trigger = "60.001"),
            AlertForm(warning = "80", critical = "90", clear = "0.0001")
        ).forEach { assertNull(it.saved("fuel", readingRange("fuel"), null)) }
    }

    @Test fun formValidationAgreesWithConfigurationValidationForEveryAvailableReading() {
        ConfigurationProjector.supportedPidIds.forEach { id ->
            val range = readingRange(id)
            AlertDirection.entries.filter { it != AlertDirection.Equals }.forEach { direction ->
                val form = AlertForm(direction,
                    (if (direction == AlertDirection.Above) range.first else range.last).toString(),
                    (if (direction == AlertDirection.Above) range.last else range.first).toString())
                val alert = requireNotNull(form.saved(id, range, null))
                assertTrue(ConfigurationProjector.blockers(Draft(alerts = listOf(alert))).isEmpty())
            }
        }
    }
}
