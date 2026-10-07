package com.lstepnio.egauge

enum class DiagnosticAvailability { NotChecked, Available, Unavailable, Unsupported }

data class DiagnosticCategory(val name: String, val availability: DiagnosticAvailability,
    val known: Boolean, val fresh: Boolean, val codes: List<String>, val ageMs: Long?,
    val truncated: Boolean = false)

/** Evidence ages are based on gauge monotonic time, never wall-clock time. */
fun diagnosticCategoryAgeCurrent(category: DiagnosticCategory, elapsedSinceRead: Long): Boolean =
    elapsedSinceRead in 0..30_000 && category.known && category.fresh &&
        category.availability == DiagnosticAvailability.Available && category.ageMs != null &&
        category.ageMs in 0..120_000 && elapsedSinceRead <= 120_000 - category.ageMs

fun validateDiagnosticScope(data: GaugeConfigTransferClient.Diagnostics, source: String,
                            revision: Long?) {
    require(data.source == source && data.responder == if (source == "TCM") 0x7e9 else 0x7e8) {
        "The gauge's fault snapshot belongs to a different adapter setup. Send this profile's setup first."
    }
    require(!data.simulated) { "Simulated readings cannot establish vehicle fault status" }
    if (data.revision != null) require(revision != null && data.revision == revision) {
        "Refresh the gauge's saved setup before checking faults for this profile."
    }
}
