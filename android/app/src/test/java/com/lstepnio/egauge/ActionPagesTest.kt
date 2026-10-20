package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ActionPagesTest {
    private val legacy = """[{"type":"vehicleActions","gesture":"up","count":3,"windowMs":5000,"airUpSeconds":60,"winchSeconds":300}]"""

    @Test fun legacyMenuMigratesInOrderAndKeepsGestureAndDurations() {
        val migrated = ProfileActions.decode(JSONArray(legacy)).single()
        assertEquals(ActionPageId.entries.toList(), migrated.pages)
        assertEquals(3, migrated.count)
        assertEquals(60, migrated.airUpSeconds)
        assertEquals(listOf(migrated), ProfileActions.decode(ProfileActions.json(listOf(migrated))))
        val empty = migrated.copy(pages = emptyList())
        assertTrue(ProfileActions.decode(ProfileActions.json(listOf(empty))).single().pages.isEmpty())
    }

    @Test fun unsupportedDuplicateAndOversizedCollectionsAreRejected() {
        listOf("[\"airUp\",\"airUp\"]", "[\"rawCommand\"]", "[1]", "[\"airUp\",\"winch\",\"absTcs\",\"airUp\"]").forEach { bad ->
            val item = JSONArray(legacy).getJSONObject(0).put("pages", JSONArray(bad))
            assertTrue(runCatching { ProfileActions.decode(JSONArray().put(item)) }.isFailure)
        }
    }

    @Test fun gaugeOverridesAreIndependentAndReassignmentDoesNotLeakActions() {
        val action = ProfileActions.decode(JSONArray(legacy)).single().copy(pages = listOf(ActionPageId.AbsTcs, ActionPageId.AirUp))
        val gauges = GaugeAssociations("one", listOf(KnownGauge("one", "One", "jeep", "ECM", actionOverride = listOf(action)), KnownGauge("two", "Two", "jeep", "ECM", actionOverride = listOf(action.copy(pages = emptyList())))))
        assertEquals(gauges, GaugeAssociationDocumentCodec.decode(GaugeAssociationDocumentCodec.encode(gauges)))
        assertNull(gauges.assign("one", "other", "ECM").gauges[0].actionOverride)
        assertEquals(gauges.gauges[0].actionOverride, gauges.assign("one", "jeep", "ECM").gauges[0].actionOverride)
        val old = JSONObject(GaugeAssociationDocumentCodec.encode(GaugeAssociations("one", listOf(KnownGauge("one", "One", "jeep", "ECM"))))).put("schemaVersion", 1)
        assertNull(GaugeAssociationDocumentCodec.decode(old.toString()).gauges.single().actionOverride)
    }

    @Test fun projectedOrderUsesGaugeOverrideWithoutChangingVehicleOrOtherGauge() {
        val legacyAction = ProfileActions.decode(JSONArray(legacy)).single()
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(actions = listOf(legacyAction)))
        val override = legacyAction.copy(pages = listOf(ActionPageId.Winch), winchSeconds = 45)
        val projected = ConfigurationProjector.projectVehicle(java.io.File("src/main/assets/numeric_config_template.json").readText(), vehicle, 7, false, actions = listOf(override))
        assertEquals("winch", JSONObject(projected.second.toString(Charsets.UTF_8)).getJSONArray("actions").getJSONObject(0).getJSONArray("pages").getString(0))
        assertEquals(45, projected.first.actions.single().winchSeconds)
        assertEquals(legacyAction, vehicle.draft.actions.single())
    }

    @Test fun fractionalDurationAndForeignKeysCannotBecomeCommands() {
        val bad = JSONArray(legacy).getJSONObject(0).put("airUpSeconds", 60.5)
        assertTrue(runCatching { ProfileActions.decode(JSONArray().put(bad)) }.isFailure)
        bad.put("airUpSeconds", 60).put("packet", "31A4")
        assertTrue(runCatching { ProfileActions.decode(JSONArray().put(bad)) }.isFailure)
    }
}
