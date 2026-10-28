package com.lstepnio.egauge

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Saved vehicle intent projects presentation only. Repository evidence is never mutated. */
object AlertVisibility {
    fun project(entry: StoredAlert, rules: List<DiagnosticCodeFilter>): StoredAlert? {
        DiagnosticCodeFilters.validate(rules)
        val event = transition(entry.event, entry.context, rules) ?: return null
        return entry.copy(event = event, context = context(entry.context, rules))
    }

    fun transition(event: AlertEvent, recorded: JSONObject, rules: List<DiagnosticCodeFilter>): AlertEvent? {
        val source = controller(event)
        if (source == null) {
            return event.copy(
                label = redactUnscoped(event.label, rules),
                unit = redactUnscoped(event.unit, rules)
            )
        }
        val snapshot = recorded.optJSONObject("diagnostics")?.optJSONObject(source)
        val codes = codes(snapshot)
        val visible = codes.filterNot { DiagnosticCodeFilters.hides(source, it, rules) }
        val hidden = codes.size != visible.size
        val complete = complete(snapshot)
        val attributable = if (event.key < 34) {
            val stored = snapshot?.optJSONArray("categories")?.let { categories ->
                (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == "Stored" }
            }
            val storedCodes = codes(JSONObject().put("categories", JSONArray().put(stored)))
            complete && snapshot != null && snapshot.has("reportedCount") &&
                snapshot.optInt("reportedCount") > 0 && storedCodes.size == snapshot.optInt("reportedCount")
        } else {
            complete
        }
        if (hidden && visible.isEmpty() && attributable) return null
        // Diagnostic labels/counts can carry the original total. Rebuild from visible evidence.
        return if (rules.any { it.controller == source }) {
            event.copy(
                label = if (hidden || codes.isEmpty()) event.producer else redact(event.label, source, rules),
                value = if (visible.isEmpty()) Float.NaN else visible.size.toFloat()
            )
        } else {
            event
        }
    }

    /** Also used for ordinary diagnostic-operation reports and nested recorded context. */
    fun context(raw: JSONObject, rules: List<DiagnosticCodeFilter>): JSONObject = scrub(raw, null, rules) as JSONObject

    private fun scrub(value: Any?, inherited: String?, rules: List<DiagnosticCodeFilter>): Any = when (value) {
        is JSONObject -> {
            val source = value.optString("controller").uppercase(Locale.ROOT).takeIf { it in setOf("ECM", "TCM") }
                ?: value.optString("source").uppercase(Locale.ROOT).takeIf { it in setOf("ECM", "TCM") }
                ?: value.optInt("key", -1).takeIf { it in 32..35 }?.let { if (it % 2 == 1) "TCM" else "ECM" }
                ?: inherited
            val out = JSONObject()
            value.keys().forEach { key ->
                if (key != "configuration" && key != "diagnosticFilters") {
                    val childSource = key.takeIf { it in setOf("ECM", "TCM") } ?: source
                    if (key == "codes" && value.optJSONArray(key) != null) {
                        val codes = value.getJSONArray(key)
                        out.put(
                            key,
                            JSONArray(
                                (0 until codes.length()).map { codes.getString(it) }
                                    .filterNot { code ->
                                        if (childSource == null) {
                                            rules.any { it.code.equals(code, ignoreCase = true) }
                                        } else {
                                            DiagnosticCodeFilters.hides(childSource, code, rules)
                                        }
                                    }
                            )
                        )
                    } else {
                        out.put(key, scrub(value.opt(key), childSource, rules))
                    }
                }
            }
            if (source != null) {
                val before = codes(value)
                val after = codes(out)
                if (before.size != after.size) {
                    listOf("codeCount", "count").filter { out.has(it) }.forEach { out.put(it, after.size) }
                    if (out.has("reportedCount")) out.put("reportedCount", storedCodes(out).size)
                    if (after.isEmpty() && complete(value) && value.optInt("reportedCount", -1) > 0 &&
                        value.optInt("reportedCount") == storedCodes(value).size
                    ) {
                        out.remove("milOn")
                    }
                }
                if (out.optInt("key", -1) in 32..35 && rules.any { it.controller == source }) {
                    out.put("label", if (source == "TCM") "Transmission warning" else "Engine warning")
                    // These numbers belong to the raw diagnostic producer, not a visible code set.
                    out.remove("value")
                    out.remove("limit")
                }
            }
            out
        }

        is JSONArray -> JSONArray().also { out ->
            for (index in 0 until value.length()) out.put(scrub(value.opt(index), inherited, rules))
        }

        is String -> if (inherited == null) redactUnscoped(value, rules) else redact(value, inherited, rules)

        else -> value ?: JSONObject.NULL
    }

    // Free text has no controller attribution. Remove exact hidden tokens without hiding its episode.
    private fun redactUnscoped(value: String, rules: List<DiagnosticCodeFilter>): String = rules.fold(value) { text, rule -> redact(text, rule.controller, listOf(rule)) }

    private fun redact(value: String, source: String, rules: List<DiagnosticCodeFilter>): String {
        var text = value
        rules.filter { it.controller == source }.forEach { rule ->
            text = text.replace(Regex("(?i)(?<![A-Z0-9])${rule.code}(?![A-Z0-9])"), "")
        }
        return text.trim()
    }

    private fun controller(event: AlertEvent): String? = when (event.key) {
        32, 34 -> "ECM"
        33, 35 -> "TCM"
        else -> null
    }

    private fun codes(snapshot: JSONObject?): List<String> {
        val categories = snapshot?.optJSONArray("categories") ?: return snapshot?.optJSONArray("codes")?.let { codes ->
            (0 until codes.length()).map { codes.optString(it).uppercase(Locale.ROOT) }.distinct()
        }.orEmpty()
        return (0 until categories.length()).flatMap { index ->
            val codes = categories.optJSONObject(index)?.optJSONArray("codes") ?: return@flatMap emptyList()
            (0 until codes.length()).map { codes.optString(it).uppercase(Locale.ROOT) }
        }.distinct()
    }

    private fun storedCodes(snapshot: JSONObject): List<String> {
        val categories = snapshot.optJSONArray("categories") ?: return emptyList()
        return (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
            .firstOrNull { it.optString("name") == "Stored" }?.optJSONArray("codes")?.let { codes ->
                (0 until codes.length()).map { codes.optString(it) }.distinct()
            }.orEmpty()
    }

    private fun complete(snapshot: JSONObject?): Boolean {
        val categories = snapshot?.optJSONArray("categories") ?: return false
        if (categories.length() != 3) return false
        if (snapshot.optBoolean("milOn") && snapshot.has("reportedCount") &&
            (snapshot.optInt("reportedCount") <= 0 || storedCodes(snapshot).size != snapshot.optInt("reportedCount"))
        ) {
            return false
        }
        val entries = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
        return entries.map { it.optString("name") }.toSet() == setOf("Stored", "Pending", "Permanent") &&
            entries.all { category ->
                category.optString("availability") == "Unsupported" ||
                    (category.optString("availability") == "Available" && category.optBoolean("fresh") && !category.optBoolean("truncated"))
            }
    }
}
