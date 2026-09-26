package com.lstepnio.egauge

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipInputStream

/** Bounded prototype update package. Signature validation here complements firmware verification. */
data class DevUpdateBundle(val image: ByteArray, val sha256: ByteArray, val signatureDer: ByteArray) {
    companion object {
        private const val board = "ESP32-S3-Touch-LCD-1.28"
        private const val boardTag = 0x31534745
        private val fields = setOf("board", "boardTag", "imageLength", "sha256",
            "signatureDerHex", "signaturePartBytes", "transferProtocol")

        fun read(context: Context, input: InputStream): DevUpdateBundle {
            var metadata: ByteArray? = null
            var image: ByteArray? = null
            ZipInputStream(input).use { archive ->
                var entries = 0
                while (true) {
                    val entry = archive.nextEntry ?: break
                    require(++entries <= 2) { "Update package has extra entries" }
                    when (entry.name) {
                        "metadata.json" -> {
                            require(metadata == null && !entry.isDirectory) { "Duplicate update metadata" }
                            metadata = bounded(archive, 4096)
                        }
                        "firmware.bin" -> {
                            require(image == null && !entry.isDirectory) { "Duplicate firmware image" }
                            image = bounded(archive, 0x300000)
                        }
                        else -> error("Unknown update package entry")
                    }
                    archive.closeEntry()
                }
            }
            val rawMetadata = metadata ?: error("Update metadata is missing")
            val bytes = image ?: error("Firmware image is missing")
            require(bytes.size in 1024..0x300000) { "Firmware image does not fit the OTA slot" }
            val json = JSONObject(rawMetadata.toString(Charsets.UTF_8))
            require(json.keys().asSequence().toSet() == fields) { "Unsupported update metadata fields" }
            require(json.getString("board") == board && json.getInt("boardTag") == boardTag &&
                json.getInt("transferProtocol") == 0 && json.getInt("signaturePartBytes") == 8 &&
                json.getInt("imageLength") == bytes.size) { "Update targets a different board or image" }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val expected = hex(json.getString("sha256"), 32)
            require(digest.contentEquals(expected)) { "Firmware SHA-256 does not match metadata" }
            val signature = hex(json.getString("signatureDerHex"), 64, 72)
            val publicPem = context.assets.open("dev-update-public.pem").bufferedReader().use { it.readText() }
            val base64 = publicPem.lineSequence().filter { !it.startsWith("-----") && it.isNotBlank() }.joinToString("")
            val key = KeyFactory.getInstance("EC").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(base64)))
            val signed = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(boardTag).putInt(bytes.size).put(digest).array()
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(signed)
            require(verifier.verify(signature)) { "Development update signature is invalid" }
            return DevUpdateBundle(bytes, digest, signature)
        }

        private fun bounded(input: InputStream, maximum: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) { "Update package entry is too large" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        private fun hex(value: String, minBytes: Int, maxBytes: Int = minBytes): ByteArray {
            require(value.length in minBytes * 2..maxBytes * 2 && value.length % 2 == 0 &&
                value.all { it in '0'..'9' || it in 'a'..'f' }) { "Invalid update metadata hex" }
            return ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
