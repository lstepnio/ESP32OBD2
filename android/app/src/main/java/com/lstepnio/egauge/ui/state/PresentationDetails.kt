package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.DetailUi

/** Explicit allowlist: no Wi-Fi session, credential, key or bond object can be exported here. */
fun AppViewModel.presentationDetails(): List<DetailUi> = buildList {
    fun fact(name: String, value: Any?) { if (value != null) add(DetailUi(name, value.toString())) }
    fact("Gauge identifier", rememberedGaugeId)
    fact("Connection check", connection.phase.name)
    fact("Connection retries", connection.attempts.toString())
    fact("Connection detail", connection.detail)
    fact("Owner access", ownerAccess.name)
    fact("Last device response", deviceMessage)
    fact("Profile identifier", profileCollection.activeId)
    fact("Profile problem", profileError)
    fact("Gauge association problem", gaugeAssociationError)
    fact("Operation", "${operation.id} · ${operation.kind} · ${operation.stage}")
    fact("Operation response", operation.detail)
    capabilities?.let { caps ->
        fact("Board", caps.board)
        fact("Protocol", caps.protocolMajor)
        fact("Capability flags", caps)
        fact("Adapter links", "${caps.maxAdapterLinks}; simultaneous use ${if (caps.simultaneousVerified) "verified" else "unverified"}")
        fact("Transfer", caps.wifiBulk ?: "Bluetooth")
    }
    savedGauge?.let {
        fact("Saved selection", listOf("RPM", "Speed", "Engine load", "Coolant", "Fuel").getOrNull(it.readingIndex))
        fact("Saved selection revision", it.revision)
        fact("Rotation", "${it.rotation * 90}°")
    }
    runtimeIdentity?.let {
        fact("Running revision", it.revision); fact("Stored revision", it.storedRevision)
        fact("Running SHA-256", it.sha256); fact("Runtime flags", "running=${it.running}, trial=${it.trial}, previous=${it.usedPreviousGeneration}")
    }
    fact("Expected sent SHA-256", expectedSentDigest)
    fact("Verified stored SHA-256", verifiedConfigHash)
    fact("Verified stored revision", activeConfigRevision)
    activeDocument?.let {
        fact("Document profile", it.vehicleProfileId); fact("Document revision", it.revision)
        fact("Document SHA-256", it.sha256); fact("Document bytes", it.length)
        fact("Definitions / pages / alerts", "${it.definitionCount} / ${it.pageCount} / ${it.alertCount}")
        fact("Complete saved configuration", it.json)
    }
    draftComparison?.let { comparison ->
        fact("Saved comparison scope", "${comparison.matchingCount} match; ${comparison.differingCount} differ; ${comparison.unknownCount} unavailable. Saved settings only; no live vehicle support is implied.")
        comparison.fields.forEach { field ->
            fact(field.label, "On this phone: ${field.phone}\nOn gauge: ${field.gauge ?: "Unavailable"}\n" +
                when (field.matches) { true -> "Matches"; false -> "Different"; null -> "Not comparable" })
        }
    }
    fact("Stored read response", documentMessage)
    draft.alerts.forEach { alert ->
        val reading = demoCatalog.firstOrNull { it.id == alert.pidId }
        fact("${reading?.let { readingName(it.id) } ?: alert.pidId} alert",
            "Warn ${alert.direction.name.lowercase()} ${alert.warning} ${reading?.unit.orEmpty()}; critical ${alert.direction.name.lowercase()} ${alert.critical} ${reading?.unit.orEmpty()}. Reset margin ${alert.hysteresis}. Trigger after ${alert.triggerDwellMs} ms; clear after ${alert.clearDwellMs} ms.")
        fact("${alert.id} technical fields", "Direction=${alert.direction}; hysteresis=${alert.hysteresis}; triggerDwellMs=${alert.triggerDwellMs}; clearDwellMs=${alert.clearDwellMs}; priority=${alert.priority}")
    }
    draft.pages.forEachIndexed { index, page ->
        fact("Page ${index + 1}", "${page.id} · ${page.name} · ${page.layout}\n${page.pidIds.joinToString()}")
    }
    demoCatalog.forEach { pid -> fact("${readingName(pid.id)} definition", "ID=${pid.id}; source=${pid.source}; request=${pid.request}; unit=${pid.unit}; ${pid.exampleKind}") }
    fact("Vehicle observations", if (vehicleObservations.isEmpty()) "None" else vehicleObservations.joinToString("\n"))
    fact("Diagnostics receipt time (elapsed ms)", diagnosticsObservedAtElapsedMs)
    fact("Diagnostic raw snapshot", diagnostics)
    fact("Diagnostic limitations", "Counts and first code only per category. Live freshness expires after 30 seconds. No code clearing is implemented.")
    bootIdentity?.let {
        fact("Installed version", it.version); fact("OTA state", it.otaState)
        fact("Partition", "0x${it.partitionAddress.toString(16)}"); fact("Partition subtype", it.partitionSubtype)
        fact("Secure version", it.secureVersion); fact("ELF SHA-256", it.elfSha256)
    }
    hardwareSnapshot?.let { h ->
        fact("Processor", "${h.cores} cores at ${h.cpuMhz} MHz; model ${h.chipModel}; chip revision ${h.chipRevision}")
        fact("Flash", "${h.flashBytes} bytes")
        fact("Internal RAM", "${h.internalFreeBytes} free of ${h.internalTotalBytes} bytes\nMinimum free ${h.internalMinimumFreeBytes}; largest block ${h.internalLargestBlockBytes}")
        fact("PSRAM", "${h.psramFreeBytes} free of ${h.psramTotalBytes} bytes; minimum free ${h.psramMinimumFreeBytes}")
        fact("Uptime", "${h.uptimeSeconds} seconds")
        fact("Last reset", resetLabel(h.resetReason))
        fact("Wi-Fi state", wifiLabel(h.wifiMode))
        fact("Initialized subsystems", hardwareLabels(h.initializedFeatures))
        fact("Declared subsystems", hardwareLabels(h.declaredFeatures))
        fact("Hardware raw snapshot", h)
    }
    fact("Hosted update response", hostedUpdateMessage)
    hostedUpdate?.let { fact("Release metadata", it.release) }
    if (updateReady) updateBundle().let { bundle ->
        fact("Package bytes", bundle.image.size)
        fact("Package SHA-256", bundle.sha256.hex())
        fact("Package ELF SHA-256", bundle.elfSha256.hex())
        fact("Signature (DER)", bundle.signatureDer.hex())
        fact("Signature verification", "Verified with the pinned development public key; not production signing.")
    }
    fact("Update response", updatePackageMessage)
    fact("Update recovery", updateRecoveryResult)
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun resetLabel(reason: Int): String {
    val labels = listOf("Unknown", "Power on", "External reset", "Software restart", "Software panic",
        "Interrupt watchdog", "Task watchdog", "Watchdog", "Deep sleep wake", "Brownout", "SDIO",
        "USB peripheral", "JTAG", "eFuse error", "Power glitch", "CPU lockup")
    return "${labels.getOrNull(reason) ?: "Unknown"} ($reason)"
}
private fun wifiLabel(mode: Int): String = when (mode) {
    0 -> "Initialized, radio idle"; 1 -> "Station"; 2 -> "Temporary access point"
    3 -> "Station and access point"; 255 -> "Stack not initialized"; else -> "Unknown mode $mode"
}
private fun hardwareLabels(flags: Long) = listOfNotNull(
    "Wi-Fi".takeIf { flags and GaugeProtocolCodec.HARDWARE_WIFI != 0L },
    "BLE".takeIf { flags and GaugeProtocolCodec.HARDWARE_BLE != 0L },
    "PSRAM".takeIf { flags and GaugeProtocolCodec.HARDWARE_PSRAM != 0L },
    "display".takeIf { flags and GaugeProtocolCodec.HARDWARE_DISPLAY != 0L },
    "touch".takeIf { flags and GaugeProtocolCodec.HARDWARE_TOUCH != 0L },
    "backlight".takeIf { flags and GaugeProtocolCodec.HARDWARE_BACKLIGHT != 0L },
    "IMU".takeIf { flags and GaugeProtocolCodec.HARDWARE_IMU != 0L },
    "battery ADC".takeIf { flags and GaugeProtocolCodec.HARDWARE_BATTERY_ADC != 0L },
    "expansion".takeIf { flags and GaugeProtocolCodec.HARDWARE_EXPANSION != 0L },
    "USB to UART".takeIf { flags and GaugeProtocolCodec.HARDWARE_USB_UART != 0L },
).joinToString().ifEmpty { "None reported" }
