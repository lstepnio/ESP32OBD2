package com.lstepnio.egauge

internal class TransferCleanupState {
    var abortable = false
        private set
    fun preparing() {
        abortable = true
    }
    fun committing() {
        abortable = false
    }
}

internal data class TransferAbortState(val phase: Int, val transferId: Long)

/** Called on a fresh owner; never abort an unrelated or possibly committed transfer. */
internal suspend fun abortStagedTransfer(
    transferId: Long,
    read: suspend () -> TransferAbortState,
    abort: suspend () -> Unit
) {
    require(transferId in 1..0xffffffffL)
    val observed = read()
    if (observed.transferId == transferId && observed.phase in 1..3) abort()
}

/** The operation's connection closes before this bounded, noncancellable cleanup attempt. */
internal suspend fun <T> withStagedTransferCleanup(
    operation: suspend (TransferCleanupState) -> T,
    cleanup: suspend () -> Unit
): T {
    val state = TransferCleanupState()
    try {
        return operation(state)
    } catch (failure: Exception) {
        if (state.abortable) {
            try {
                boundedCleanup(3_000, cleanup)
            } catch (_: Exception) {
                // Preserve the operation's uncertainty/cancellation if cleanup itself times out.
            }
        }
        throw failure
    }
}
