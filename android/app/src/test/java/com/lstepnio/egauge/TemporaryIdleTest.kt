package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.sameSettings
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

class TemporaryIdleTest {
    private val presets = listOf(IdlePreset(IdlePresetId.AirUp, 1200), IdlePreset(IdlePresetId.Winch, 1300, 60))

    @Test fun presetsRoundtripWithoutInventingAControllerOrDefaultRpm() {
        assertNull(IdlePreset(IdlePresetId.AirUp).rpm)
        val profile = VehicleProfile("jeep", "Jeep", Draft(idlePresets = presets))
        val document = ProfileDocumentCodec.encode(ProfileCollection("jeep", listOf(profile)))
        assertEquals(19, JSONObject(document).getInt("schemaVersion"))
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

    @Category(DevelopmentConfiguration::class)
    @Test
    fun dualAdapterDashboardKeepsEnginePresetsOnTheParent() {
        val child = TransmissionConnection(AdapterBinding("tcm", "AA:BB:CC:DD:EE:02", "public"), TransmissionSetup.draft())
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(), AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"), child)
        val updated = vehicle.withDashboard(vehicle.dashboardDraft().copy(idlePresets = presets))
        assertEquals(presets, updated.draft.idlePresets)
        assertTrue(updated.transmission!!.draft.idlePresets.isEmpty())
        assertEquals(presets, updated.dashboardDraft().idlePresets)
        val encoded = ProfileDocumentCodec.encode(ProfileCollection("jeep", listOf(updated)))
        assertEquals(updated, ProfileDocumentCodec.decode(encoded).active)
    }

    @Test fun numericDraftsUseDifferentIntentLabelsWithSharedStrictStorageBounds() {
        assertEquals("Target RPM", IdleTargetDraft.label(IdlePresetId.AirUp))
        assertEquals("Minimum RPM", IdleTargetDraft.label(IdlePresetId.Winch))
        assertTrue(IdleTargetDraft.valid(""))
        listOf("400", "1200", "3000").forEach { assertEquals(it.toInt(), IdleTargetDraft.parse(it)) }
        listOf("399", "3001", "1200.0", "-1200", "+1200", " 1200", "1e3", "999999999999").forEach {
            assertFalse(it, IdleTargetDraft.valid(it))
        }
        listOf(400, 3000).forEach { rpm ->
            val presets = IdlePresetId.entries.map { IdlePreset(it, rpm) }
            assertEquals(presets, IdlePresets.decode(IdlePresets.json(presets)))
        }
        val legacy = JSONObject(ProfileDocumentCodec.encode(ProfileCollection("jeep", listOf(VehicleProfile("jeep", "Jeep", Draft(idlePresets = presets)))))).put("schemaVersion", 14)
        assertEquals(presets, ProfileDocumentCodec.decode(legacy.toString()).active.draft.idlePresets)
    }

    @Test fun numericActionPageCannotLoseItsTargetInFixedOnlyGaugeProjection() {
        val template = File("src/main/assets/numeric_config_template.json").readText()
        for (id in IdlePresetId.entries) {
            val page = if (id == IdlePresetId.AirUp) ActionPageId.AirUp else ActionPageId.Winch
            val draft = Draft(idlePresets = listOf(IdlePreset(id, 1200)), actions = listOf(PageAction(type = PageActionType.VehicleActions, pages = listOf(page))))
            val vehicle = VehicleProfile("jeep", "Jeep", draft)
            assertTrue(ConfigurationProjector.transferBlockers(draft).any { it.contains("numeric RPM") })
            // Preparing an unsupported target cannot make local alert Save silently fail.
            val added = draft.copy(alerts = listOf(defaultAlert("coolant")))
            val edited = added.copy(alerts = listOf(added.alerts.single().copy(warning = 105.0, critical = 115.0)))
            for (local in listOf(added, edited)) {
                assertTrue(ConfigurationProjector.blockers(local).isEmpty())
                val saved = ProfileCollection("jeep", listOf(vehicle.copy(draft = local)))
                assertEquals(saved, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(saved)))
                assertTrue(ConfigurationProjector.transferBlockers(local).any { it.contains("numeric RPM") })
            }
            assertTrue(runCatching { ConfigurationProjector.projectVehicle(template, vehicle, 7, false) }.isFailure)
            // A gauge override is resolved before admission; it cannot bypass the draft.
            assertTrue(runCatching { ConfigurationProjector.projectVehicle(template, vehicle.copy(draft = draft.copy(actions = emptyList())), 7, false, actions = draft.actions) }.isFailure)
            // A saved draft without a matching enabled page still permits unrelated setup.
            val unrelated = listOf(PageAction(type = PageActionType.VehicleActions, pages = listOf(ActionPageId.AbsTcs)))
            assertTrue(runCatching { ConfigurationProjector.projectVehicle(template, vehicle, 7, false, actions = unrelated) }.isSuccess)
            assertTrue(runCatching { ConfigurationProjector.projectVehicle(template, vehicle.copy(draft = draft.copy(idlePresets = listOf(IdlePreset(id)))), 7, false) }.isSuccess)
        }
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
