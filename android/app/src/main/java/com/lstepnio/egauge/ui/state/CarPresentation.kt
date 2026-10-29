package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.core.designsystem.*

/** One vehicle list, retaining endpoint and last-checked meaning for each code. */
data class CarFaultUi(
    val code: String,
    val description: String,
    val source: String,
    val categories: String,
    val categoryNames: List<String> = emptyList(),
    val current: Boolean = true
)

fun carFaults(state: CarUiState): List<CarFaultUi> {
    val sources = state.faultSources.ifEmpty {
        listOf(
            FaultSourceUi("Vehicle", state.status, listOf(FaultCategoryUi("", "", state.faults)))
        )
    }
    return sources.flatMap { source ->
        source.categories.flatMap { it.faults }.groupBy { it.code }.map { (code, faults) ->
            CarFaultUi(
                code,
                faults.first().description,
                source.title,
                faults.map {
                    it.category
                }.filter { it.isNotBlank() }.distinct().joinToString(" · "),
                source.categories.filter { category -> category.faults.any { it.code == code } }.map { it.name }.filter { it.isNotBlank() }.distinct(),
                faults.any { it.current }
            )
        }
    }
}

/** Visible attention rows are stable across polls; recorded history is a separate destination. */
data class CarAttentionAlert(val id: String, val title: String, val detail: String, val severity: Int, val diagnosticSource: String? = null)
data class CarAttentionIssue(val title: String, val detail: String, val severity: Int, val fault: CarFaultUi? = null, val alertId: String? = null, val warningSource: String? = null)
fun carAttention(state: CarUiState, alerts: List<CarAttentionAlert>): List<CarAttentionIssue> {
    val faults = carFaults(state).filter { it.current }
    val codes = faults.map {
        CarAttentionIssue(
            com.lstepnio.egauge.FaultCodeCatalog.lookup(it.code).title,
            "${it.code} · ${it.source}",
            1,
            fault = it
        )
    }
    val episodes = alerts.filterNot { alert ->
        alert.diagnosticSource != null && faults.any { it.source == alert.diagnosticSource }
    }.distinctBy { it.diagnosticSource ?: it.id }.map { CarAttentionIssue(it.title, it.detail, it.severity, alertId = it.id) }
    val unknownWarnings = state.faultSources.filter { source ->
        source.status.tone == StatusTone.Warning && faults.none { it.source == source.title } &&
            alerts.none { it.diagnosticSource == source.title }
    }.map { CarAttentionIssue("${it.title} warning", "Code information is not available", 3, warningSource = it.title) }
    return (episodes + unknownWarnings + codes).sortedByDescending { it.severity }
}
fun carChecksComplete(state: CarUiState): Boolean = !state.incomplete &&
    state.faultSources.isNotEmpty() && state.faultSources.all { source ->
        source.status.tone == StatusTone.Success && source.categories.isNotEmpty() &&
            source.categories.none { it.incomplete } && source.categories.all { it.faults.isEmpty() }
    }

fun carAlertSummary(event: com.lstepnio.egauge.AlertEvent, system: MeasurementSystem): String = when {
    event.unavailable -> "Current reading unavailable"

    event.key < 32 && event.value.isFinite() && event.limit.isFinite() ->
        "${alertContextReading(event.value.toDouble(), event.unit, system)} · Limit ${alertContextReading(event.limit.toDouble(), event.unit, system)}"

    else -> "Reported by your car"
}
