package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.*
import org.json.JSONObject

fun alertTitle(event: AlertEvent): String = when (event.key) {
    32 -> "Engine warning light"
    33 -> "Transmission warning light"
    34 -> "Engine warning"
    35 -> "Transmission warning"
    else -> event.label
}

/** A notification describes the event, not a diagnosis or an invitation to repeat an ECU action. */
fun alertNotificationText(event: AlertEvent, system: MeasurementSystem, codes: List<CarFaultUi> = emptyList()): String {
    val source = if (event.key % 2 == 1) "Transmission" else "Engine"
    val faults = codes.filter { it.source == source }
    return when {
        event.key in 32..35 && faults.isNotEmpty() -> "${faults.first().code} · ${FaultCodeCatalog.lookup(faults.first().code).let { it.title + it.manufacturer?.let { maker -> " ($maker reference)" }.orEmpty() }}${if (faults.size > 1) " · +${faults.size - 1} more" else ""}"

        event.key in 32..33 -> "The car reported a warning light. Open the saved alert to review available codes."

        event.key in 34..35 -> "The car reported a warning. Code details were not available with this alert."

        event.key < 32 && event.value.isFinite() && event.limit.isFinite() -> {
            val reading = alertContextReading(event.value.toDouble(), event.unit, system)
            val limit = alertContextReading(event.limit.toDouble(), event.unit, system)
            if (event.unit == "gear") "$reading matched your alert setting." else "$reading · Your limit: $limit"
        }

        else -> "${event.severity.name} · Open the saved alert for more information."
    }
}

/** Context belongs to its recorded controller snapshot, never today's active vehicle or live checks. */
fun recordedFaultCodes(context: JSONObject, eventKey: Int? = null): List<CarFaultUi> {
    val snapshots = context.optJSONObject("diagnostics") ?: return emptyList()
    return listOf("ECM", "TCM").filter { source ->
        eventKey == null || eventKey !in 32..35 || source == if (eventKey % 2 == 1) "TCM" else "ECM"
    }.flatMap { source ->
        val categories = snapshots.optJSONObject(source)?.optJSONArray("categories") ?: return@flatMap emptyList()
        val byCode = linkedMapOf<String, MutableSet<String>>()
        for (i in 0 until categories.length()) {
            val category = categories.optJSONObject(i) ?: continue
            val codes = category.optJSONArray("codes") ?: continue
            for (j in 0 until codes.length()) {
                val code = codes.optString(j).trim().uppercase(java.util.Locale.ROOT)
                if (code.isNotEmpty()) byCode.getOrPut(code) { linkedSetOf() }.add(category.optString("name"))
            }
        }
        byCode.map { (code, names) ->
            CarFaultUi(code, FaultCodeCatalog.lookup(code).title, if (source == "TCM") "Transmission" else "Engine", names.joinToString(" · "), names.toList(), false)
        }
    }
}
