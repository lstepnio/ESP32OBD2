package com.lstepnio.egauge

import android.content.Context
import org.json.JSONObject

data class PendingUpdateRecovery(
    val gaugeId: String,
    val imageSha256: String,
    val expectedElfSha256: String?,
    val stage: String,
    val updatedAtEpochMs: Long,
)

enum class UpdateRecoveryState {
    CHECK_REQUIRED,
    INSTALLED,
    PREVIOUS_FIRMWARE,
    WAITING_FOR_CONFIRMATION,
    IDENTITY_CHECKED,
}

data class UpdateRecoveryResult(
    val state: UpdateRecoveryState,
    val message: String,
    val terminal: Boolean,
)

/** Resolve a durable journal entry only from authenticated running identity. */
fun reconcilePendingUpdate(
    pending: PendingUpdateRecovery,
    gaugeId: String?,
    runningElfSha256: String,
    otaState: Int,
): UpdateRecoveryResult {
    if (pending.gaugeId != "unknown" && pending.gaugeId != gaugeId) {
        return UpdateRecoveryResult(
            UpdateRecoveryState.CHECK_REQUIRED,
            "The interrupted update belongs to another gauge. Reconnect that gauge to finish recovery.",
            false,
        )
    }
    val expected = pending.expectedElfSha256
    if (expected == null) {
        return UpdateRecoveryResult(
            UpdateRecoveryState.IDENTITY_CHECKED,
            "Running firmware identity was verified. The older recovery record cannot identify the candidate image.",
            true,
        )
    }
    if (runningElfSha256 == expected && otaState == 2) {
        return UpdateRecoveryResult(
            UpdateRecoveryState.INSTALLED,
            "The interrupted update completed and the new firmware is confirmed healthy.",
            true,
        )
    }
    if (runningElfSha256 == expected && otaState in 0..1) {
        return UpdateRecoveryResult(
            UpdateRecoveryState.WAITING_FOR_CONFIRMATION,
            "The candidate firmware is running but has not passed its health check yet. Check again before retrying.",
            false,
        )
    }
    return UpdateRecoveryResult(
        UpdateRecoveryState.PREVIOUS_FIRMWARE,
        "The gauge is running a different confirmed image. The interrupted candidate was not activated or was rolled back.",
        true,
    )
}

/** Minimal durable evidence for reconciling an interrupted foreground BLE update. */
class UpdateRecoveryJournal(context: Context) {
    private val preferences = context.getSharedPreferences("update-recovery", Context.MODE_PRIVATE)

    fun read(): PendingUpdateRecovery? = preferences.getString(KEY, null)?.let { raw ->
        runCatching {
            val value = JSONObject(raw)
            PendingUpdateRecovery(
                value.getString("gaugeId"),
                value.getString("imageSha256"),
                value.optString("expectedElfSha256").takeIf { it.length == 64 },
                value.getString("stage"),
                value.getLong("updatedAtEpochMs"),
            )
        }.getOrNull()
    }

    fun write(gaugeId: String, imageSha256: String, expectedElfSha256: String, stage: String) {
        val value = JSONObject()
            .put("gaugeId", gaugeId)
            .put("imageSha256", imageSha256)
            .put("expectedElfSha256", expectedElfSha256)
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
