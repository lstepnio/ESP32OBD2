package com.lstepnio.egauge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TransmissionSetupTest {
    @Test fun combinedSetupUsesTwoReadingsAndExistingSavedConfiguration() {
        val draft = TransmissionSetup.draft()
        assertEquals(listOf("tcmgear", "tcmtemp"), draft.pages.single().pidIds)
        assertEquals(GaugeLayout.Dual, draft.layout)
        val adapter = AdapterBinding("adapter-test", "AA:BB:CC:DD:EE:FF", "random")
        val bytes = ConfigurationProjector.project(File("src/main/assets/numeric_config_template.json").readText(),
            draft, "same-profile", 27, adapter, 2).second
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(2, json.getJSONArray("definitions").length())
        val gear = (0 until 2).map { json.getJSONArray("definitions").getJSONObject(it) }.first { it.getString("id") == "transmission.gear" }
        assertEquals("5503", gear.getJSONObject("request").getString("identifier"))
        assertEquals("gear", gear.getString("unit"))
        assertEquals(draft, GaugeDraftComparison.savedDraft(GaugeConfigTransferClient.ActiveDocument(
            28, "0".repeat(64), bytes.size, "same-profile", 2, 1, 0, json.toString()), "same-profile"))
        assertTrue(ConfigurationProjector.blockers(draft.copy(source = "ECM")).isEmpty())
        val arc = draft.copy(pages = listOf(draft.pages.single().copy(layout = GaugeLayout.Arc, pidIds = listOf("tcmgear"))))
        assertTrue(ConfigurationProjector.blockers(arc).contains("Gear uses Numeric or Dual layout"))
    }

    @Test fun upgradeIsIdempotentAndKeepsCustomPages() {
        val old = Draft(pidId = "tcmtemp", source = "TCM", pages = listOf(
            GaugePageDraft("saved-page", "TCM temp (test)", GaugeLayout.Numeric, listOf("tcmtemp"))), alerts = emptyList())
        val upgraded = TransmissionSetup.draft(old)
        assertEquals("saved-page", upgraded.pages.single().id)
        assertEquals(GaugeLayout.Dual, upgraded.pages.single().layout)
        assertEquals(upgraded, TransmissionSetup.draft(upgraded))
        val custom = old.copy(pages = listOf(old.pages.single().copy(name = "MY TEMP", layout = GaugeLayout.Arc)))
        val preserved = TransmissionSetup.draft(custom)
        assertEquals(custom.pages.first(), preserved.pages.first())
        assertEquals(listOf("tcmgear"), preserved.pages.last().pidIds)
        assertEquals("P", MeasurementUnits.displayValue("P", "", MeasurementSystem.Imperial))
        assertEquals("113", MeasurementUnits.displayValue("45", "°C", MeasurementSystem.Imperial))
    }
}
