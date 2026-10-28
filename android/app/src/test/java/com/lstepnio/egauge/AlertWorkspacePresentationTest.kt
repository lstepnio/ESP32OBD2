package com.lstepnio.egauge

import com.lstepnio.egauge.ui.alertWorkspaceEntries
import com.lstepnio.egauge.ui.alertWorkspaceRows
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertWorkspacePresentationTest {
    private fun entry(id: String, severity: AlertSeverity, recordedAt: Long, lifecycle: AlertLifecycle = AlertLifecycle.Active) = StoredAlert(
        id,
        "car",
        "gauge",
        recordedAt,
        true,
        AlertEvent(1, 1, 1, 0, 0, severity, false, lifecycle, 1, false, 100f, 90f, "Coolant", "degC", 0, 1),
        JSONObject()
    )

    @Test fun currentKeepsAcknowledgedProblemsAndStableOrderWithinSeverity() {
        val first = entry("first", AlertSeverity.Warning, 1)
        val second = entry("second", AlertSeverity.Warning, 2).let { it.copy(event = it.event.copy(acknowledged = true)) }
        val critical = entry("critical", AlertSeverity.Critical, 3)
        val resolved = entry("resolved", AlertSeverity.Critical, 4, AlertLifecycle.Resolved)
        val disconnected = entry("disconnected", AlertSeverity.Critical, 5)
        val result = alertWorkspaceEntries(listOf(first, second, critical, resolved, disconnected), false) { it.id != "disconnected" }
        assertEquals(listOf("critical", "first", "second"), result.map { it.id })
    }

    @Test fun historyKeepsDisconnectedEvidenceAndOrdersByRecordedTime() {
        val old = entry("old", AlertSeverity.Critical, 1)
        val recent = entry("recent", AlertSeverity.Warning, 2, AlertLifecycle.Resolved)
        assertEquals(listOf("recent", "old"), alertWorkspaceEntries(listOf(old, recent), true) { false }.map { it.id })
    }

    private fun diagnostic(id: String, key: Int, at: Long = 1, code: String = "P0700"): StoredAlert {
        val source = if (key % 2 == 1) "TCM" else "ECM"
        val context = JSONObject().put(
            "diagnostics",
            JSONObject().put(
                source,
                JSONObject().put(
                    "categories",
                    JSONArray().put(
                        JSONObject().put("name", "Stored").put("codes", JSONArray().put(code))
                    ).put(JSONObject().put("name", "Pending").put("codes", JSONArray().put(code)))
                )
            )
        )
        return entry(id, AlertSeverity.Warning, at).let { it.copy(event = it.event.copy(key = key, unit = "codes"), context = context) }
    }

    @Test fun diagnosticRowsDecodeMeaningAndDeduplicateCategoriesAndCurrentMilEpisodes() {
        val mil = diagnostic("mil", 33, code = "U0140")
        val codes = diagnostic("codes", 35, code = "U0140")
        val original = mil.context.toString()
        val rows = alertWorkspaceRows(listOf(mil, codes), false) { true }
        assertEquals(1, rows.size)
        assertEquals("Body communication lost", rows.single().title)
        assertEquals("U0140", rows.single().fault!!.code)
        assertEquals("Transmission", rows.single().fault!!.source)
        assertEquals(listOf("Stored", "Pending"), rows.single().fault!!.categoryNames)
        assertEquals(original, mil.context.toString())
    }

    @Test fun currentCodeDeduplicationRetainsVehicleGaugeAndControllerScopes() {
        val engine = diagnostic("engine", 34)
        val transmission = diagnostic("transmission", 35)
        val otherVehicle = engine.copy(id = "other-car", vehicle = "other")
        val otherGauge = engine.copy(id = "other-gauge", gauge = "independent")
        val rows = alertWorkspaceRows(listOf(engine, transmission, otherVehicle, otherGauge), false) { true }
        assertEquals(4, rows.size)
        assertEquals(listOf("Engine", "Transmission", "Engine", "Engine"), rows.map { it.fault!!.source })
        assertEquals(listOf("car", "car", "other", "car"), rows.map { it.entry.vehicle })
        assertEquals(listOf("gauge", "gauge", "gauge", "independent"), rows.map { it.entry.gauge })
    }

    @Test fun historyRetainsSeparateDatedEpisodesForTheSameCode() {
        val early = diagnostic("early", 33, 1000)
        val later = diagnostic("later", 35, 2000)
        val rows = alertWorkspaceRows(listOf(early, later), true) { false }
        assertEquals(listOf("later", "early"), rows.map { it.entry.id })
        assertEquals(listOf(2000L, 1000L), rows.map { it.entry.recordedAt })
        assertEquals(listOf("P0700", "P0700"), rows.map { it.fault!!.code })
    }

    @Test fun thresholdsRemainIndependentAndUnattributedWarningsRemainGeneric() {
        val code = diagnostic("code", 34)
        val threshold = entry("coolant", AlertSeverity.Warning, 1).copy(context = code.context)
        val unknownMil = entry("unknown", AlertSeverity.Warning, 1).let { it.copy(event = it.event.copy(key = 32)) }
        val rows = alertWorkspaceRows(listOf(threshold, code, unknownMil), false) { true }
        assertEquals(3, rows.size)
        assertEquals("Coolant", rows.first().title)
        assertEquals(null, rows.first().fault)
        assertEquals("Engine warning light", rows.last().title)
        assertEquals(null, rows.last().fault)
    }

    @Test fun diagnosticRowsUseOnlyTheRecordedControllerAndPreserveUnknownMeaning() {
        val engineContext = diagnostic("engine", 34, code = "P1999")
        val unknown = alertWorkspaceRows(listOf(engineContext), false) { true }.single()
        assertEquals("Code explanation unavailable", unknown.title)
        assertEquals("P1999", unknown.fault!!.code)
        val transmissionWarning = engineContext.copy(event = engineContext.event.copy(key = 33))
        assertEquals(null, alertWorkspaceRows(listOf(transmissionWarning), false) { true }.single().fault)
    }
}
