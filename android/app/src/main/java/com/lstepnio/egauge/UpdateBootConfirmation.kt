package com.lstepnio.egauge

internal enum class UpdateBootDecision { WAIT, CONFIRMED, PREVIOUS_IMAGE }

/** Seeing the old healthy image immediately after activation is normal while
 * its reboot is pending. It is not evidence that the new trial rolled back. */
internal fun updateBootDecision(before: GaugeConfigTransferClient.BootIdentity,
    observed: GaugeConfigTransferClient.BootIdentity?, expectedElf: String,
    sawNewPartition: Boolean, deadlineExpired: Boolean): UpdateBootDecision {
    if (observed == null) return UpdateBootDecision.WAIT
    if (observed.partitionAddress != before.partitionAddress && observed.elfSha256 == expectedElf && observed.otaState == 2)
        return UpdateBootDecision.CONFIRMED
    if ((sawNewPartition || deadlineExpired) && observed.partitionAddress == before.partitionAddress &&
        observed.elfSha256 == before.elfSha256 && observed.otaState == 2) return UpdateBootDecision.PREVIOUS_IMAGE
    return UpdateBootDecision.WAIT
}
