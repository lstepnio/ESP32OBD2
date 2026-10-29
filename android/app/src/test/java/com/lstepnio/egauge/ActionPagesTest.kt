package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ActionPagesTest {
    private val legacy = """[{"type":"vehicleActions","gesture":"up","count":3,"windowMs":5000,"airUpSeconds":60,"winchSeconds":300}]"""

    @Test fun legacyMenuMigratesInOrderAndKeepsGestureAndDurations() {
        val migrated = ProfileActions.decode(JSONArray(legacy)).single()
        assertEquals(legacyActionPages, migrated.pages)
        assertEquals(3, migrated.count)
        assertEquals(60, migrated.airUpSeconds)
        assertEquals(listOf(migrated), ProfileActions.decode(ProfileActions.json(listOf(migrated))))
        val empty = migrated.copy(pages = emptyList())
        assertTrue(ProfileActions.decode(ProfileActions.json(listOf(empty))).single().pages.isEmpty())
    }

    @Test fun burnoutDraftRoundTripsWithoutChangingLegacyOrJumpDefaults() {
        val action = PageAction(type = PageActionType.VehicleActions, pages = ActionPageId.entries.toList())
        assertEquals(4, action.pages.size)
        assertEquals(action, ProfileActions.decode(ProfileActions.json(listOf(action))).single())
        assertEquals(legacyActionPages, PageAction(pageId = "main").pages)
        assertFalse(ProfileActions.supported(listOf(action), 1))
        assertFalse(ProfileActions.supported(listOf(action), null))
        assertTrue(ProfileActions.supported(listOf(action), 1, 1))
        assertTrue(ProfileActions.supported(listOf(action.copy(pages = legacyActionPages)), 1))
    }

    @Test fun optionalBurnoutCapabilityKeepsLegacyVehicleCommandsVersion() {
        val base = """{"board":"board","protocolMajor":0,"maxAdapterLinks":2,"configWrite":false,"ota":false,"vc":1}"""
        assertEquals(0, GaugeProtocolCodec.capabilities(base.toByteArray()).burnoutActionsVersion)
        val extended = base.dropLast(1) + ",\"bm\":1}"
        val capabilities = GaugeProtocolCodec.capabilities(extended.toByteArray())
        assertEquals(1, capabilities.vehicleCommandsVersion)
        assertEquals(1, capabilities.burnoutActionsVersion)
        listOf("2", "-1", "1.5", "true", "\"1\"").forEach { invalid ->
            assertTrue(runCatching { GaugeProtocolCodec.capabilities((base.dropLast(1) + ",\"bm\":$invalid}").toByteArray()) }.isFailure)
        }
    }

    @Test fun optionalBrakeSupervisorCapabilityIsStrictAndKeepsLegacyDefaults() {
        val base = """{"board":"board","protocolMajor":0,"maxAdapterLinks":2,"configWrite":false,"ota":false,"vc":1}"""
        assertEquals(0, GaugeProtocolCodec.capabilities(base.toByteArray()).brakeActionsTestVersion)
        for (version in 0..1) {
            val caps = GaugeProtocolCodec.capabilities((base.dropLast(1) + ",\"bt\":$version,\"future\":true}").toByteArray())
            assertEquals(version, caps.brakeActionsTestVersion)
            assertEquals(0, caps.burnoutActionsVersion)
            assertEquals(1, caps.vehicleCommandsVersion)
        }
        listOf("2", "-1", "1.5", "true", "null", "\"1\"").forEach { invalid ->
            assertTrue(runCatching { GaugeProtocolCodec.capabilities((base.dropLast(1) + ",\"bt\":$invalid}").toByteArray()) }.isFailure)
        }
    }

    @Test fun profileSchemaPreservesExplicitBurnoutAndRejectsUnknownPages() {
        val action = PageAction(type = PageActionType.VehicleActions, pages = listOf(ActionPageId.Burnout))
        val collection = ProfileCollection("jeep", listOf(VehicleProfile("jeep", "Jeep", Draft(actions = listOf(action)))))
        val encoded = ProfileDocumentCodec.encode(collection)
        assertEquals(19, JSONObject(encoded).getInt("schemaVersion"))
        assertEquals(listOf(action), ProfileDocumentCodec.decode(encoded).active.draft.actions)
        assertTrue(runCatching { ProfileDocumentCodec.decode(encoded.replace("burnout", "futureBrakeAction")) }.isFailure)
        assertEquals(listOf(action), ProfileDocumentCodec.decode(encoded).active.draft.actions)
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
