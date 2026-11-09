package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.presentationBlockers
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

class GaugeFeaturePolicyTest {
    private fun capabilities(vararg flags: Pair<String, Int>): CapabilitySnapshot {
        val wire = JSONObject("""{"board":"board","protocolMajor":0,"maxAdapterLinks":1,"configWrite":false,"ota":false,"cfg":7,"ds":3,"va":1}""")
        flags.forEach { (key, value) -> wire.put(key, value) }
        return GaugeProtocolCodec.capabilities(wire.toString().toByteArray())
    }

    @Test fun onlyAnExplicitPolicySuppressesSavedFeatures() {
        assertNull(capabilities().featurePolicy)
        val restricted = capabilities("fp" to 1)
        assertEquals(GaugeFeaturePolicy(false, false), restricted.featurePolicy)
        assertEquals(0, restricted.smartPageCyclingVersion)
        assertEquals(3, restricted.displaySettingsVersion)
        assertEquals(2, restricted.pageActionsVersion)
        assertEquals(GaugeFeaturePolicy(true, false), capabilities("fp" to 1, "da" to 1).featurePolicy)
        assertEquals(GaugeFeaturePolicy(false, true), capabilities("fp" to 1, "vc" to 1).featurePolicy)
        val privateGauge = capabilities("da" to 1, "vc" to 1, "sp" to 1)
        assertNull(privateGauge.featurePolicy)
        assertEquals(1, privateGauge.smartPageCyclingVersion)
        assertThrows(IllegalArgumentException::class.java) { capabilities("fp" to 2) }
    }

    @Test fun restrictedRuntimeUsesEngineSlotZeroForTransmissionFirstDocument() {
        val indices = linkedMapOf("TCM" to 0, "ECM" to 1)
        assertEquals(mapOf("ECM" to 0), GaugeFeaturePolicy(allowsSecondAdapter = false).sourceIndices(indices))
        assertEquals(indices, GaugeFeaturePolicy().sourceIndices(indices))
    }

    @Test fun ordinaryDashboardCanSendWhenSavedActionsAreUnavailable() {
        val action = PageAction(type = PageActionType.VehicleActions)
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(actions = listOf(action)))
        val restricted = capabilities("fp" to 1)
        val policy = requireNotNull(restricted.featurePolicy)
        val dashboard = vehicle.dashboardDraft()
        assertTrue(presentationBlockers(dashboard, capabilities()).any { "vehicle actions" in it })
        val effective = policy.project(dashboard, dashboard.actions)
        assertTrue(presentationBlockers(effective, restricted).isEmpty())
        val bytes = ConfigurationProjector.projectVehicle(
            File("src/main/assets/numeric_config_template.json").readText(),
            vehicle,
            4,
            false,
            actions = effective.actions
        ).second
        val payload = JSONObject(bytes.toString(Charsets.UTF_8))
        assertFalse(payload.has("actions"))
        assertEquals(1, payload.getJSONArray("sources").length())
        assertEquals(listOf(action), vehicle.draft.actions)

        val savedGauge = KnownGauge("gauge", "eGauge", vehicle.id, "ECM", bothAdapters = true)
        val assignments = GaugeAssociations(savedGauge.id, listOf(savedGauge))
        val profiles = ProfileCollection(vehicle.id, listOf(vehicle))
        assertNull(assignments.context(savedGauge.id, profiles))
        assertEquals(vehicle.id, assignments.context(savedGauge.id, profiles, policy.allowsSecondAdapter)?.first)
        assertTrue(assignments.gauges.single().bothAdapters)
    }

    @Category(DevelopmentConfiguration::class)
    @Test
    fun restrictedProjectionKeepsDashboardAndSavedPrivateAssignment() {
        val engine = AdapterBinding("ecm.radio", "AA:BB:CC:DD:EE:01", "public")
        val child = AdapterBinding("tcm.radio", "AA:BB:CC:DD:EE:02", "random")
        val vehicle = VehicleProfile("jeep", "Jeep", Draft(), engine, TransmissionConnection(child))
        val privateAction = PageAction(type = PageActionType.VehicleActions)
        val saved = KnownGauge("gauge", "eGauge", vehicle.id, "ECM", bothAdapters = true, actionOverride = listOf(privateAction))
        val dashboard = vehicle.dashboardDraft()
        val restricted = capabilities("fp" to 1)
        val policy = requireNotNull(restricted.featurePolicy)
        val draft = policy.project(dashboard, requireNotNull(saved.actionOverride))
        assertEquals(dashboard.pages, draft.pages)
        assertEquals(dashboard.alerts, draft.alerts)
        assertTrue(draft.actions.isEmpty())
        assertTrue(presentationBlockers(draft, restricted).isEmpty())

        val jump = PageAction(dashboard.pages.last().id)
        val jumpDraft = policy.project(dashboard, listOf(jump))
        val template = File("src/main/assets/numeric_config_template.json").readText()
        val restrictedBytes = ConfigurationProjector.projectVehicle(
            template,
            vehicle,
            4,
            policy.usesSecondAdapter(saved.bothAdapters),
            actions = jumpDraft.actions
        ).second
        val restrictedJson = JSONObject(restrictedBytes.toString(Charsets.UTF_8))
        assertEquals(1, restrictedJson.getJSONArray("sources").length())
        assertEquals(dashboard.pages.size, restrictedJson.getJSONArray("pages").length())
        assertEquals(listOf(jump), ProfileActions.decode(restrictedJson.getJSONArray("actions")))

        val privatePolicy = GaugeFeaturePolicy()
        val privateDraft = privatePolicy.project(dashboard, requireNotNull(saved.actionOverride))
        val privateBytes = ConfigurationProjector.projectVehicle(
            template,
            vehicle,
            4,
            privatePolicy.usesSecondAdapter(saved.bothAdapters),
            actions = privateDraft.actions
        ).second
        val privateJson = JSONObject(privateBytes.toString(Charsets.UTF_8))
        assertEquals(2, privateJson.getJSONArray("sources").length())
        assertEquals(listOf(privateAction), ProfileActions.decode(privateJson.getJSONArray("actions")))
        assertEquals(child, vehicle.transmission?.adapter)
    }
}
