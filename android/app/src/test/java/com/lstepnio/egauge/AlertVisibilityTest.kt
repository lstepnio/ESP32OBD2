package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AlertVisibilityTest {
    private val rules = listOf(DiagnosticCodeFilters.rule("TCM", "P0700"))
    private fun event(key: Int = 35) = AlertEvent(
        1, 1, 1, 1, key, AlertSeverity.Warning, false,
        AlertLifecycle.Active, 1, false, 2f, 0f, if (key < 32) "Coolant" else "P0700 vehicle fault", "codes", 1, 99
    )
    private fun context(vararg codes: String, count: Boolean = true): JSONObject {
        val snapshot = JSONObject().put("milOn", true).put(
            "categories",
            JSONArray().apply {
                listOf("Stored", "Pending", "Permanent").forEachIndexed { index, name ->
                    put(
                        JSONObject().put("name", name).put("availability", "Available")
                            .put("fresh", true).put("truncated", false)
                            .put("codes", JSONArray(if (index == 0) codes.toList() else emptyList<String>()))
                    )
                }
            }
        )
        if (count) snapshot.put("reportedCount", codes.size)
        return JSONObject().put("diagnostics", JSONObject().put("TCM", snapshot))
            .put("entryEvent", alertJson(event()))
            .put("configuration", JSONObject().put("diagnosticFilters", DiagnosticCodeFilters.json(rules)))
    }
    private fun entry(context: JSONObject, key: Int = 35) = StoredAlert("episode", "vehicle-a", "gauge", 1, false, event(key), context)

    @Test fun hiddenOnlyHistoryNeedsNoLiveSnapshotAndRestoresWithoutMutatingEvidence() {
        val raw = entry(context("P0700"))
        val original = raw.context.toString()
        assertNull(AlertVisibility.project(raw, rules))
        assertNotNull(AlertVisibility.project(raw, emptyList()))
        assertEquals(original, raw.context.toString())
        assertEquals("P0700 vehicle fault", raw.event.label)
    }

    @Test fun mixedEventKeepsRemainderAndRebuildsCountInNestedEvidence() {
        val raw = entry(context("P0700", "P0730"))
        val projected = AlertVisibility.project(raw, rules)!!
        assertEquals(1f, projected.event.value)
        assertFalse(projected.event.label.contains("P0700"))
        assertFalse(projected.context.toString().contains("P0700"))
        assertTrue(projected.context.toString().contains("P0730"))
        assertFalse(projected.context.has("configuration"))
        assertEquals(1, projected.context.getJSONObject("diagnostics").getJSONObject("TCM").getInt("reportedCount"))
        assertEquals(2, raw.context.getJSONObject("diagnostics").getJSONObject("TCM").getInt("reportedCount"))
    }

    @Test fun thresholdSurvivesHiddenIncidentalContextAndOtherControllerIsPreserved() {
        val raw = entry(context("P0700"), key = 0)
        raw.context.getJSONObject("diagnostics").put("ECM", JSONObject(raw.context.getJSONObject("diagnostics").getJSONObject("TCM").toString()))
        val projected = AlertVisibility.project(raw, rules)!!
        assertEquals(raw.event, projected.event)
        assertTrue(projected.context.getJSONObject("diagnostics").getJSONObject("ECM").toString().contains("P0700"))
        assertFalse(projected.context.getJSONObject("diagnostics").getJSONObject("TCM").toString().contains("P0700"))
        assertFalse(projected.context.getJSONObject("diagnostics").getJSONObject("TCM").has("milOn"))
        assertTrue(projected.context.getJSONObject("diagnostics").getJSONObject("ECM").getBoolean("milOn"))
    }

    @Test fun oldUnattributableMilRemainsGenericAndPartialEvidenceCannotProveOmission() {
        val old = entry(context("P0700", count = false), key = 33)
        val projected = AlertVisibility.project(old, rules)!!
        assertFalse(projected.context.toString().contains("P0700"))
        val partial = entry(context("P0700"))
        partial.context.getJSONObject("diagnostics").getJSONObject("TCM").getJSONArray("categories")
            .getJSONObject(0).put("truncated", true)
        assertNotNull(AlertVisibility.project(partial, rules))
        assertNull(AlertVisibility.project(entry(context("P0700"), key = 33), rules))
        val unknown = AlertVisibility.project(entry(JSONObject(), key = 33), rules)!!
        assertTrue(unknown.event.value.isNaN())
        assertEquals("Transmission warning", unknown.event.label)
        assertFalse(alertJson(unknown.event).toString().contains("NaN"))
        assertTrue(alertFromJson(alertJson(unknown.event)).value.isNaN())
    }

    @Test fun milAttributionRequiresExactStoredCountAndAllowsUnsupportedCategories() {
        val raw = entry(context("P0700"), key = 33)
        val snapshot = raw.context.getJSONObject("diagnostics").getJSONObject("TCM")
        snapshot.getJSONArray("categories").getJSONObject(1)
            .put("availability", "Unsupported").put("fresh", false)
        assertNull(AlertVisibility.project(raw, rules))
        snapshot.put("reportedCount", 2)
        val mismatch = AlertVisibility.project(raw, rules)!!
        assertNotNull(AlertVisibility.project(raw.copy(event = raw.event.copy(key = 35)), rules))
        assertTrue(mismatch.event.value.isNaN())
        assertTrue(mismatch.context.getJSONObject("diagnostics").getJSONObject("TCM").getBoolean("milOn"))
        assertFalse(alertJson(mismatch.event).toString().contains("P0700"))
    }

    @Test fun unscopedProviderTextCannotBypassHiddenContextPolicy() {
        val raw = entry(context("P0700"), key = 40)
        val projected = AlertVisibility.project(raw, rules)!!
        assertFalse(projected.event.label.contains("P0700"))
        assertFalse(projected.context.toString().contains("P0700"))
    }

    @Test fun scopeIsResolvedPerVehicleAndTransitionUsesSameProjection() {
        val raw = entry(context("P0700"))
        val saved = mapOf("vehicle-a" to rules)
        assertNull(AlertVisibility.project(raw, saved[raw.vehicle].orEmpty()))
        assertNotNull(AlertVisibility.project(raw.copy(vehicle = "vehicle-b"), saved["vehicle-b"].orEmpty()))
        assertNull(AlertVisibility.transition(raw.event.copy(lifecycle = AlertLifecycle.Resolved), raw.context, rules))
        val operation = JSONObject().put("diagnostics", raw.context.getJSONObject("diagnostics"))
        assertFalse(AlertVisibility.context(operation, rules).toString().contains("P0700"))
    }
}
