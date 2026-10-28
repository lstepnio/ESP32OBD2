package com.lstepnio.egauge

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

fun exportAlertReport(
    context: Context,
    history: List<StoredAlert>,
    repository: AlertRepository,
    vehicle: String,
    rulesForVehicle: (String) -> List<DiagnosticCodeFilter>
): android.net.Uri {
    val visibleHistory = history.mapNotNull { AlertVisibility.project(it, rulesForVehicle(it.vehicle)) }
    val directory = File(context.cacheDir, "reports").apply { mkdirs() }
    directory.listFiles()?.filter {
        System.currentTimeMillis() - it.lastModified() > 86400000
    }?.forEach { it.delete() }
    directory.listFiles()?.sortedByDescending {
        it.lastModified()
    }?.drop(9)?.forEach { it.delete() }
    val file = File(directory, "egauge-alerts-${System.currentTimeMillis()}.zip")
    val manifest = JSONObject().put(
        "schemaVersion",
        1
    ).put("exportedAt", System.currentTimeMillis())
        .put(
            "diagnosticOperations",
            JSONArray().apply {
                val reports = repository.reports(vehicle)
                for (index in 0 until reports.length()) {
                    put(AlertVisibility.context(reports.getJSONObject(index), rulesForVehicle(vehicle)))
                }
            }
        ).put(
            "alerts",
            JSONArray().apply {
                visibleHistory.forEach { entry ->
                    // Configuration may contain adapter addresses; shared reports omit it.
                    val safe = JSONObject(entry.context.toString()).apply {
                        remove("configuration")
                    }
                    put(
                        JSONObject().put(
                            "vehicle",
                            entry.vehicle
                        ).put("current", entry.current).put("event", alertJson(entry.event))
                            .put(
                                "context",
                                safe
                            ).put(
                                "transitions",
                                JSONArray().apply {
                                    val transitions = repository.transitions(entry.id)
                                    for (index in 0 until transitions.length()) {
                                        AlertVisibility.transition(
                                            alertFromJson(transitions.getJSONObject(index)),
                                            history.first { it.id == entry.id }.context,
                                            rulesForVehicle(entry.vehicle)
                                        )?.let { put(alertJson(it)) }
                                    }
                                }
                            )
                    )
                }
            }
        )
    ZipOutputStream(file.outputStream().buffered()).use {
        it.putNextEntry(ZipEntry("alerts.json"))
        it.write(manifest.toString(2).toByteArray())
        it.closeEntry()
        it.putNextEntry(ZipEntry("report.txt"))
        it.write(
            buildString {
                append("eGauge diagnostic alert report\n\n")
                visibleHistory.forEach { entry ->
                    append(
                        "${entry.event.severity}: ${entry.event.label}\n${entry.event.lifecycle}; ${if (entry.current) "current" else "last checked"}; ${if (entry.event.unavailable) "data unavailable" else "observed data"}\nCaptured ${java.util.Date(
                            entry.recordedAt
                        )}\n\n"
                    )
                }
                append(
                    "Missing or stale context is not proof of fault resolution. Clearing does not repair the cause.\n"
                )
            }.toByteArray()
        )
        it.closeEntry()
    }
    var cachedBytes = directory.listFiles()?.sumOf { it.length() } ?: 0L
    directory.listFiles()?.filter { it != file }?.sortedBy { it.lastModified() }?.forEach { older ->
        if (cachedBytes >
            50L * 1024 * 1024
        ) {
            cachedBytes -= older.length()
            older.delete()
        }
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
}
