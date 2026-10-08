package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.presentationBlockers
import com.lstepnio.egauge.ui.state.sameSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProfileActionsTest {
    private val initial = Draft()
    private val action = PageAction(initial.pages.first().id)
    private fun template() = File("src/main/assets/numeric_config_template.json").readText()
    @Test fun profileRoundtripAndLegacyMigrationPreserveOtherSettings() {
        val profile = VehicleProfile("jeep", "Jeep", initial.copy(actions = listOf(action)))
        val value = ProfileCollection(profile.id, listOf(profile))
        assertEquals(value, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(value)))
        val legacy = JSONObject(ProfileDocumentCodec.encode(value)).put("schemaVersion", 6)
        legacy.getJSONArray("profiles").getJSONObject(0).getJSONObject("draft").remove("actions")
        assertEquals(initial, ProfileDocumentCodec.decode(legacy.toString()).active.draft)
    }
    @Test fun projectionReviewAndReadbackPreserveTheExactBinding() {
        val draft = initial.copy(actions = listOf(action))
        val (review, bytes) = ConfigurationProjector.project(template(), draft, "jeep", 29, schemaVersion = 2)
        assertEquals(listOf(action), review.actions)
        assertTrue(review.reviewLines().any { "swipes up" in it })
        val json = bytes.toString(Charsets.UTF_8)
        assertEquals(listOf(action), ProfileActions.decode(JSONObject(json).getJSONArray("actions")))
        val document = GaugeConfigTransferClient.ActiveDocument(30,"",bytes.size,"jeep",0,initial.pages.size,initial.alerts.size,json)
        assertEquals(draft, GaugeDraftComparison.savedDraft(document,"jeep"))
        assertFalse(sameSettings(initial,draft))
        assertTrue(GaugeDraftComparison.from(document,"jeep",draft).fields.single { it.label == "Gesture actions" }.matches == true)
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationProjector.project(template(),draft,"jeep",29)
        }
    }
    @Test fun invalidAndUnknownVehicleActionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PageAction(action.pageId,1) }
        assertThrows(IllegalArgumentException::class.java) { PageAction(action.pageId,3,10001) }
        assertThrows(IllegalArgumentException::class.java) { PageAction(action.pageId,3,2500) }
        assertThrows(IllegalArgumentException::class.java) { ProfileActions.validate(listOf(action,action),initial.pages) }
        assertThrows(IllegalArgumentException::class.java) { ProfileActions.validate(listOf(PageAction("missing")),initial.pages) }
        for (bad in listOf(action.json().put("type","disableAbs"), action.json().put("count",3.5),
            action.json().put("gesture","down"),action.json().put("windowMs","5000"),action.json().put("rawCommand","3105"))) {
            assertThrows(IllegalArgumentException::class.java) { ProfileActions.decode(JSONArray().put(bad)) }
        }
    }
    @Test fun dualChildActionTargetsArePrefixedAndConflictingBindingsFail() {
        val engine = AdapterBinding("ecm","AA:BB:CC:DD:EE:01","public")
        val child = AdapterBinding("tcm","AA:BB:CC:DD:EE:02","public")
        val tcm = TransmissionSetup.draft()
        val vehicle = VehicleProfile("jeep","Jeep",initial,engine,
            TransmissionConnection(child,tcm.copy(actions=listOf(PageAction(tcm.pages.first().id)))))
        val combined = vehicle.combinedDraft()
        assertTrue(combined.actions.single().pageId.startsWith("child."))
        val root = JSONObject(ConfigurationProjector.projectCombined(template(),vehicle,29).second.toString(Charsets.UTF_8))
        assertEquals(combined.actions,ProfileActions.decode(root.getJSONArray("actions")))
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurationProjector.projectCombined(template(),vehicle.copy(draft=initial.copy(actions=listOf(action))),29)
        }
    }
    @Test fun combinedPickerRoutesEitherTargetAndClearsThePreviousBinding() {
        val ecm = AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public")
        val tcm = AdapterBinding("tcm", "AA:BB:CC:DD:EE:02", "public")
        val vehicle = VehicleProfile("jeep", "Jeep", initial, ecm, TransmissionConnection(tcm))
        val target = "child." + vehicle.transmission!!.draft.pages.first().id
        val childAction = vehicle.withCombinedPageAction(PageAction(target))
        assertTrue(childAction.draft.actions.isEmpty())
        assertEquals(target, childAction.combinedDraft().actions.single().pageId)
        val engineAction = childAction.withCombinedPageAction(action)
        assertEquals(listOf(action), engineAction.draft.actions)
        assertTrue(engineAction.transmission!!.draft.actions.isEmpty())
        assertTrue(engineAction.withCombinedPageAction(null).combinedDraft().actions.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { vehicle.withCombinedPageAction(PageAction("missing")) }
        assertEquals(vehicle, vehicle.withCombinedPageAction(null))
    }

    @Test fun developmentConfigVersionControlsAvailability() {
        val base = JSONObject("""{"protocolMajor":0,"board":"test","maxAdapterLinks":1,"configWrite":false,"ota":false,"cfg":3}""")
        val old = GaugeProtocolCodec.capabilities(base.toString().toByteArray())
        assertEquals(0,old.pageActionsVersion)
        assertTrue(presentationBlockers(initial.copy(actions=listOf(action)),old).any { "gesture" in it })
        val current = GaugeProtocolCodec.capabilities(base.put("cfg",4).toString().toByteArray())
        assertEquals(1,current.pageActionsVersion)
        assertEquals(GaugeLayout.entries.toSet(),current.supportedRenderers)
        assertFalse(presentationBlockers(initial.copy(actions=listOf(action)),current).any { "gesture" in it })
    }
}
