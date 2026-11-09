package com.lstepnio.egauge

import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DevUpdateBundleTest {
    private val keyGenerator = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }
    private val signer = keyGenerator.generateKeyPair()
    private val alternateBoard = "Synthetic-Test-Board"
    private val alternateTag = 0xf2434755L
    private val alternateTagBytes = byteArrayOf(0x55, 0x47, 0x43, 0xf2.toByte())

    @Test fun referenceDefaultsAcceptLegacyWireSignature() {
        val bundle = bundle(
            "ESP32-S3-Touch-LCD-1.28",
            0x31534745L,
            byteArrayOf(0x45, 0x47, 0x53, 0x31)
        )
        assertEquals(0x31534745L, DevUpdateBundle.read(bundle.inputStream(), signer.public).boardTag)
        assertThrows(IllegalArgumentException::class.java) {
            DevUpdateBundle.read(bundle.inputStream(), signer.public, alternateBoard, alternateTag)
        }
    }

    @Test fun explicitTargetRequiresMatchingLabelTagAndKey() {
        val bundle = bundle(alternateBoard, alternateTag, alternateTagBytes)
        assertEquals(
            alternateTag,
            DevUpdateBundle.read(bundle.inputStream(), signer.public, alternateBoard, alternateTag).boardTag
        )
        assertThrows(IllegalArgumentException::class.java) {
            DevUpdateBundle.read(bundle.inputStream(), signer.public)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DevUpdateBundle.read(bundle.inputStream(), signer.public, "Other-Board", alternateTag)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DevUpdateBundle.read(
                bundle.inputStream(),
                keyGenerator.generateKeyPair().public,
                alternateBoard,
                alternateTag
            )
        }
    }

    @Test fun signedTagCannotBeRelabeledOrTruncated() {
        val changedTag = 0x01020304L
        val relabeled = bundle(alternateBoard, changedTag, alternateTagBytes)
        assertThrows(IllegalArgumentException::class.java) {
            DevUpdateBundle.read(relabeled.inputStream(), signer.public, alternateBoard, changedTag)
        }
        val overflow = bundle(alternateBoard, alternateTag + 0x100000000L, alternateTagBytes)
        assertThrows(IllegalArgumentException::class.java) {
            DevUpdateBundle.read(overflow.inputStream(), signer.public, alternateBoard, alternateTag)
        }
    }

    private fun bundle(board: String, tag: Long, signedTagBytes: ByteArray): ByteArray {
        val image = ByteArray(1024)
        image[0] = 0xe9.toByte()
        byteArrayOf(0x32, 0x54, 0xcd.toByte(), 0xab.toByte()).copyInto(image, 32)
        val digest = MessageDigest.getInstance("SHA-256").digest(image)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(signer.private)
            update(signedTagBytes + byteArrayOf(0, 4, 0, 0) + digest)
            sign()
        }
        val metadata = JSONObject()
            .put("board", board)
            .put("boardTag", tag)
            .put("imageLength", image.size)
            .put("sha256", digest.toHexString())
            .put("signatureDerHex", signature.toHexString())
            .put("signaturePartBytes", 8)
            .put("transferProtocol", 0)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry("metadata.json"))
            it.write(metadata.toString().toByteArray(Charsets.UTF_8))
            it.closeEntry()
            it.putNextEntry(ZipEntry("firmware.bin"))
            it.write(image)
            it.closeEntry()
        }
        return output.toByteArray()
    }
}
