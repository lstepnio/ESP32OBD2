package com.lstepnio.egauge

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

enum class OperationKind { DISCOVERY, READ, CONFIGURATION, UPDATE, DIAGNOSTIC_CLEAR }

enum class OperationStage {
    IDLE,
    FINDING_GAUGE,
    CONNECTING,
    PREPARING,
    SENDING,
    VERIFYING,
    SAVED,
    RESTARTING,
    CHECKING_RUNNING,
    ACTIVE,
    RECOVERED,
    FAILED,
    OUTCOME_UNKNOWN,
}

data class OperationState(
    val id: Long,
    val kind: OperationKind,
    val stage: OperationStage,
    val title: String,
    val detail: String? = null,
    val progressPercent: Int? = null,
    val terminal: Boolean = false,
) {
    init {
        require(progressPercent == null || progressPercent in 0..100)
    }

    companion object {
        val Idle = OperationState(0, OperationKind.READ, OperationStage.IDLE, "")
    }
}

class OperationBusyException : IllegalStateException("Another gauge operation is already running")

/** Owns the single app-side gauge operation lease. */
class OperationCoordinator {
    private val mutex = Mutex()
    private val sequence = AtomicLong(0)

    suspend fun <T> run(kind: OperationKind, block: suspend (Long) -> T): T {
        if (!mutex.tryLock()) throw OperationBusyException()
        return try {
            block(sequence.incrementAndGet())
        } finally {
            mutex.unlock()
        }
    }
}

