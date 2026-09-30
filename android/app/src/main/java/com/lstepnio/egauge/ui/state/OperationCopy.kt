package com.lstepnio.egauge.ui.state

import androidx.compose.runtime.Immutable
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*

@Immutable
data class OperationUi(
    val id: Long = 0, val kind: OperationKind = OperationKind.READ,
    val status: StatusUi = StatusUi(""), val visible: Boolean = false,
    val busy: Boolean = false, val progress: Int? = null, val step: Int = 0,
    val needsCheck: Boolean = false, val update: Boolean = false,
)

/** Only a proven, terminal transfer success may leave the persistent activity area. */
fun successBannerMayDismiss(state: OperationUi): Boolean =
    state.visible && state.kind in setOf(OperationKind.CONFIGURATION, OperationKind.UPDATE) &&
        !state.busy && !state.needsCheck && state.status.tone == StatusTone.Success

/** Presentation must be stricter than a generic ACTIVE stage, which also represents successful reads. */
fun isConfirmedSetup(expectedRevision: Long?, expectedHash: String?,
                     runtime: GaugeConfigTransferClient.RuntimeIdentity?): Boolean =
    expectedRevision != null && expectedHash != null && runtime != null &&
        confirmRuntime(expectedRevision, expectedHash, runtime) == RuntimeConfirmation.ACTIVE

/** One mapping for all transfer banners, screen status, accessibility announcements and fixtures. */
fun operationUi(state: OperationState, confirmedSetup: Boolean = false): OperationUi {
    val update = state.kind == OperationKind.UPDATE
    val copy = when (state.stage) {
        OperationStage.IDLE -> StatusUi("")
        OperationStage.FINDING_GAUGE -> StatusUi("Finding your gauge", "Keep it powered and nearby.", StatusTone.Loading)
        OperationStage.CONNECTING -> StatusUi("Connecting to your gauge", "Keep it powered and nearby.", StatusTone.Loading)
        OperationStage.PREPARING -> StatusUi(if (update) "Preparing update" else "Checking your gauge",
            "Keep your gauge powered and the app open.", StatusTone.Loading)
        OperationStage.SENDING -> StatusUi("Sending to gauge", "Keep your gauge powered and the app open.", StatusTone.Loading)
        OperationStage.VERIFYING -> StatusUi("Checking the transfer", "Your gauge is checking what it received.", StatusTone.Loading)
        OperationStage.SAVED -> StatusUi("Saved. Waiting for your gauge", "Your settings are not confirmed running yet.", StatusTone.Loading)
        OperationStage.RESTARTING -> StatusUi("Restarting gauge", "Keep your gauge powered while it restarts.", StatusTone.Loading)
        OperationStage.CHECKING_RUNNING -> StatusUi("Checking your gauge", "Waiting for it to confirm everything is working.", StatusTone.Loading)
        OperationStage.ACTIVE -> when {
            update -> StatusUi("Update installed", "Your gauge confirmed it is running.", StatusTone.Success)
            state.kind == OperationKind.DISCOVERY -> StatusUi("Gauge found", "Pair it to make changes.")
            state.kind == OperationKind.CONFIGURATION && confirmedSetup ->
                StatusUi("Saved & running on gauge", "Your gauge confirmed these settings.", StatusTone.Success)
            state.kind == OperationKind.CONFIGURATION ->
                StatusUi("Gauge response received", "Check your gauge to confirm the change.")
            else -> StatusUi("Gauge checked", "Your gauge responded to the check.")
        }
        OperationStage.RECOVERED -> StatusUi("Earlier settings are running", "Your gauge restored its previous setup. Review your changes before sending again.", StatusTone.Stale)
        OperationStage.FAILED -> friendlyFailure(state.detail, update)
        OperationStage.OUTCOME_UNKNOWN -> StatusUi(if (update) "Update outcome is unknown" else "Your changes are unconfirmed",
            "The connection ended before confirmation. Check your gauge before trying again.", StatusTone.Error)
    }
    return OperationUi(state.id, state.kind, copy, state.stage != OperationStage.IDLE,
        !state.terminal && state.stage != OperationStage.IDLE, state.progressPercent,
        when (state.stage) {
            OperationStage.SENDING, OperationStage.VERIFYING -> 1
            OperationStage.SAVED -> if (update) 1 else 2
            OperationStage.RESTARTING -> 2
            OperationStage.CHECKING_RUNNING -> if (update) 2 else 3
            OperationStage.ACTIVE -> 3
            else -> 0
        }, state.stage in setOf(OperationStage.OUTCOME_UNKNOWN, OperationStage.RECOVERED, OperationStage.FAILED), update)
}

fun friendlyFailure(reason: String?, update: Boolean = false): StatusUi {
    val message = reason.orEmpty().lowercase()
    return when {
        "appearance settings" in message -> StatusUi("Your preference was not saved", "Try changing it again.", StatusTone.Error)
        reason == HOSTED_RELEASE_FEED_UNAVAILABLE -> StatusUi("Online updates unavailable",
            "This app cannot access the development release feed. Choose a signed package saved on your phone.", StatusTone.Stale)
        "permission" in message -> StatusUi("Nearby devices permission is needed", "Allow Nearby devices in Android settings, then reconnect.", StatusTone.Error)
        "bluetooth" in message && ("off" in message || "disabled" in message) ->
            StatusUi("Bluetooth is turned off", "Turn on Bluetooth, then find your gauge.", StatusTone.Offline)
        "revision" in message || "conflict" in message || "refresh gauge" in message ->
            StatusUi("Your gauge has different settings", "Check your gauge, then review the changes before sending.", StatusTone.Error)
        "owner" in message || "bond" in message || "pair" in message || "authenticat" in message ->
            StatusUi("Pairing needs another check", "Open pairing on the gauge, then reconnect this phone.", StatusTone.Error)
        "signature" in message || "digest" in message || "sha-256" in message || "catalog" in message ->
            StatusUi(if (update) "This update could not be verified" else "Your settings could not be verified",
                if (update) "Choose a new signed update package." else "Check your gauge, then review your settings.", StatusTone.Error)
        "no gauge" in message || "no compatible" in message || "timed out" in message ->
            StatusUi("Your gauge did not respond", "Keep it powered and nearby, then reconnect.", StatusTone.Offline)
        else -> StatusUi(if (update) "The update could not finish" else "Your gauge could not finish this request",
            "Keep your gauge powered and nearby, then check it again.", StatusTone.Error)
    }
}

fun updateRecoveryUi(recovery: UpdateRecoveryResult): StatusUi = when (recovery.state) {
    UpdateRecoveryState.CHECK_REQUIRED -> StatusUi("Update outcome is unknown", "Reconnect the same gauge and check the installed update before retrying.", StatusTone.Error)
    UpdateRecoveryState.INSTALLED -> StatusUi("Update installed", "Your gauge confirmed it is running.", StatusTone.Success)
    UpdateRecoveryState.PREVIOUS_FIRMWARE -> StatusUi("The new update is not running", "Your gauge is using a different version. Review the update before trying again.", StatusTone.Stale)
    UpdateRecoveryState.WAITING_FOR_CONFIRMATION -> StatusUi("Your gauge is still checking the update", "Keep it powered and check again before retrying.", StatusTone.Stale)
    UpdateRecoveryState.IDENTITY_CHECKED -> StatusUi("Installed version checked", "The previous update could not be identified. Review the installed version before retrying.", StatusTone.Stale)
}
