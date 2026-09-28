package com.lstepnio.egauge.connection

import com.lstepnio.egauge.OperationBusyException
import com.lstepnio.egauge.OperationCoordinator
import com.lstepnio.egauge.OperationKind
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/** Called on the UI dispatcher. Cancels only automatic reads, never a user transaction. */
class ForegroundConnectionController(
    scope: CoroutineScope,
    private val coordinator: OperationCoordinator,
    private val attempt: suspend () -> Long,
) {
    private val foreground = MutableStateFlow(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var automatic: Job? = null
    private var userOperations = 0

    init {
        scope.launch {
            foreground.collectLatest { visible ->
                if (visible) while (currentCoroutineContext().isActive) {
                    if (userOperations > 0) { wake.receive(); continue }
                    var pauseMs = 1_000L
                    coroutineScope {
                        val job = launch(start = CoroutineStart.LAZY) {
                            try { pauseMs = coordinator.run(OperationKind.READ) { attempt() } }
                            catch (_: OperationBusyException) { /* A user operation owns the lease. */ }
                        }
                        automatic = job
                        try { job.start(); job.join() }
                        finally { if (automatic === job) automatic = null }
                    }
                    if (userOperations == 0) withTimeoutOrNull(pauseMs.coerceAtLeast(1)) { wake.receive() }
                }
            }
        }
    }

    fun setForeground(value: Boolean) { foreground.value = value }
    fun retrySoon() { wake.trySend(Unit) }

    suspend fun <T> runUserOperation(kind: OperationKind, block: suspend (Long) -> T): T {
        userOperations++
        try {
            // Await the transport's finally/close before taking its existing operation lease.
            automatic?.cancelAndJoin()
            return coordinator.run(kind, block)
        } finally {
            userOperations--
            wake.trySend(Unit)
        }
    }
}

enum class ConnectionPhase { Idle, PermissionRequired, BluetoothOff, Searching, Checking, ChooseGauge, PairRequired, Ready, Retrying, Unavailable }

@androidx.compose.runtime.Immutable
data class ConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val checkedAtElapsedMs: Long? = null,
    val attempts: Int = 0,
    val detail: String? = null,
) {
    fun fresh(now: Long): Boolean = checkedAtElapsedMs?.let { now >= it && now - it <= 30_000 } == true &&
        phase in setOf(ConnectionPhase.Ready, ConnectionPhase.Checking)
}

/** Never silently substitutes another gauge for the remembered target. */
fun automaticCandidateId(ids: List<String>, remembered: String?): String? =
    if (remembered != null) ids.singleOrNull { it == remembered } else ids.singleOrNull()

fun reconnectDelayMs(failures: Int): Long = when (failures) {
    0, 1 -> 2_000
    2 -> 5_000
    3 -> 10_000
    4 -> 20_000
    else -> 30_000
}
