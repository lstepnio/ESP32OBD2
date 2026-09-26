package com.lstepnio.egauge

import android.content.Context
import org.json.JSONObject

data class PendingUpdateRecovery(
    val gaugeId: String,
    val imageSha256: String,
    val stage: String,
    val updatedAtEpochMs: Long,
)

/** Minimal durable evidence for reconciling an interrupted foreground BLE update. */
class UpdateRecoveryJournal(context: Context) {
    private val preferences = context.getSharedPreferences("update-recovery", Context.MODE_PRIVATE)

    fun read(): PendingUpdateRecovery? = preferences.getString(KEY, null)?.let { raw ->
        runCatching {
            val value = JSONObject(raw)
            PendingUpdateRecovery(
                value.getString("gaugeId"),
                value.getString("imageSha256"),
                value.getString("stage"),
                value.getLong("updatedAtEpochMs"),
            )
        }.getOrNull()
    }

    fun write(gaugeId: String, imageSha256: String, stage: String) {
        val value = JSONObject()
            .put("gaugeId", gaugeId)
            .put("imageSha256", imageSha256)
            .put("stage", stage)
            .put("updatedAtEpochMs", System.currentTimeMillis())
        check(preferences.edit().putString(KEY, value.toString()).commit()) {
            "Could not save update recovery state"
        }
    }

    fun clear() {
        check(preferences.edit().remove(KEY).commit()) { "Could not clear update recovery state" }
    }

    companion object { private const val KEY = "pending-update" }
}
