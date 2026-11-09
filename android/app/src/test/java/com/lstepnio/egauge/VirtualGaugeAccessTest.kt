package com.lstepnio.egauge

import com.lstepnio.egauge.virtual.VirtualAccessKind
import com.lstepnio.egauge.virtual.verifyVirtualAccess
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class VirtualGaugeAccessTest {
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val installation = "installation-a"
    private val day = 86_400L
    private val start = 100_000L

    private fun grant(kind: String, end: Long, issued: Long = start): String {
        val payload = "EGVG1|virtual-gauge|$installation|$kind|$start|$end|$issued".encodeToByteArray()
        val signer = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key.private)
            update(payload)
        }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return encoder.encodeToString(payload) + "." + encoder.encodeToString(signer.sign())
    }

    @Test fun trialHasAnExactEndAndAnIndependentOfflineRefreshWindow() {
        val end = start + 30 * day
        val initial = grant("Trial", end)
        assertEquals(VirtualAccessKind.Trial, verifyVirtualAccess(initial, key.public, installation, start)?.kind)
        assertNull(verifyVirtualAccess(initial, key.public, installation, start + 3 * day))
        val refreshed = grant("Trial", end, end - day)
        assertNotNull(verifyVirtualAccess(refreshed, key.public, installation, end - 1))
        assertNull(verifyVirtualAccess(refreshed, key.public, installation, end))
        assertNull(verifyVirtualAccess(grant("Trial", end + 1), key.public, installation, start))
    }

    @Test fun subscriptionIsInstallationBoundAndRejectsTampering() {
        val valid = grant("Subscription", start + 31 * day)
        assertEquals(VirtualAccessKind.Subscription, verifyVirtualAccess(valid, key.public, installation, start)?.kind)
        assertNull(verifyVirtualAccess(valid, key.public, "different-installation", start))
        val tampered = (if (valid.startsWith("A")) "B" else "A") + valid.drop(1)
        assertNull(verifyVirtualAccess(tampered, key.public, installation, start))
        assertNull(verifyVirtualAccess(valid, key.public, installation, start - 1))
    }
}
