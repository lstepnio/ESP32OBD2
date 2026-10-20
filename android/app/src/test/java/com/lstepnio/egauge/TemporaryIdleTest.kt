package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.sameSettings
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TemporaryIdleTest {
    private val presets = listOf(IdlePreset(IdlePresetId.AirUp, 1200), IdlePreset(IdlePresetId.Winch, 1300, 60))

    @Test fun presetsRoundtripWithoutInventingAControllerOrDefaultRpm() {
        assertNull(IdlePreset(IdlePresetId.AirUp).rpm)
        val profile = VehicleProfile("jeep", "Jeep", Draft(idlePresets = presets))
        val document = ProfileDocumentCodec.encode(ProfileCollection("jeep", listOf(profile)))
        assertEquals(16, JSONObject(document).getInt("schemaVersion"))
        assertEquals(profile, ProfileDocumentCodec.decode(document).active)
        val old = JSONObject(document).put("schemaVersion", 13)
        old.getJSONArray("profiles").getJSONObject(0).getJSONObject("draft").remove("idlePresets")
        assertTrue(ProfileDocumentCodec.decode(old.toString()).active.draft.idlePresets.isEmpty())
    }

    @Test fun malformedAndFuturePresetDataIsRetainedByRejectingIt() {
        assertTrue(runCatching { IdlePresets.decode(JSONArray("""[{"id":"AirUp","rpm":1200.5,"seconds":60}]""")) }.isFailure)
        assertTrue(runCatching { IdlePresets.decode(JSONArray("""[{"id":"Other","rpm":1200,"seconds":60}]""")) }.isFailure)
        assertTrue(runCatching { IdlePresets.validate(listOf(presets[0], presets[0])) }.isFailure)
        assertTrue(runCatching { IdlePreset(IdlePresetId.AirUp, 3001) }.isFailure)
        assertTrue(runCatching { IdlePreset(IdlePresetId.Winch, 1200, 601) }.isFailure)
    }

    @Test fun localPresetsNeverLeakIntoLegacyGaugeConfigurationOrMarkItUnsaved() {
        val template = File("src/main/assets/numeric_config_template.json").readText()
        val original = Draft()
        val withPresets = original.copy(idlePresets = presets)
        val before = ConfigurationProjector.project(template, original, "jeep", 7, schemaVersion = 2).second
        val after = ConfigurationProjector.project(template, withPresets, "jeep", 7, schemaVersion = 2).second
        assertArrayEquals(before, after)
        assertTrue(sameSettings(original, withPresets))
    }

    @Test fun dualAdapterDashboardKeepsEnginePresetsOnTheParent() {
        val child = TransmissionConnection(AdapterBinding("tcm", "AA:BB:CC:DD:EE:02", "public"), TransmissionSetup.draft())
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(), AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"), child)
        val updated = vehicle.withDashboard(vehicle.dashboardDraft().copy(idlePresets = presets))
        assertEquals(presets, updated.draft.idlePresets)
        assertTrue(updated.transmission!!.draft.idlePresets.isEmpty())
        assertEquals(presets, updated.dashboardDraft().idlePresets)
        val encoded = ProfileDocumentCodec.encode(ProfileCollection("jeep", listOf(updated)))
        assertEquals(updated, ProfileDocumentCodec.decode(encoded).active)
    }

    @Test fun innerRequestsMatchTheCDecoderAndKeepRoleAndReservedBytesZero() {
        val expected = File("../../contracts/examples/temporary-idle-start.hex").readText().trim()
        val bytes = IdleCommandProtocol.request(1, 1, 7, 9, presets[0].copy(seconds = 10))
        assertEquals(expected, bytes.joinToString("") { "%02x".format(it.toInt() and 255) })
        assertTrue(IdleCommandProtocol.request(2, 1, 7, 9).slice(16..23).all { it == 0.toByte() })
        assertTrue(runCatching { IdleCommandProtocol.request(1, 1, 7, 9, IdlePreset(IdlePresetId.AirUp)) }.isFailure)
        assertTrue(runCatching { IdleCommandProtocol.request(2, 1, 7, 9, presets[0]) }.isFailure)
        assertTrue(runCatching { IdleCommandProtocol.request(1, 1, 0, 9, presets[0]) }.isFailure)
    }

    @Test fun presentationNeverOffersExecutionForUnqualifiedOrUnknownState() {
        assertFalse(IdleControlState().canStart)
        assertFalse(IdleControlState(IdlePhase.Unknown).canStart)
        assertFalse(IdleControlState(IdlePhase.Stopping).canStart)
        val active = IdleControlState(IdlePhase.Active, IdlePresetId.Winch, 1300, 240)
        assertTrue(active.canStop)
        assertTrue(active.message.contains("4:00"))
    }
}
