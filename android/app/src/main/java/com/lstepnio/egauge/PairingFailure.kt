package com.lstepnio.egauge

/** Stable protocol/Android reasons; text is presentation only. */
enum class PairingFailureReason(val code: String) {
    StartFailed("android_pair_start_failed"), BondIncomplete("android_bond_incomplete"),
    Failed("pairing_failed"), TimedOut("pairing_timeout"), Cancelled("pairing_cancelled"),
    InProgress("pairing_in_progress"), WindowClosed("pairing_window_closed"),
    AlreadyOwned("gauge_already_owned"), OwnerVerificationPending("owner_verification_pending");

    val refreshWindow: Boolean get() = this in setOf(Failed, TimedOut, Cancelled)
}
class PairingFailure(val reason: PairingFailureReason) : IllegalStateException(reason.code)

/** Retriable protected owner-read transport failure; malformed payloads use separate rejection. */
class OwnerReadFailure(message: String) : IllegalStateException(message)
