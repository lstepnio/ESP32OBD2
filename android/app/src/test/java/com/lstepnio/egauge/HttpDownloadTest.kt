package com.lstepnio.egauge

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HttpDownloadTest {
    private class Response(private val stream: InputStream, private val code: Int = 200,
        private val size: Long = -1, private val finalUrl: URL = URL("https://api.github.com/test")) :
        HttpURLConnection(finalUrl) {
        @Volatile var disconnected = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true; stream.close() }
        override fun getResponseCode() = code
        override fun getContentLengthLong() = size
        override fun getInputStream() = stream
    }
    private class StalledStream : InputStream() {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        @Volatile var left = false
        override fun read(): Int {
            entered.countDown()
            try { check(closed.await(2, TimeUnit.SECONDS)); throw IOException("closed") }
            finally { left = true }
        }
        override fun close() { closed.countDown() }
    }
    private val url = URL("https://api.github.com/test")
    private suspend fun read(response: Response, max: Int = 4, timeout: Long = 1_000) =
        boundedHttpDownload(url, max, timeout, { response })

    @Test fun completeBodyIsBoundedAndAlwaysClosed() = runBlocking {
        val response = Response(ByteArrayInputStream(byteArrayOf(1, 2, 3)), size = 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), read(response))
        assertTrue(response.disconnected)
    }
    @Test fun oversizedDeclaredAndChunkedBodiesAreRejected() = runBlocking {
        for (size in listOf(5L, -1L)) {
            val response = Response(ByteArrayInputStream(ByteArray(5)), size = size)
            try { read(response); fail("Accepted oversized body") } catch (_: IllegalArgumentException) {}
            assertTrue(response.disconnected)
        }
    }
    @Test fun untrustedRedirectAndRateLimitsFailBeforeReadingBody() = runBlocking {
        for (response in listOf(Response(ByteArrayInputStream(byteArrayOf(1)), finalUrl = URL("https://example.com/")),
            Response(ByteArrayInputStream(byteArrayOf(1)), code = 429))) {
            try { read(response); fail("Accepted rejected response") } catch (_: IllegalArgumentException) {}
            assertTrue(response.disconnected)
        }
    }
    @Test fun cancellationClosesAnActiveRequestAndWaitsForCleanup() = runBlocking {
        val stream = StalledStream(); val response = Response(stream)
        val job = launch { read(response) }
        withTimeout(1_000) { while (stream.entered.count > 0) delay(2) }
        job.cancelAndJoin()
        assertTrue(response.disconnected); assertTrue(stream.left)
    }
    @Test fun totalDeadlineRetiresAStalledBody() = runBlocking {
        val stream = StalledStream(); val response = Response(stream)
        try { read(response, timeout = 100); fail("Request exceeded total deadline") }
        catch (_: TimeoutCancellationException) {}
        assertTrue(response.disconnected); assertTrue(stream.left)
    }
    @Test fun slowFragmentsCannotExtendTheTotalBudget() = runBlocking {
        val stream = object : InputStream() {
            @Volatile var closed = false
            override fun read(): Int { Thread.sleep(25); return if (closed) -1 else 1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val result = read(); if (result < 0) return -1; b[off] = result.toByte(); return 1
            }
            override fun close() { closed = true }
        }
        val response = Response(stream)
        try { read(response, max = 100, timeout = 100); fail("Fragments extended the deadline") }
        catch (_: TimeoutCancellationException) {}
        assertTrue(stream.closed)
    }
}
