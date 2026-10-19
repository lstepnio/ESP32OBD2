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
        OperationStage.PREPARING -> when {
            update -> StatusUi("Preparing update", "Keep your gauge powered and the app open.", StatusTone.Loading)
            state.title == "Waiting for Android" -> StatusUi("Waiting for Android",
                "Enter the code shown on the gauge in Android's pairing prompt.", StatusTone.Loading)
            state.title == "Checking gauge access" -> StatusUi("Checking gauge access",
                "Confirming the saved owner and phone bond.", StatusTone.Loading)
            else -> StatusUi("Starting Android pairing", "Respond to the pairing prompt on your phone.", StatusTone.Loading)
        }
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
        OperationStage.OUTCOME_UNKNOWN -> if (state.title == "Android paired")
            StatusUi("Android paired. Checking gauge access",
                "The app will keep checking. Keep the gauge powered and nearby.", StatusTone.Stale)
        else StatusUi(if (update) "Update outcome is unknown" else "Your changes are unconfirmed",
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
    val updateTrust = update || listOf("firmware", "update", "catalog", "github").any { it in message }
    return when {
        "appearance settings" in message -> StatusUi("Your preference was not saved", "Try changing it again.", StatusTone.Error)
        reason == HOSTED_RELEASE_FEED_UNAVAILABLE -> StatusUi("Online updates unavailable",
            "This app cannot access the development release feed. Choose a signed package saved on your phone.", StatusTone.Stale)
        "signature" in message || "digest" in message || "sha-256" in message || "catalog" in message ||
            "untrusted" in message || "not trusted" in message || "bundle hash" in message ->
            StatusUi(if (updateTrust) "This update could not be verified" else "Your settings could not be verified",
                if (updateTrust) "Choose a new signed update package." else "Check your gauge, then review your settings.", StatusTone.Error)
        "no compatible development firmware" in message -> StatusUi("No compatible update available",
            "No signed release matches this gauge yet. Try again later.", StatusTone.Neutral)
        "rate limited" in message -> StatusUi("Update service is busy", "Try again later.", StatusTone.Offline)
        "github" in message || "online update" in message || "firmware download" in message ->
            StatusUi("Online updates unavailable", "Check your internet connection and try again.", StatusTone.Offline)
        "permission" in message -> StatusUi("Nearby devices permission is needed", "Allow Nearby devices in Android settings, then reconnect.", StatusTone.Error)
        "bluetooth" in message && ("off" in message || "disabled" in message) ->
            StatusUi("Bluetooth is turned off", "Turn on Bluetooth, then find your gauge.", StatusTone.Offline)
        "revision" in message || "conflict" in message || "refresh gauge" in message ->
            StatusUi("Your gauge has different settings", "Check your gauge, then review the changes before sending.", StatusTone.Error)
        "pairing_window_closed" in message ->
            StatusUi("Pairing is closed on the gauge", "Hold the screen until it says OPEN APP TO PAIR GAUGE, then tap Pair gauge.", StatusTone.Error)
        "gauge_already_owned" in message ->
            StatusUi("This gauge still has an owner", "To move it to this phone, hold the gauge screen for 12 seconds, release, then tap once to confirm. Display settings stay saved.", StatusTone.Error)
        "pairing_timeout" in message ->
            StatusUi("Pairing timed out", "Keep the gauge nearby, reopen its pairing window if needed, and tap Pair gauge to retry.", StatusTone.Stale)
        "android_bond_incomplete" in message ->
            StatusUi("Android did not finish pairing", "Accept the system prompt and enter the code shown on the gauge, then retry.", StatusTone.Error)
        "pairing_in_progress" in message ->
            StatusUi("Finish Android pairing", "Enter the code shown on the gauge in Android's pairing prompt.", StatusTone.Stale)
        "pairing_failed" in message ->
            StatusUi("Android pairing did not complete", "Enter the code shown on the gauge and confirm Android's prompt, then tap Pair gauge to try again.", StatusTone.Stale)
        "pairing_cancelled" in message || "cancelled" in message ->
            StatusUi("Pairing was cancelled", "Tap Pair gauge to try again, then accept Android's prompt and enter the gauge code.", StatusTone.Stale)
        "bonded_not_owner" in message ->
            StatusUi("This phone's saved bond does not own the gauge", "If the gauge is in pairing mode, forget eGauge in Android Bluetooth settings, then tap Pair gauge again.", StatusTone.Error)
        "android_pair_start_failed" in message ->
            StatusUi("Android could not start pairing", "Open Android Bluetooth settings, remove eGauge if it is listed, return here, and tap Pair gauge.", StatusTone.Error)
        "pairing_required" in message ->
            StatusUi("Pair this phone first", "Tap Pair gauge and accept Android's pairing prompt.", StatusTone.Stale)
        "owner" in message || "bond" in message || "pair" in message || "authenticat" in message ->
            StatusUi("Pairing needs another check", "Open pairing on the gauge, then reconnect this phone.", StatusTone.Error)
        "services changed" in message || "gatt 2" in message ->
            StatusUi("Gauge connection needs refreshing", "Reconnect the gauge to load its updated services, then check the saved result.", StatusTone.Stale)
        "wi-fi" in message || "wifi" in message || "network" in message ->
            StatusUi("Could not connect for the update", "Keep eGauge open near the gauge and retry the Wi-Fi connection.", StatusTone.Offline)
        "socket" in message || "connection reset" in message ->
            StatusUi("Update connection was interrupted", "Keep eGauge open, reconnect the gauge, then check the installed firmware before retrying.", StatusTone.Offline)
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
