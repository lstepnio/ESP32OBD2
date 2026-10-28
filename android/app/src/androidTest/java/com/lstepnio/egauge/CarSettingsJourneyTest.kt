package com.lstepnio.egauge

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual durable model saves on a disposable emulator, with no Bluetooth or ECU writes. */
class CarSettingsJourneyTest {
    @get:Rule val compose = activityTestRule()

    @Test fun savedOfflineFiltersAndDisabledAlertsUseVehicleIntentAndKeepRawEvidence() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val model = (compose.activity.application as EGaugeApplication).model
        val before = model.profileCollection
        val repository = AlertRepository(compose.activity)
        compose.runOnUiThread {
            model.editProfileName("Car policy fixture")
            model.createProfile()
        }
        compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.active.name == "Car policy fixture" }
        val vehicle = model.profileCollection.activeId
        val gauge = "policy-$vehicle"
        try {
            val event = AlertEvent(1, 9, 1, 1000, 35, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 1f, 0f, "TCM faults", "codes", 1000, 1)
            val diagnostic = JSONObject().put(
                "TCM",
                JSONObject().put("milOn", true).put("reportedCount", 1)
                    .put(
                        "categories",
                        JSONArray().apply {
                            listOf("Stored", "Pending", "Permanent").forEachIndexed { index, name ->
                                put(
                                    JSONObject().put("name", name).put("availability", "Available").put("fresh", true)
                                        .put("truncated", false).put("codes", JSONArray(if (index == 0) listOf("U0121") else emptyList<String>()))
                                )
                            }
                        }
                    )
            )
            repository.ingest(
                vehicle,
                gauge,
                AlertBatch(9, 1, 1, 0, 1, false, true, false, listOf(event)),
                """{"baseRevision":0,"definitions":[]}""",
                diagnostic.toString(),
                System.currentTimeMillis()
            )
            compose.runOnUiThread { model.refreshAlertHistory() }
            compose.waitUntil(5000) { model.alertHistory.any { it.id == event.id(gauge) } }
            val raw = repository.list(vehicle).single().context.toString()
            assertEquals(1, model.visibleAlertHistory().size)
            compose.runOnUiThread { model.addDiagnosticFilter(vehicle, "TCM", "u0121") }
            compose.waitUntil(10_000) { !model.localSaveBusy && model.editorDraft.diagnosticFilters.size == 1 }
            assertTrue(model.visibleAlertHistory().isEmpty())
            assertEquals(raw, repository.list(vehicle).single().context.toString())
            assertEquals(model.profileCollection, ProfileStore(compose.activity).load().collection)
            val disabled = defaultAlert("coolant").copy(enabled = false)
            var saved = false
            compose.runOnUiThread { model.saveCarAlert(disabled) { saved = true } }
            compose.waitUntil(10_000) { !model.localSaveBusy && saved && model.editorDraft.alerts.any { it == disabled } }
            assertNull(model.profileError)
            assertEquals(model.profileCollection, ProfileStore(compose.activity).load().collection)
            assertFalse(model.canApplyCarEdit)
            assertNull(idleCoolantLimitC(model.editorDraft.alerts))
            compose.runOnUiThread { model.choosePrimaryAdapter(AdapterBinding("fixture-engine", "AA:BB:CC:DD:EE:01", "public")) }
            compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.active.primaryAdapter != null }
            compose.runOnUiThread { model.addTransmissionChild() }
            compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.active.transmission != null }
            val transmission = defaultAlert("tcmtemp").copy(triggerDwellMs = 4321)
            saved = false
            compose.runOnUiThread { model.saveCarAlert(transmission) { saved = true } }
            compose.waitUntil(10_000) { !model.localSaveBusy && saved }
            assertEquals(transmission, model.editorDraft.alerts.first { it.pidId == "tcmtemp" }.copy(id = transmission.id))
            compose.runOnUiThread { model.removeDiagnosticFilter(vehicle, DiagnosticCodeFilters.rule("TCM", "U0121")) }
            compose.waitUntil(10_000) { !model.localSaveBusy && model.editorDraft.diagnosticFilters.isEmpty() }
            assertEquals(1, model.visibleAlertHistory().size)
            assertEquals(raw, repository.list(vehicle).single().context.toString())
        } finally {
            compose.runOnUiThread { model.selectProfile(before.activeId) }
            compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.activeId == before.activeId }
            compose.runOnUiThread { model.deleteVehicle(vehicle) }
            compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.profiles.none { it.id == vehicle } }
            assertEquals(before, model.profileCollection)
            repository.close()
        }
    }
}
