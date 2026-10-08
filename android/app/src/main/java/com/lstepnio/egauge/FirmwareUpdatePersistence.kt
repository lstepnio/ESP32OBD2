package com.lstepnio.egauge

import android.content.Context

/** Durable update evidence is separate from presentation and transport ownership. */
internal class FirmwareUpdatePersistence(context: Context, private val writes: DurableWrites) {
    private val journal = UpdateRecoveryJournal(context)
    private val hold = AutomaticUpdateHoldStore(context)
    fun pending(): PendingUpdateRecovery? = journal.read()
    fun heldDigest(gaugeId: String): String? = hold.digestFor(gaugeId)

    suspend fun start(gaugeId: String?, bundle: DevUpdateBundle, hosted: HostedUpdate?) = writes.write {
        journal.write(gaugeId ?: "unknown", bundle.sha256.hex(), bundle.elfSha256.hex(), "preparing")
        if (gaugeId != null && hosted?.bundle?.sha256?.contentEquals(bundle.sha256) == true)
            hold.hold(gaugeId, hosted.release.bundleSha256)
    }

    suspend fun interrupted(gaugeId: String?, bundle: DevUpdateBundle) = writes.write {
        journal.write(gaugeId ?: "unknown", bundle.sha256.hex(), bundle.elfSha256.hex(), "needs-reconciliation")
    }

    suspend fun clearRecovery() = writes.write { journal.clear() }
    suspend fun complete(gaugeId: String?) = writes.write {
        // Failed hold cleanup leaves recovery evidence for a fresh authenticated reconciliation.
        if (gaugeId != null) hold.clear(gaugeId)
        journal.clear()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
