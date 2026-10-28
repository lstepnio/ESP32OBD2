package com.lstepnio.egauge

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EnabledAlertReleaseGateTest {
    @Test fun disabledDevelopmentRuleIsRetainedWithoutBecomingExecutable() {
        assertFalse(BuildConfig.DEBUG)
        val dormant = defaultAlert("tcmtemp").copy(enabled = false)
        val draft = Draft(alerts = listOf(dormant))
        val profiles = ProfileCollection("vehicle", listOf(VehicleProfile("vehicle", "Car", draft)))
        assertEquals(profiles, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(profiles)))
        assertTrue(ConfigurationProjector.blockers(draft).isEmpty())
        val bytes = ConfigurationProjector.project(File("src/main/assets/numeric_config_template.json").readText(), draft, "vehicle", 1, schemaVersion = 2).second
        val wire = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(0, wire.getJSONArray("alerts").length())
        assertFalse(wire.toString().contains("transmission.temperature.experimental"))
        assertTrue(ConfigurationProjector.blockers(draft.copy(alerts = listOf(dormant.copy(enabled = true)))).isNotEmpty())
    }
}
