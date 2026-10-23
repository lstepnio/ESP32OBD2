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

/** Routine coverage is neutral; known warnings/codes and last-checked meaning stay visible. */
fun carHealthStatus(state: CarUiState): StatusUi {
    val status = if (state.faultSources.isEmpty()) state.status else vehicleFaultStatus(state.faultSources)
    val partial = state.incomplete || state.faultSources.any { source -> source.categories.any { it.incomplete } }
    val faults = carFaults(state)
    val unavailable = state.faultSources.filter {
        it.status.tone in setOf(StatusTone.Stale, StatusTone.Offline, StatusTone.Disabled, StatusTone.Loading, StatusTone.Error)
    }.map { it.title }
    val warning = (state.faultSources.map { it.status } + status)
        .firstOrNull { it.tone == StatusTone.Critical }
        ?: (state.faultSources.map { it.status } + status).firstOrNull { it.tone == StatusTone.Warning }
    val unavailableDetail = if (unavailable.isNotEmpty()) {
        "${unavailable.joinToString(" and ")} status is unavailable. Checks update automatically when connected."
    } else {
        "Checks update automatically when connected."
    }
    return when {
        warning != null -> StatusUi(
            "Warning light on",
            if (unavailable.isNotEmpty()) {
                unavailableDetail
            } else if (faults.isNotEmpty()) {
                "Review the codes below."
            } else {
                "Codes will appear here as they are read."
            },
            warning.tone
        )

        faults.isNotEmpty() -> StatusUi(
            if (faults.size == 1) "1 fault code" else "${faults.size} fault codes",
            if (unavailable.isNotEmpty()) unavailableDetail else "A warning light can be off while codes remain.",
            if (faults.any { !it.current }) StatusTone.Stale else StatusTone.Neutral
        )

        status.tone in setOf(StatusTone.Disabled, StatusTone.Loading) &&
            state.faultSources.all { it.status.tone in setOf(StatusTone.Disabled, StatusTone.Loading) } -> StatusUi(
            "Waiting for car",
            "Codes appear automatically when this car is connected.",
            StatusTone.Neutral
        )

        partial -> StatusUi(
            "Checking for codes",
            "Codes appear here as they are read.",
            StatusTone.Neutral
        )

        unavailable.isNotEmpty() || status.tone in setOf(StatusTone.Stale, StatusTone.Offline, StatusTone.Error) -> StatusUi(
            "No recent check",
            unavailableDetail,
            StatusTone.Neutral
        )

        status.tone == StatusTone.Success -> StatusUi(
            "No warning reported",
            "The car is not reporting a warning light.",
            StatusTone.Success
        )

        else -> StatusUi("Checking for codes", "Codes appear here as they are read.", StatusTone.Neutral)
    }
}
