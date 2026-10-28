package com.lstepnio.egauge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Ordinary sharing uses saved policy, while SQLite remains the immutable evidence owner. */
class AlertVisibilityReportTest {
    @Test fun ordinaryZipRemovesHiddenEpisodesNestedCodesTransitionsAndConfigurationPolicy() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) {
            "Report fixtures require a disposable emulator"
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AlertRepository(context)
        val unique = java.util.UUID.randomUUID().toString()
        val vehicle = "export-car:$unique"
        val gauge = "export-gauge:$unique"
        val now = System.currentTimeMillis()
        val rules = listOf(DiagnosticCodeFilters.rule("TCM", "P0700"))
        val configuration = JSONObject().put("baseRevision", 9)
            .put("definitions", JSONArray().put(JSONObject().put("name", "Coolant").put("unit", "degC")))
            .put("diagnosticFilters", DiagnosticCodeFilters.json(rules)).toString()
        fun diagnostics(codes: List<String>): JSONObject = JSONObject().put(
            "TCM",
            JSONObject().put("milOn", true).put("reportedCount", codes.size)
                .put(
                    "categories",
                    JSONArray().apply {
                        listOf("Stored", "Pending", "Permanent").forEachIndexed { index, name ->
                            put(
                                JSONObject().put("name", name).put("availability", "Available")
                                    .put("fresh", true).put("truncated", false)
                                    .put("codes", JSONArray(if (index == 0) codes else emptyList<String>()))
                            )
                        }
                    }
                )
        )
        fun event(sequence: Long, episode: Long, key: Int = 35) = AlertEvent(
            sequence, 77, episode,
            1000, key, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false,
            if (key == 0) 108f else 1f, if (key == 0) 105f else 0f,
            if (key == 0) "Coolant" else "P0700 transmission faults", if (key == 0) "degC" else "codes", 1000, 10
        )
        fun ingest(event: AlertEvent, codes: List<String>) {
            repository.ingest(
                vehicle,
                gauge,
                AlertBatch(77, 1, event.sequence, 0, event.sequence, false, true, false, listOf(event)),
                configuration,
                diagnostics(codes).toString(),
                now + event.sequence
            )
        }
        fun evidence(): Map<String, String> = repository.list(vehicle).associate { entry ->
            entry.id to (alertJson(entry.event).toString() + "\n" + entry.context.toString() + "\n" + repository.transitions(entry.id).toString())
        }
        try {
            val hidden = event(1, 11)
            val mixed = event(2, 12).copy(value = 2f)
            val threshold = event(3, 13, key = 0)
            ingest(hidden, listOf("P0700"))
            ingest(mixed, listOf("P0700", "U0121"))
            ingest(threshold, listOf("P0700"))
            ingest(mixed.copy(sequence = 4, kind = 5, acknowledged = true), listOf("P0700", "U0121"))
            repository.saveReport(
                "operation:$unique",
                JSONObject().put("vehicle", vehicle)
                    .put("stage", "before clear").put("diagnostics", diagnostics(listOf("P0700", "U0121")))
                    .put("configuration", JSONObject(configuration)),
                now
            )
            val rawBefore = evidence()
            val operationsBefore = repository.reports(vehicle).toString()
            assertTrue(rawBefore.values.any { "P0700" in it })
            val uri = exportAlertReport(context, repository.list(vehicle), repository, vehicle) { id ->
                if (id == vehicle) rules else emptyList()
            }
            val files = linkedMapOf<String, String>()
            ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
                while (true) {
                    val part = zip.nextEntry ?: break
                    files[part.name] = zip.readBytes().toString(Charsets.UTF_8)
                    zip.closeEntry()
                }
            }
            assertEquals(setOf("alerts.json", "report.txt"), files.keys)
            val entireZip = files.values.joinToString("\n")
            assertFalse(entireZip.contains("P0700"))
            assertFalse(entireZip.contains("diagnosticFilters"))
            assertFalse(entireZip.contains("configuration\""))
            assertFalse(entireZip.contains("NaN"))
            assertTrue(entireZip.contains("U0121"))
            assertTrue(entireZip.contains("Coolant"))
            val manifest = JSONObject(files.getValue("alerts.json"))
            val alerts = manifest.getJSONArray("alerts")
            assertEquals(2, alerts.length())
            val episodes = (0 until alerts.length()).map { alerts.getJSONObject(it).getJSONObject("event").getLong("episode") }
            assertFalse(episodes.contains(hidden.episode))
            assertTrue(episodes.containsAll(listOf(mixed.episode, threshold.episode)))
            val exportedMixed = (0 until alerts.length()).map { alerts.getJSONObject(it) }
                .single { it.getJSONObject("event").getLong("episode") == mixed.episode }
            assertEquals(1.0, exportedMixed.getJSONObject("event").getDouble("value"), 0.0)
            assertEquals(2, exportedMixed.getJSONArray("transitions").length())
            assertEquals(rawBefore, evidence())
            assertEquals(operationsBefore, repository.reports(vehicle).toString())
            // Removing the saved rule reveals the retained episode again.
            assertNotNull(AlertVisibility.project(repository.list(vehicle).single { it.id == hidden.id(gauge) }, emptyList()))
        } finally {
            repository.close()
        }
    }
}
