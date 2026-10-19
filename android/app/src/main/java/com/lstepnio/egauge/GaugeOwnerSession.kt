package com.lstepnio.egauge

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Raw transport is private to the session factory. Callers only receive transaction-scoped IO. */
internal interface GaugeOwnerChannel {
    val mtu: Int
    suspend fun read(): OwnerStateRead
    suspend fun write(bytes: ByteArray)
}

internal interface GaugeOwnerTransaction {
    val mtu: Int
    suspend fun readRaw(): ByteArray
    suspend fun writeRaw(bytes: ByteArray)
}

/** Multiple transactions may reuse this authenticated connection within the factory's deadline.
 * A failed/cancelled transaction retires it: delayed replies cannot belong to the next operation.
 * This is not a background owner or a connection cache. The App operation lease remains required.
 */
internal class GaugeOwnerSession private constructor(private val channel: GaugeOwnerChannel) {
    private val transactions = Mutex()
    private val att = Mutex()
    private val retired = AtomicBoolean(false)

    suspend fun <T> transaction(action: suspend GaugeOwnerTransaction.() -> T): T {
        check(currentCoroutineContext()[TransactionContext]?.session !== this) {
            "Gauge owner transactions cannot be nested"
        }
        return transactions.withLock {
            currentCoroutineContext().ensureActive()
            check(!retired.get()) { "Gauge owner session is retired" }
            val active = AtomicBoolean(true)
            val io = object : GaugeOwnerTransaction {
                override val mtu: Int get() = channel.mtu
                private fun checkActive() {
                    check(active.get() && !retired.get()) { "Gauge owner transaction is retired" }
                }
                private suspend fun <R> guarded(action: suspend () -> R): R = att.withLock {
                    try {
                        checkActive()
                        currentCoroutineContext().ensureActive()
                        action().also { checkActive() }
                    } catch (failure: Throwable) {
                        // Retire even when a caller catches the IO error inside its transaction.
                        retired.set(true)
                        throw failure
                    }
                }
                override suspend fun readRaw(): ByteArray = guarded {
                    // Retry only transient ATT resource pressure, never the preceding command.
                    repeat(4) { attempt ->
                        currentCoroutineContext().ensureActive()
                        val event = channel.read()
                        checkActive()
                        if (event.status == 0) return@guarded event.bytes.copyOf()
                        if (event.status != 0x11 || attempt == 3)
                            throw GaugeLinkException("Protected read failed (GATT ${event.status})")
                        delay(100L * (attempt + 1))
                    }
                    error("Protected status read retry budget exhausted")
                }
                override suspend fun writeRaw(bytes: ByteArray): Unit = guarded {
                    channel.write(bytes.copyOf())
                }
            }
            try {
                withContext(TransactionContext(this@GaugeOwnerSession)) { io.action() }
            } catch (failure: Throwable) {
                retired.set(true)
                throw failure
            } finally {
                active.set(false)
            }
        }
    }

    private class TransactionContext(val session: GaugeOwnerSession) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<TransactionContext>
    }

    companion object {
        internal suspend fun <T> use(channel: GaugeOwnerChannel, action: suspend (GaugeOwnerSession) -> T): T {
            // Preserve the existing bounded owner-read/reset policy before exposing any commands.
            establishProtectedOwner(read = channel::read, writeStatusReset = channel::write)
            val session = GaugeOwnerSession(channel)
            return try { action(session) } finally { session.retired.set(true) }
        }
    }
}

internal suspend fun <T> useGaugeOwnerSession(
    channel: GaugeOwnerChannel,
    action: suspend (GaugeOwnerSession) -> T,
): T = GaugeOwnerSession.use(channel, action)
