package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*

fun diagnosticSources(data: GaugeConfigTransferClient.Diagnostics?, elapsedSinceRead: Long,
                      expectedSource: String): List<FaultSourceUi> = listOf("ECM", "TCM").map { source ->
    val title = if (source == "TCM") "Transmission" else "Engine"
    if (data == null || data.source != source || data.source != expectedSource || data.simulated)
        FaultSourceUi(title, StatusUi(if (source == expectedSource) "Waiting for car" else "Not checked", "Fault checks start automatically when this car is connected.", StatusTone.Disabled), emptyList())
    else {
        val receiptCurrent = elapsedSinceRead in 0..30_000
        val milCurrent = receiptCurrent && data.milFresh && (data.milAgeMs == null ||
            data.milAgeMs + elapsedSinceRead <= 60_000)
        val categories = data.categories ?: listOf(
            DiagnosticCategory("Stored", DiagnosticAvailability.Available, data.confirmedFresh || data.confirmedFirst != null,
                data.confirmedFresh, listOfNotNull(data.confirmedFirst), if (data.confirmedFresh) 0 else null),
            DiagnosticCategory("Pending", DiagnosticAvailability.Available, data.pendingFresh || data.pendingFirst != null,
                data.pendingFresh, listOfNotNull(data.pendingFirst), if (data.pendingFresh) 0 else null),
            DiagnosticCategory("Permanent", DiagnosticAvailability.Available, data.permanentFresh || data.permanentFirst != null,
                data.permanentFresh, listOfNotNull(data.permanentFirst), if (data.permanentFresh) 0 else null))
        val categoryUi = categories.map { category ->
            val current = data.connected && diagnosticCategoryAgeCurrent(category, elapsedSinceRead)
            val age = category.ageMs?.let { checkedAgo(it + elapsedSinceRead.coerceIn(0, 120_000)) }
            val status = when {
                category.availability == DiagnosticAvailability.Unsupported -> "Not supported by this controller"
                category.availability == DiagnosticAvailability.Unavailable -> "Unavailable. Retrying automatically."
                !category.known -> "Not checked yet"
                !current -> "Out of date${age?.let { ". $it" } ?: ". Retrying automatically."}"
                category.codes.isEmpty() -> "No ${category.name.lowercase()} codes reported. ${age ?: "Checked just now"}"
                else -> age ?: "Checked just now"
            }
            FaultCategoryUi(category.name, status, category.codes.map {
                FaultUi(it, faultDescription(it), if (current) category.name else "Last checked · ${category.name}")
            })
        }
        val status = when {
            !data.connected -> StatusUi("Adapter disconnected", "Previously read codes are shown as last checked.", StatusTone.Stale)
            !receiptCurrent -> StatusUi("Refreshing faults", "Last checked codes remain visible while we reconnect.", StatusTone.Stale)
            milCurrent && data.milOn -> StatusUi("$title reports a warning", "${data.reportedCount} codes reported by this controller. Review the lists below.", StatusTone.Critical)
            milCurrent -> StatusUi("$title warning is off", "Last checked controller status. Other fault categories may still contain codes.", StatusTone.Success)
            else -> StatusUi("Warning status unavailable", "Review the fault lists below; no current warning-lamp status was established.", StatusTone.Stale)
        }
        FaultSourceUi(title, status, categoryUi)
    }
}

private fun checkedAgo(ageMs: Long): String = when {
    ageMs < 1000 -> "Checked just now"
    ageMs < 60_000 -> "Checked ${ageMs / 1000} seconds ago"
    else -> "Checked ${ageMs / 60_000} minutes ago"
}
