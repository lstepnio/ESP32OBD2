package com.lstepnio.egauge

import android.content.Context
import java.security.MessageDigest

internal enum class AutomaticUpdateDecision { CURRENT, READY, HELD, WAIT_FOR_GAUGE }

/** A failed release is never advertised again automatically on the same gauge. */
internal fun automaticUpdateDecision(
    runningVersion: String,
    otaState: Int,
    candidateVersion: String,
    candidateDigest: String,
    heldDigest: String?,
    unresolvedOutcome: Boolean,
): AutomaticUpdateDecision = when {
    unresolvedOutcome || otaState != 2 -> AutomaticUpdateDecision.WAIT_FOR_GAUGE
    !isFirmwareNewer(candidateVersion, runningVersion) -> AutomaticUpdateDecision.CURRENT
    candidateDigest == heldDigest -> AutomaticUpdateDecision.HELD
    else -> AutomaticUpdateDecision.READY
}

/** Keeps only the exact release that failed, scoped to its paired gauge. No image is stored here. */
internal class AutomaticUpdateHoldStore(context: Context) {
    private val preferences = context.getSharedPreferences("automatic-update-hold", Context.MODE_PRIVATE)
    private fun key(gaugeId: String): String = "held-" + MessageDigest.getInstance("SHA-256")
        .digest(gaugeId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun digestFor(gaugeId: String): String? = preferences.getString(key(gaugeId), null)

    fun hold(gaugeId: String, digest: String) {
        require(gaugeId.isNotBlank() && digest.matches(Regex("[0-9a-f]{64}")))
        check(preferences.edit().putString(key(gaugeId), digest).commit()) {
            "Could not remember the update that failed"
        }
    }

    fun clear(gaugeId: String) {
        preferences.edit().remove(key(gaugeId)).commit()
    }
}
