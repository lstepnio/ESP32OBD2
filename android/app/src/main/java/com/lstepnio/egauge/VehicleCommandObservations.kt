package com.lstepnio.egauge

/** Scope and epoch fence delayed replies independently of the owner GATT session. */
data class VehicleCommandScope(val gauge: String, val vehicle: String, val revision: Long, val sha256: String)

data class VehicleCommandObservation(val status: VehicleCommandStatus, val observedAt: Long?)

data class VehicleCommandObservations(
    val scope: VehicleCommandScope? = null,
    val epoch: Long = 0,
    val entries: Map<VehicleCommandKind, VehicleCommandObservation> = emptyMap()
) {
    val values get() = entries.mapValues { it.value.status }

    fun inScope(value: VehicleCommandScope) = if (scope == value) this else copy(scope = value, epoch = epoch + 1, entries = emptyMap())
    fun retired() = VehicleCommandObservations(epoch = epoch + 1)

    fun freshKinds(value: VehicleCommandScope?, now: Long): Set<VehicleCommandKind> = if (value == null || value != scope) {
        emptySet()
    } else {
        entries.filterValues { it.observedAt?.let { at -> now >= at && now - at <= 30_000 } == true }.keys
    }

    fun snapshot(value: VehicleCommandScope, expectedEpoch: Long, statuses: Map<VehicleCommandKind, VehicleCommandStatus>, observedAt: Long): VehicleCommandObservations {
        if (!accepts(value, expectedEpoch) || observedAt < 0 || statuses.isEmpty() || statuses.any { (kind, status) -> !valid(kind, status) } || statuses.values.map { it.boot }.distinct().size != 1) return this
        return copy(entries = statuses.mapValues { VehicleCommandObservation(it.value, observedAt) })
    }

    fun pending(value: VehicleCommandScope, expectedEpoch: Long, kind: VehicleCommandKind): VehicleCommandObservations {
        if (!accepts(value, expectedEpoch)) return this
        val previous = entries[kind] ?: return this
        return copy(entries = entries + (kind to VehicleCommandObservation(previous.status.copy(phase = VehicleCommandPhase.Unknown), null)))
    }

    fun observed(value: VehicleCommandScope, expectedEpoch: Long, kind: VehicleCommandKind, status: VehicleCommandStatus, observedAt: Long): VehicleCommandObservations {
        if (!accepts(value, expectedEpoch) || observedAt < 0 || !valid(kind, status)) return this
        // A new gauge boot retires sibling receipts, even with unchanged image/configuration.
        val retained = if (entries.values.any { it.status.boot != status.boot }) emptyMap() else entries
        return copy(entries = retained + (kind to VehicleCommandObservation(status, observedAt)))
    }

    private fun accepts(value: VehicleCommandScope, expectedEpoch: Long) = value == scope && expectedEpoch == epoch
    private fun valid(kind: VehicleCommandKind, status: VehicleCommandStatus) = status.kind == kind && status.revision == scope?.revision && status.boot > 0
}
