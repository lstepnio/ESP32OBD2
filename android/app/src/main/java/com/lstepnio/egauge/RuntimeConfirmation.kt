package com.lstepnio.egauge

enum class RuntimeConfirmation { WAITING, ACTIVE, RECOVERED, REJECTED }

fun confirmRuntime(
    expectedRevision: Long,
    expectedSha256: String,
    identity: GaugeConfigTransferClient.RuntimeIdentity,
): RuntimeConfirmation = when {
    identity.usedPreviousGeneration -> RuntimeConfirmation.RECOVERED
    !identity.running || identity.revision != expectedRevision || identity.sha256 != expectedSha256 ->
        RuntimeConfirmation.REJECTED
    identity.trial -> RuntimeConfirmation.WAITING
    else -> RuntimeConfirmation.ACTIVE
}

