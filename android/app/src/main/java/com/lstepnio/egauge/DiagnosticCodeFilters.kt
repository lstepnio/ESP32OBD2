package com.lstepnio.egauge

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticCodeFilter(val controller: String, val code: String) {
    init {
        require(controller in setOf("ECM", "TCM") && code.matches(Regex("[PCBU][0-3][0-9A-F]{3}")) && code != "P0000") { "Enter an exact fault code, such as P0420" }
    }
}

/** Vehicle/controller scoped presentation policy. Raw ECU evidence is never rewritten. */
object DiagnosticCodeFilters {
    const val LIMIT = 16
    fun rule(controller: String, code: String) = DiagnosticCodeFilter(controller.uppercase(Locale.ROOT), code.trim().uppercase(Locale.ROOT))
    fun validate(rules: List<DiagnosticCodeFilter>) {
        require(rules.size <= LIMIT && rules.distinct().size == rules.size) { "Use up to 16 distinct code filters" }
    }
    fun json(rules: List<DiagnosticCodeFilter>): JSONArray {
        validate(rules)
        return JSONArray().also { out -> rules.forEach { out.put(JSONObject().put("controller",it.controller.lowercase(Locale.ROOT)).put("code",it.code)) } }
    }
    fun fromDocument(root: JSONObject): List<DiagnosticCodeFilter> =
        if(root.has("diagnosticFilters"))decode(root.getJSONArray("diagnosticFilters")) else emptyList()
    fun decode(array: JSONArray?): List<DiagnosticCodeFilter> {
        if(array == null) return emptyList()
        require(array.length()<=LIMIT) { "Use up to 16 code filters" }
        return (0 until array.length()).map { index ->
            val item=array.getJSONObject(index)
            require(item.length()==2) { "Unknown code-filter field" }
            DiagnosticCodeFilter(item.getString("controller").uppercase(Locale.ROOT),item.getString("code"))
        }.also(::validate)
    }
    fun filtersAlert(event: AlertEvent, raw: GaugeConfigTransferClient.Diagnostics?, elapsed: Long, rules: List<DiagnosticCodeFilter>): Boolean {
        if(event.key !in 32..35 || event.severity==AlertSeverity.Critical || raw==null || raw.revision!=event.revision)return false
        val source=if(event.key%2==1) "TCM" else "ECM"
        if(raw.source!=source)return false
        val projection=project(raw,elapsed,rules)
        return if(event.key<34)projection.suppressMil else projection.complete&&projection.hidden.isNotEmpty()&&projection.visible.isEmpty()
    }
    data class Projection(val visible: List<String>, val hidden: List<String>, val complete: Boolean, val suppressMil: Boolean)
    fun project(data: GaugeConfigTransferClient.Diagnostics, elapsed: Long, rules: List<DiagnosticCodeFilter>): Projection {
        validate(rules)
        val categories=data.categories.orEmpty()
        val categoryComplete=data.connected && !data.simulated && categories.size==3 && elapsed in 0..30_000 && categories.all {
            it.availability==DiagnosticAvailability.Unsupported || (!it.truncated && diagnosticCategoryAgeCurrent(it,elapsed)) }
        val checked=categories.filter { diagnosticCategoryAgeCurrent(it,elapsed) }
        val codes=checked.flatMap { it.codes }.distinct()
        val hidden=codes.filter { code -> rules.any { it.controller==data.source && it.code==code } }
        val visible=codes.filterNot { it in hidden }
        val confirmed=categories.firstOrNull()
        val milCurrent=data.milKnown && data.milAvailability==DiagnosticAvailability.Available && data.milFresh &&
            elapsed in 0..30_000 && data.milAgeMs?.let { it in 0..60_000 && elapsed<=60_000-it } == true
        val reconciled = !data.milOn || !milCurrent || (data.reportedCount>0 && confirmed!=null &&
            diagnosticCategoryAgeCurrent(confirmed,elapsed) && confirmed.codes.distinct().size==data.reportedCount)
        val complete=categoryComplete && reconciled
        val suppress=complete && data.milOn && milCurrent && hidden.isNotEmpty() && visible.isEmpty() &&
            data.reportedCount>0 && confirmed!=null && diagnosticCategoryAgeCurrent(confirmed,elapsed) &&
            !confirmed.truncated && confirmed.codes.distinct().size==data.reportedCount
        return Projection(visible,hidden,complete,suppress)
    }
}
