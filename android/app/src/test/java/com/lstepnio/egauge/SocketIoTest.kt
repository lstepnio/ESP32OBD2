package com.lstepnio.egauge

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SocketIoTest {
    private fun server() = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 2_000 }
    private fun client(server: ServerSocket) = Socket().apply { connect(InetSocketAddress("127.0.0.1", server.localPort), 2_000) }

    @Test fun cancellationClosesSilentSocketAndReleasesWorker() = runBlocking<Unit> {
        server().use { server ->
            client(server).use { client ->
                server.accept().use {
                    val entered = CompletableDeferred<Unit>()
                    val task =
                        launch {
                            socketIo(client, 10_000) {
                                entered.complete(Unit)
                                client.getInputStream().read()
                            }
                        }
                    withTimeout(2_000) { entered.await() }
                    withTimeout(1_000) { task.cancelAndJoin() }
                    assertTrue(client.isClosed)
                }
            }
        }
    }

    @Test fun cancellationAlsoUnblocksAWriteUnderBackpressure() = runBlocking<Unit> {
        server().use { server ->
            client(server).use { client ->
                server.accept().use {
                    client.sendBufferSize = 1024
                    val entered = CompletableDeferred<Unit>()
                    val task = launch {
                        socketIo(client, 10_000) {
                            entered.complete(Unit)
                            client.getOutputStream().write(ByteArray(8 * 1024 * 1024))
                        }
                    }
                    withTimeout(2_000) { entered.await() }
                    delay(30)
                    assertTrue(task.isActive)
                    withTimeout(1_000) { task.cancelAndJoin() }
                    assertTrue(client.isClosed)
                }
            }
        }
    }

    @Test fun partialResponsesCannotExtendTotalDeadline() = runBlocking<Unit> {
        server().use { server ->
            client(server).use { client ->
                server.accept().use { peer ->
                    val executor = Executors.newSingleThreadExecutor()
                    try {
                        val writer = executor.submit {
                            try {
                                repeat(100) {
                                    peer.getOutputStream().write(1)
                                    Thread.sleep(20)
                                }
                            } catch (
                                _: Exception
                            ) { /* Deadline closes client. */ }
                        }
                        val started = System.nanoTime()
                        try {
                            socketIo(client, 150) {
                                repeat(100) { check(client.getInputStream().read() >= 0) }
                            }
                            fail("Trickle response must time out")
                        } catch (_: TimeoutCancellationException) { }
                        assertTrue(client.isClosed)
                        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000)
                        writer.cancel(true)
                    } finally {
                        executor.shutdownNow()
                    }
                }
            }
        }
    }

    @Test fun successfulExchangeKeepsSocketAndFailureInvalidatesIt() = runBlocking<Unit> {
        server().use { server ->
            client(server).use { client ->
                server.accept().use { peer ->
                    peer.getOutputStream().write(byteArrayOf(9, 8))
                    assertEquals(9, socketIo(client, 1_000) { client.getInputStream().read() })
                    assertEquals(8, socketIo(client, 1_000) { client.getInputStream().read() })
                    assertFalse(client.isClosed)
                    try {
                        socketIo(client, 1_000) { error("invalid authenticated response") }
                        fail()
                    } catch (
                        _: IllegalStateException
                    ) { }
                    assertTrue(client.isClosed)
                }
            }
        }
    }
}
