package com.lstepnio.egauge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

enum class FirmwareChannel { DEVELOPMENT, BETA, STABLE }

internal data class FirmwareVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val prerelease: List<String>?,
) : Comparable<FirmwareVersion> {
    override fun compareTo(other: FirmwareVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (prerelease == null) return if (other.prerelease == null) 0 else 1
        if (other.prerelease == null) return -1
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[index]
            val right = other.prerelease[index]
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return compareValues(prerelease.size, other.prerelease.size)
    }

    companion object {
        private val pattern = Regex("([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-([0-9A-Za-z.-]+))?")

        fun parse(value: String): FirmwareVersion? {
            val match = pattern.matchEntire(value) ?: return null
            val core = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
            val prerelease = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.')
            if (prerelease?.any { it.isEmpty() } == true) return null
            return FirmwareVersion(core[0], core[1], core[2], prerelease)
        }
    }
}

internal fun isFirmwareNewer(candidate: String, running: String): Boolean {
    val candidateVersion = FirmwareVersion.parse(candidate)
        ?: error("Hosted firmware version is invalid")
    val runningVersion = FirmwareVersion.parse(running)
        ?: error("Running firmware version cannot be compared safely")
    return candidateVersion > runningVersion
}

internal fun isTrustedGitHubDownloadUrl(url: URL): Boolean =
    url.protocol == "https" && (url.host == "github.com" || url.host == "api.github.com" ||
        url.host.endsWith(".githubusercontent.com"))

data class HostedFirmwareRelease(
    val version: String,
    val releaseSequence: Long,
    val channel: FirmwareChannel,
    val boardId: String,
    val boardRevisions: List<String>,
    val partitionLayout: String,
    val transferProtocol: Int,
    val bundleBytes: Int,
    val bundleSha256: String,
    val bundleUrl: String,
    val releaseNotes: String,
    val sourceTag: String,
)

data class HostedFirmwareCatalog(
    val generation: Long,
    val generatedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val releases: List<HostedFirmwareRelease>,
)

object HostedFirmwareCatalogCodec {
    private val rootFields = setOf("schemaVersion", "repository", "generation", "generatedAt",
        "expiresAt", "releases")
    private val releaseFields = setOf("version", "releaseSequence", "channel", "boardId",
        "boardRevisions", "partitionLayout", "transferProtocol", "bundleBytes", "bundleSha256",
        "bundleUrl", "releaseNotes", "sourceTag")

    fun verifyAndParse(bytes: ByteArray, signatureDer: ByteArray, publicKey: PublicKey,
                       nowEpochSeconds: Long): HostedFirmwareCatalog {
        require(bytes.size in 1..131_072) { "Firmware catalog is too large" }
        require(signatureDer.size in 64..72) { "Firmware catalog signature is invalid" }
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(bytes)
        require(verifier.verify(signatureDer)) { "Firmware catalog signature is invalid" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.keys().asSequence().toSet() == rootFields && root.getInt("schemaVersion") == 1 &&
            root.getString("repository") == "lstepnio/ESP32OBD2") { "Unsupported firmware catalog" }
        val generated = Instant.parse(root.getString("generatedAt")).epochSecond
        val expires = Instant.parse(root.getString("expiresAt")).epochSecond
        require(generated <= nowEpochSeconds + 300 && expires > generated && expires >= nowEpochSeconds) {
            "Firmware catalog is expired or dated in the future"
        }
        val array = root.getJSONArray("releases")
        require(array.length() in 1..100) { "Firmware catalog has an invalid release count" }
        val releases = (0 until array.length()).map { parseRelease(array.getJSONObject(it)) }
        require(releases.map { it.releaseSequence }.distinct().size == releases.size) {
            "Firmware catalog has duplicate release sequences"
        }
        return HostedFirmwareCatalog(root.getLong("generation"), generated, expires, releases)
    }

    fun select(catalog: HostedFirmwareCatalog, boardId: String, boardRevision: String,
               partitionLayout: String, channel: FirmwareChannel,
               transferProtocol: Int): HostedFirmwareRelease? = catalog.releases
        .filter { release ->
            release.boardId == boardId && boardRevision in release.boardRevisions &&
                release.partitionLayout == partitionLayout && release.channel == channel &&
                release.transferProtocol == transferProtocol
        }.maxByOrNull { it.releaseSequence }

    fun publicKey(pem: String): PublicKey {
        val encoded = pem.lineSequence().filter { !it.startsWith("-----") && it.isNotBlank() }.joinToString("")
        return KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
    }

    private fun parseRelease(value: JSONObject): HostedFirmwareRelease {
        require(value.keys().asSequence().toSet() == releaseFields) { "Unsupported release fields" }
        val version = value.getString("version")
        require(FirmwareVersion.parse(version) != null) {
            "Firmware version is invalid"
        }
        val revisions = value.getJSONArray("boardRevisions").strings()
        require(revisions.isNotEmpty() && revisions.size <= 16 && revisions.all { it.length in 1..32 }) {
            "Board revision list is invalid"
        }
        val digest = value.getString("bundleSha256")
        require(digest.matches(Regex("[a-f0-9]{64}"))) { "Firmware bundle hash is invalid" }
        val url = value.getString("bundleUrl")
        require(isTrustedAssetUrl(url)) { "Firmware bundle URL is not trusted" }
        return HostedFirmwareRelease(
            version,
            value.getLong("releaseSequence").also { require(it in 1..0xffffffffL) },
            FirmwareChannel.valueOf(value.getString("channel").uppercase()),
            value.getString("boardId").also { require(it.length in 1..80) },
            revisions,
            value.getString("partitionLayout").also { require(it.length in 1..64) },
            value.getInt("transferProtocol").also { require(it in 0..255) },
            value.getInt("bundleBytes").also { require(it in 1024..0x340000) },
            digest,
            url,
            value.getString("releaseNotes").also { require(it.length <= 2000) },
            value.getString("sourceTag").also { require(it.matches(Regex("[A-Za-z0-9._-]{1,80}"))) },
        )
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    fun isTrustedAssetUrl(raw: String): Boolean = runCatching {
        val url = URL(raw)
        url.protocol == "https" && url.host == "github.com" &&
            url.path.startsWith("/lstepnio/ESP32OBD2/releases/download/")
    }.getOrDefault(false)
}

data class HostedUpdate(val release: HostedFirmwareRelease, val bundle: DevUpdateBundle? = null)

class GitHubFirmwareSource(private val context: Context) {
    private val releasesUrl = "https://api.github.com/repos/lstepnio/ESP32OBD2/releases?per_page=20"

    suspend fun check(capabilities: CapabilitySnapshot): HostedUpdate = withContext(Dispatchers.IO) {
        val boardId = when (capabilities.board) {
            "ESP32-S3-Touch-LCD-1.28" -> "waveshare-esp32-s3-touch-lcd-1.28"
            else -> error("This gauge board is not registered for hosted updates")
        }
        val releases = JSONArray(download(URL(releasesUrl), 1_048_576).toString(Charsets.UTF_8))
        var selectedCatalog: Pair<URL, URL>? = null
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.getBoolean("draft")) continue
            val assets = release.getJSONArray("assets")
            var catalog: URL? = null
            var signature: URL? = null
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                when (asset.getString("name")) {
                    "egauge-release-catalog.json" -> catalog = URL(asset.getString("browser_download_url"))
                    "egauge-release-catalog.sig" -> signature = URL(asset.getString("browser_download_url"))
                }
            }
            if (catalog != null && signature != null) {
                selectedCatalog = catalog to signature
                break
            }
        }
        val urls = selectedCatalog ?: error("No signed eGauge firmware catalog is published on GitHub")
        require(HostedFirmwareCatalogCodec.isTrustedAssetUrl(urls.first.toString()) &&
            HostedFirmwareCatalogCodec.isTrustedAssetUrl(urls.second.toString())) {
            "GitHub returned an untrusted catalog location"
        }
        val catalogBytes = download(urls.first, 131_072)
        val signatureBytes = download(urls.second, 256)
        val pem = context.assets.open("dev-update-public.pem").bufferedReader().use { it.readText() }
        val catalog = HostedFirmwareCatalogCodec.verifyAndParse(catalogBytes, signatureBytes,
            HostedFirmwareCatalogCodec.publicKey(pem), Instant.now().epochSecond)
        val catalogState = context.getSharedPreferences("firmware-catalog", Context.MODE_PRIVATE)
        val highestGeneration = catalogState.getLong("highest-generation", 0)
        require(catalog.generation >= highestGeneration) {
            "Firmware catalog is older than one this app has already trusted"
        }
        if (catalog.generation > highestGeneration) {
            check(catalogState.edit().putLong("highest-generation", catalog.generation).commit()) {
                "Could not save firmware catalog state"
            }
        }
        val compatible = HostedFirmwareCatalogCodec.select(catalog, boardId, "all",
            "egauge-16m-ab-v1", FirmwareChannel.DEVELOPMENT, 0)
            ?: error("No compatible development firmware is published for this gauge")
        HostedUpdate(compatible)
    }

    suspend fun download(update: HostedUpdate): HostedUpdate = withContext(Dispatchers.IO) {
        val release = update.release
        val bytes = download(URL(release.bundleUrl), release.bundleBytes + 1)
        require(bytes.size == release.bundleBytes) { "Downloaded firmware bundle size does not match the catalog" }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(digest == release.bundleSha256) { "Downloaded firmware bundle failed catalog verification" }
        val bundle = DevUpdateBundle.read(context, ByteArrayInputStream(bytes))
        HostedUpdate(release, bundle)
    }

    private fun download(url: URL, maximumBytes: Int): ByteArray {
        require(url.protocol == "https") { "Firmware downloads require HTTPS" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            setRequestProperty("User-Agent", "ESP32OBD2-Android")
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                if (connection.responseCode == 403) "GitHub update check was rate limited"
                else "GitHub download failed (${connection.responseCode})"
            }
            val finalUrl = connection.url
            require(isTrustedGitHubDownloadUrl(finalUrl)) { "GitHub redirected to an untrusted host" }
            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= maximumBytes) { "GitHub asset is larger than allowed" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maximumBytes) { "GitHub asset is larger than allowed" }
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
