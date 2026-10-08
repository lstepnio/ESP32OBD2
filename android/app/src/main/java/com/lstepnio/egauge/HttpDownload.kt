package com.lstepnio.egauge

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** A size bound and one total deadline also cover a slow, fragmented response. */
internal suspend fun boundedHttpDownload(url: URL, maximumBytes: Int, timeoutMs: Long = 45_000,
    open: () -> HttpURLConnection = { url.openConnection() as HttpURLConnection },
    trusted: (URL) -> Boolean = ::isTrustedGitHubDownloadUrl): ByteArray {
    require(url.protocol == "https" && maximumBytes > 0)
    val connection = open().apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
        setRequestProperty("User-Agent", "ESP32OBD2-Android")
    }
    return try {
        resourceIo(timeoutMs, connection::disconnect) {
            val response = connection.responseCode
            require(response == HttpURLConnection.HTTP_OK) {
                when {
                    response == 403 || response == 429 -> "GitHub update check was rate limited"
                    response == 404 && url.host == "api.github.com" -> HOSTED_RELEASE_FEED_UNAVAILABLE
                    else -> "GitHub download failed ($response)"
                }
            }
            require(trusted(connection.url)) { "GitHub redirected to an untrusted host" }
            require(connection.contentLengthLong < 0 || connection.contentLengthLong <= maximumBytes) {
                "GitHub asset is larger than allowed"
            }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count <= maximumBytes - output.size()) { "GitHub asset is larger than allowed" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    } finally {
        connection.disconnect()
    }
}
