package com.lstepnio.egauge

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

class ConfigurationEditPolicyTest {
    private val template = File("src/main/assets/numeric_config_template.json").readText()
    private val engine = AdapterBinding("engine", "AA:BB:CC:DD:EE:01", "public")
    private val vehicle = VehicleProfile("vehicle", "Car", Draft(), engine)
    private fun document(profile: VehicleProfile, both: Boolean = false): GaugeConfigTransferClient.ActiveDocument {
        val bytes = ConfigurationProjector.projectVehicle(template, profile, 1, both).second
        return GaugeConfigTransferClient.ActiveDocument(
            2,
            "a".repeat(64),
            bytes.size,
            profile.id,
            0,
            profile.dashboardDraft().pages.size,
            profile.dashboardDraft().alerts.count { it.enabled },
            bytes.toString(Charsets.UTF_8)
        )
    }

    @Test fun cleanSetupAndPhoneOnlyDisabledEditsCanUseQuickSave() {
        val running = document(vehicle)
        assertTrue(ConfigurationEditPolicy.matchesRunning(running, vehicle, false, vehicle.draft.actions))
        val dormant = vehicle.copy(draft = vehicle.draft.copy(alerts = vehicle.draft.alerts + defaultAlert("fuel").copy(enabled = false)))
        assertTrue(ConfigurationEditPolicy.matchesRunning(running, dormant, false, dormant.draft.actions))
    }

    @Test fun stagedAdapterFilterAndPageChangesMustKeepFullReview() {
        val running = document(vehicle)
        assertFalse(ConfigurationEditPolicy.matchesRunning(running, vehicle.copy(primaryAdapter = engine.copy(address = "AA:BB:CC:DD:EE:02")), false, emptyList()))
        assertFalse(ConfigurationEditPolicy.matchesRunning(running, vehicle.copy(draft = vehicle.draft.copy(diagnosticFilters = listOf(DiagnosticCodeFilters.rule("ECM", "P0300")))), false, emptyList()))
        assertFalse(ConfigurationEditPolicy.matchesRunning(running, vehicle.copy(draft = vehicle.draft.copy(pages = vehicle.draft.pages.reversed())), false, emptyList()))
    }

    @Category(DevelopmentConfiguration::class)
    @Test
    fun childBindingAndDualModeArePartOfTheSameReviewBoundary() {
        val profile = vehicle.copy(transmission = TransmissionConnection(engine.copy(id = "transmission", address = "AA:BB:CC:DD:EE:02")))
        val running = document(profile, true)
        assertTrue(ConfigurationEditPolicy.matchesRunning(running, profile, true, profile.dashboardDraft().actions))
        assertFalse(ConfigurationEditPolicy.matchesRunning(running, profile, false, profile.dashboardDraft().actions))
        val changed = profile.copy(transmission = profile.transmission!!.copy(adapter = engine.copy(id = "transmission", address = "AA:BB:CC:DD:EE:03")))
        assertFalse(ConfigurationEditPolicy.matchesRunning(running, changed, true, changed.dashboardDraft().actions))
    }
}
