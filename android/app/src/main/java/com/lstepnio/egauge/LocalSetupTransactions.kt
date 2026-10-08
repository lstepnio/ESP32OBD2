package com.lstepnio.egauge

class LocalSetupSaveFailure : IllegalStateException("Could not save the complete vehicle setup")

/** The returned setup is acknowledged only after both documents commit together. */
class LocalSetupTransactions(
    private val load: () -> LocalSetup,
    private val commit: (LocalSetup) -> Boolean,
    private val writes: DurableWrites,
) {
    suspend fun update(transform: (LocalSetup) -> LocalSetup): LocalSetup = writes.write {
        val previous = load()
        val next = transform(previous)
        if (!commit(next)) throw LocalSetupSaveFailure()
        next
    }
}
