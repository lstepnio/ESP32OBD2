package com.lstepnio.egauge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GaugeProfileRecoveryTest {
    private val engine = VehicleProfile("jeep", "Jeep", Draft(), AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"))
    private val original = ProfileCollection(engine.id, listOf(engine))
    private fun document(): GaugeConfigTransferClient.ActiveDocument {
        val draft = TransmissionSetup.draft().let { it.copy(actions = listOf(PageAction(it.pages.first().id))) }
        val bytes = ConfigurationProjector.project(File("src/main/assets/numeric_config_template.json").readText(),
            draft, "missing.tcm", 31, AdapterBinding("tcm", "AA:BB:CC:DD:EE:02", "public"), 2).second
        return GaugeConfigTransferClient.ActiveDocument(32, "a".repeat(64), bytes.size, "missing.tcm", 0, draft.pages.size, 0, bytes.toString(Charsets.UTF_8))
    }
    @Test fun recoveryRetainsEngineAndRestoresSourcePagesBindingAndAction() {
        val saved = document()
        val result = GaugeProfileRecovery.recover(original, saved)
        assertEquals(engine, result.profiles.first())
        assertEquals("missing.tcm", result.activeId)
        assertEquals("TCM", result.active.draft.source)
        assertEquals(GaugeDraftComparison.savedDraft(saved, saved.vehicleProfileId), result.active.draft)
        assertEquals("AA:BB:CC:DD:EE:02", result.active.primaryAdapter?.address)
        assertNull(result.active.transmission)
        assertEquals(result, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(result)))
    }
    @Test fun combinedRecoveryRetainsOneVehicleWithItsChildAndFullDashboard() {
        val vehicle = engine.copy(id = "missing.jeep", transmission = TransmissionConnection(
            AdapterBinding("child", "AA:BB:CC:DD:EE:02", "public")))
        val bytes = ConfigurationProjector.projectCombined(File("src/main/assets/numeric_config_template.json").readText(), vehicle, 42).second
        val saved = GaugeConfigTransferClient.ActiveDocument(43, "a".repeat(64), bytes.size, vehicle.id, 0, 0, 0, bytes.toString(Charsets.UTF_8))
        val recovered = GaugeProfileRecovery.recover(original, saved).active
        assertEquals(vehicle.primaryAdapter, recovered.primaryAdapter)
        assertEquals(vehicle.transmission!!.adapter, recovered.transmission!!.adapter)
        assertEquals(vehicle.dashboardDraft().pages, recovered.dashboardDraft().pages)
        assertEquals(engine, original.active)
        val restored = GaugeProfileRecovery.restore(vehicle.copy(name = "Custom Jeep"), saved)
        assertEquals("Custom Jeep", restored.name)
        assertEquals(recovered.dashboardDraft().pages, restored.dashboardDraft().pages)
        assertEquals(recovered.transmission?.adapter, restored.transmission?.adapter)
        assertThrows(IllegalArgumentException::class.java) { GaugeProfileRecovery.restore(engine, saved) }
    }

    @Test fun existingIdentityAndFullCollectionAreNeverOverwritten() {
        val recovered = GaugeProfileRecovery.recover(original, document())
        assertThrows(IllegalArgumentException::class.java) { GaugeProfileRecovery.recover(recovered, document()) }
        val full = original.copy(profiles = (1..8).map { engine.copy(id = "car.$it") }, activeId = "car.1")
        assertThrows(IllegalArgumentException::class.java) { GaugeProfileRecovery.recover(full, document()) }
    }
    @Test fun missingBindingUnknownReadingAndDualSourcesFailWithoutMutation() {
        val saved = document()
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("sources").getJSONObject(0).remove("adapter") },
            { it.getJSONArray("pages").getJSONObject(0).getJSONArray("pidIds").put(0, "unknown") },
            { it.getJSONArray("sources").put(JSONObject(it.getJSONArray("sources").getJSONObject(0).toString())) })) {
            val root = JSONObject(saved.json); mutate(root)
            assertThrows(Exception::class.java) { GaugeProfileRecovery.recover(original, saved.copy(json = root.toString())) }
            assertEquals(listOf(engine), original.profiles)
        }
    }
}
