package com.lstepnio.egauge.ui.state

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
                faults.all { it.current } && source.status.tone !in setOf(StatusTone.Stale, StatusTone.Offline, StatusTone.Disabled, StatusTone.Error)
            )
        }
    }
}

/** A compact summary must never turn missing or partial data into an all-clear. */
fun carHealthStatus(state: CarUiState): StatusUi {
    val status = if (state.faultSources.isEmpty()) {
        state.status
    } else {
        vehicleFaultStatus(
            state.faultSources
        )
    }
    val partial =
        state.incomplete ||
            state.faultSources.any { source -> source.categories.any { it.incomplete } }
    val codes = carFaults(state).size
    val unavailable = state.faultSources.filter {
        it.status.tone in
            setOf(
                StatusTone.Stale,
                StatusTone.Offline,
                StatusTone.Disabled,
                StatusTone.Loading,
                StatusTone.Error
            )
    }.map { it.title }
    val title = when {
        status.tone in setOf(StatusTone.Warning, StatusTone.Critical) -> "Vehicle warning reported"

        state.faultSources.all {
            it.status.tone in setOf(StatusTone.Disabled, StatusTone.Loading)
        } &&
            status.tone in setOf(StatusTone.Disabled, StatusTone.Loading) -> "Waiting for car"

        unavailable.isNotEmpty() ||
            status.tone in setOf(
                StatusTone.Stale,
                StatusTone.Offline,
                StatusTone.Error
            ) -> "Fault checks incomplete"

        status.tone in setOf(StatusTone.Disabled, StatusTone.Loading) -> "Waiting for car"

        partial -> "Fault checks incomplete"

        codes > 0 -> "Fault codes recorded"

        status.tone == StatusTone.Success -> "No warning reported"

        else -> "Waiting for fault checks"
    }
    val detail = when {
        title == "Waiting for car" -> "Checks start automatically when this car is connected."

        unavailable.isNotEmpty() -> "${unavailable.joinToString(
            " and "
        )} checks are unavailable or out of date. Saved codes are kept while checks retry."

        partial -> "Some checks are incomplete. More codes may be present."

        status.tone in setOf(
            StatusTone.Stale,
            StatusTone.Offline,
            StatusTone.Error
        ) -> "Current fault status is unavailable. Saved codes are kept while checks retry."

        status.tone in setOf(
            StatusTone.Disabled,
            StatusTone.Loading
        ) -> "Checks start automatically when this car is connected."

        codes > 0 && status.tone in setOf(StatusTone.Warning, StatusTone.Critical) -> "Review the codes below. Checks update automatically."

        codes > 0 -> "Review the recorded codes. A warning light can be off while codes remain."

        status.tone in setOf(
            StatusTone.Warning,
            StatusTone.Critical
        ) -> "The car reports a warning. Fault code checks continue automatically."

        status.tone == StatusTone.Success -> "The car is not reporting a warning light. Checks continue automatically."

        else -> "Checks continue automatically. Current fault status has not been established."
    }
    return StatusUi(
        title,
        detail,
        if (title == "Fault checks incomplete" &&
            status.tone !in setOf(StatusTone.Warning, StatusTone.Critical, StatusTone.Error)
        ) {
            StatusTone.Stale
        } else {
            status.tone
        }
    )
}
