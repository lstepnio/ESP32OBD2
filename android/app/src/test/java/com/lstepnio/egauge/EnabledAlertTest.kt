package com.lstepnio.egauge

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EnabledAlertTest {
    private val template = File("src/main/assets/numeric_config_template.json").readText()
    private val disabled = defaultAlert("fuel").copy(enabled = false, triggerDwellMs = 4321, clearDwellMs = 1234)
    private val draft = Draft(alerts = listOf(defaultAlert(), disabled))
    private fun collection(value: Draft = draft) = ProfileCollection("vehicle", listOf(VehicleProfile("vehicle", "Car", value)))
    private fun document(value: Draft): GaugeConfigTransferClient.ActiveDocument {
        val bytes = ConfigurationProjector.project(template, value, "vehicle", 1, AdapterBinding("engine", "AA:BB:CC:DD:EE:01", "public"), 2).second
        return GaugeConfigTransferClient.ActiveDocument(2, "a".repeat(64), bytes.size, "vehicle", 0, value.pages.size, value.alerts.count { it.enabled }, bytes.toString(Charsets.UTF_8))
    }

    @Test fun enablementRoundTripsWithAllDisabledRuleSettings() {
        val encoded = ProfileDocumentCodec.encode(collection())
        assertEquals(19, JSONObject(encoded).getInt("schemaVersion"))
        assertEquals(collection(), ProfileDocumentCodec.decode(encoded))
        assertFalse(ProfileDocumentCodec.decode(encoded).active.draft.alerts.last().enabled)
    }

    @Test fun legacyProfilesDefaultEnabledAndRejectDisabledRulesUnderOlderVersion() {
        val legacy = JSONObject(ProfileDocumentCodec.encode(collection()))
        legacy.put("schemaVersion", 18)
        assertThrows(IllegalArgumentException::class.java) { ProfileDocumentCodec.decode(legacy.toString()) }
        val alerts = legacy.getJSONArray("profiles").getJSONObject(0).getJSONObject("draft").getJSONArray("alerts")
        for (index in 0 until alerts.length()) alerts.getJSONObject(index).remove("enabled")
        assertTrue(ProfileDocumentCodec.decode(legacy.toString()).active.draft.alerts.all { it.enabled })
        assertEquals(draft.alerts.map { it.copy(enabled = true) }, ProfileDocumentCodec.decode(legacy.toString()).active.draft.alerts)
    }

    @Test fun malformedCurrentEnablementCannotSilentlyBecomeEnabled() {
        val root = JSONObject(ProfileDocumentCodec.encode(collection()))
        val alert = root.getJSONArray("profiles").getJSONObject(0).getJSONObject("draft").getJSONArray("alerts").getJSONObject(0)
        alert.put("enabled", "false")
        assertThrows(IllegalArgumentException::class.java) { ProfileDocumentCodec.decode(root.toString()) }
        root.put("schemaVersion", 20)
        assertThrows(IllegalArgumentException::class.java) { ProfileDocumentCodec.decode(root.toString()) }
    }

    @Test fun disabledRuleDoesNotReachWireDefinitionsReviewOrGaugeComparison() {
        val (review, bytes) = ConfigurationProjector.project(template, draft, "vehicle", 1, schemaVersion = 2)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(listOf(defaultAlert().id), review.alerts.map { it.id })
        assertEquals(1, root.getJSONArray("alerts").length())
        assertFalse(root.toString().contains("vehicle.fuel"))
        assertFalse(root.getJSONArray("alerts").getJSONObject(0).has("enabled"))
        val comparison = GaugeDraftComparison.from(document(draft), "vehicle", draft, AdapterBinding("engine", "AA:BB:CC:DD:EE:01", "public"))
        assertTrue(comparison.fields.filter { it.label in setOf("Alert count", "All alert settings") }.all { it.matches == true })
        assertEquals(2, draft.alerts.size)
        assertFalse(draft.alerts.last().enabled)
        val restored = GaugeDraftComparison.savedDraft(document(draft), "vehicle")!!
        assertEquals(listOf(defaultAlert()), restored.alerts)
        assertEquals(2, JSONObject(ConfigurationProjector.project(template, draft.copy(alerts = draft.alerts.map { it.copy(enabled = true) }), "vehicle", 1, schemaVersion = 2).second.toString(Charsets.UTF_8)).getJSONArray("alerts").length())
    }

    @Test fun disabledRulesStillRequireValidLimitsAndUniqueIdentity() {
        assertTrue(ConfigurationProjector.blockers(draft.copy(alerts = listOf(disabled.copy(warning = Double.NaN)))).isNotEmpty())
        assertTrue(ConfigurationProjector.blockers(draft.copy(alerts = listOf(disabled, disabled))).isNotEmpty())
    }

    @Test fun restoringGaugeSetupPreservesNonconflictingDisabledPhoneRules() {
        val existing = collection().active
        val result = GaugeProfileRecovery.restore(existing, document(draft))
        assertEquals(draft.alerts, result.draft.alerts)
        assertEquals(existing.name, result.name)
        val conflict = existing.copy(draft = draft.copy(alerts = listOf(defaultAlert().copy(enabled = false))))
        assertEquals(listOf(defaultAlert()), GaugeProfileRecovery.restore(conflict, document(draft)).draft.alerts)
    }

    @Test fun disabledCatalogReadingDoesNotDemandNewFirmwareCapabilities() {
        val dormant = Draft(alerts = listOf(defaultAlert("tcmgear").copy(enabled = false)))
        assertFalse(requiresVehicleDashboardFirmware(dormant, false))
        assertFalse(requiresPidCatalogFirmware(dormant))
        assertTrue(ConfigurationProjector.blockers(dormant).isEmpty())
    }
}
