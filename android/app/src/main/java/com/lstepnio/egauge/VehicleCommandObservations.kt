package com.lstepnio.egauge

/** Scope and epoch fence delayed replies independently of the owner GATT session. */
data class VehicleCommandScope(val gauge: String, val vehicle: String, val revision: Long, val sha256: String)

data class VehicleCommandObservation(val status: VehicleCommandStatus, val observedAt: Long?)

data class VehicleCommandObservations(
    val scope: VehicleCommandScope? = null,
    val epoch: Long = 0,
    val observedBoot: Long? = null,
    val entries: Map<VehicleCommandKind, VehicleCommandObservation> = emptyMap()
) {
    val values get() = entries.mapValues { it.value.status }

    fun inScope(value: VehicleCommandScope) = if (scope == value) this else copy(scope = value, epoch = epoch + 1, observedBoot = null, entries = emptyMap())
    fun retired() = VehicleCommandObservations(epoch = epoch + 1)

    fun freshKinds(value: VehicleCommandScope?, now: Long): Set<VehicleCommandKind> = if (value == null || value != scope) {
        emptySet()
    } else {
        entries.filterValues { it.observedAt?.let { at -> now >= at && now - at <= 30_000 } == true }.keys
    }

    fun snapshot(value: VehicleCommandScope, expectedEpoch: Long, observations: Map<VehicleCommandKind, VehicleCommandObservation>): VehicleCommandObservations {
        if (!accepts(value, expectedEpoch) || observations.isEmpty() || observations.any { (kind, observation) -> observation.observedAt == null || observation.observedAt < 0 || !valid(kind, observation.status) }) return this
        if (observations.values.map { it.status.boot }.distinct().size != 1) {
            return copy(epoch = epoch + 1, entries = entries.mapValues { it.value.copy(observedAt = null) })
        }
        val boot = observations.values.first().status.boot
        val rebooted = observedBoot?.let { it != boot } ?: entries.values.any { it.status.boot != boot }
        return copy(epoch = epoch + if (rebooted) 1 else 0, observedBoot = boot, entries = observations.toMap())
    }

    fun pending(value: VehicleCommandScope, expectedEpoch: Long, kind: VehicleCommandKind): VehicleCommandObservations {
        if (!accepts(value, expectedEpoch)) return this
        val previous = entries[kind] ?: return this
        return copy(entries = entries + (kind to VehicleCommandObservation(previous.status.copy(phase = VehicleCommandPhase.Unknown), null)))
    }

    fun observed(value: VehicleCommandScope, expectedEpoch: Long, kind: VehicleCommandKind, status: VehicleCommandStatus, observedAt: Long): VehicleCommandObservations {
        if (!accepts(value, expectedEpoch) || observedAt < 0 || !valid(kind, status)) return this
        // A new gauge boot retires sibling receipts, even with unchanged image/configuration.
        val rebooted = observedBoot?.let { it != status.boot } ?: entries.values.any { it.status.boot != status.boot }
        val retained = if (rebooted) emptyMap() else entries
        return copy(epoch = epoch + if (rebooted) 1 else 0, observedBoot = status.boot, entries = retained + (kind to VehicleCommandObservation(status, observedAt.takeUnless { status.phase == VehicleCommandPhase.Unknown })))
    }

    fun bootEvidence(value: VehicleCommandScope, expectedEpoch: Long, status: VehicleCommandStatus): VehicleCommandObservations {
        if (!accepts(value, expectedEpoch) || !valid(status.kind, status)) return this
        val known = observedBoot ?: entries.values.firstOrNull()?.status?.boot
        if (known == status.boot) return this
        return copy(
            observedBoot = status.boot,
            epoch = epoch + if (known == null) 0 else 1,
            entries = if (known == null) entries else entries.mapValues { it.value.copy(observedAt = null) }
        )
    }

    private fun accepts(value: VehicleCommandScope, expectedEpoch: Long) = value == scope && expectedEpoch == epoch
    private fun valid(kind: VehicleCommandKind, status: VehicleCommandStatus) = status.kind == kind && status.revision == scope?.revision && status.boot > 0
}

/** Positive boot evidence survives a later read failure; new statuses publish as one batch. */
internal suspend fun readVehicleCommandObservations(
    scope: VehicleCommandScope,
    kinds: List<VehicleCommandKind>,
    current: () -> VehicleCommandObservations?,
    publish: (VehicleCommandObservations) -> Unit,
    now: () -> Long,
    read: suspend (VehicleCommandKind) -> VehicleCommandStatus
): Boolean {
    var epoch = current()?.takeIf { it.scope == scope }?.epoch ?: return false
    val receipts = mutableMapOf<VehicleCommandKind, VehicleCommandObservation>()
    for (kind in kinds) {
        val status = read(kind)
        val receivedAt = now()
        val active = current()?.takeIf { it.scope == scope && it.epoch == epoch } ?: return false
        require(status.kind == kind && status.revision == scope.revision && status.boot > 0)
        val noted = active.bootEvidence(scope, epoch, status)
        publish(noted)
        epoch = noted.epoch
        receipts[kind] = VehicleCommandObservation(status, receivedAt)
    }
    val active = current() ?: return false
    val updated = active.snapshot(scope, epoch, receipts)
    if (active.scope != scope || active.epoch != epoch) return false
    publish(updated)
    return updated.entries == receipts && receipts.isNotEmpty()
}
