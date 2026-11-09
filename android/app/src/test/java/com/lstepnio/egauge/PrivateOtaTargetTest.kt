package com.lstepnio.egauge

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivateOtaTargetTest {
    private fun pem(curve: String): String {
        val key = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec(curve))
            generateKeyPair().public
        }
        return "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getEncoder().encodeToString(key.encoded) + "\n-----END PUBLIC KEY-----\n"
    }

    @Test fun explicitTargetAcceptsUnsignedTagAndP256Key() {
        val target = PrivateOtaTarget.parse("""{"board":"Synthetic Board","boardTag":4294967295}""", pem("secp256r1"))
        assertEquals("Synthetic Board", target.board)
        assertEquals(0xffffffffL, target.boardTag)
    }

    @Test fun referenceIdentityAndInvalidTagsAreRejected() {
        val key = pem("secp256r1")
        val configs = listOf(
            """{"board":"Synthetic Board","boardTag":0}""",
            """{"board":"Synthetic Board","boardTag":4294967296}""",
            """{"board":"Synthetic Board","boardTag":${DevUpdateBundle.REFERENCE_BOARD_TAG}}""",
            """{"board":"${DevUpdateBundle.REFERENCE_BOARD}","boardTag":1}""",
            """{"board":"Synthetic Board","boardTag":"1"}"""
        )
        for (config in configs) {
            assertThrows(RuntimeException::class.java) { PrivateOtaTarget.parse(config, key) }
        }
    }

    @Test fun anotherEcCurveIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivateOtaTarget.parse("""{"board":"Synthetic Board","boardTag":1}""", pem("secp384r1"))
        }
    }
}
