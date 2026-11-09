package com.lstepnio.egauge

import com.lstepnio.egauge.premium.*
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class PremiumSubscriptionTest {
    private val monthly = SubscriptionPhase("$4.99", 4_990_000, "P1M", recurring = true, cycles = 0)
    private val trial = SubscriptionPhase("$0.00", 0, "P30D", recurring = false, cycles = 1)

    @Test fun displaysTheStoreOfferWithoutAssumingTrialEligibility() {
        assertEquals(SubscriptionTerms("$4.99 per month", "30 days free"), subscriptionTerms(listOf(trial, monthly)))
        assertEquals(SubscriptionTerms("$4.99 per month", null), subscriptionTerms(listOf(monthly)))
        assertEquals("1 month free", subscriptionTerms(listOf(trial.copy(period = "P1M"), monthly))?.trial)
    }

    @Test fun refusesTermsThatWouldMisrepresentPricing() {
        assertNull(subscriptionTerms(listOf(trial)))
        assertNull(subscriptionTerms(listOf(trial.copy(amountMicros = 100), monthly)))
        assertNull(subscriptionTerms(listOf(trial.copy(cycles = 2), monthly)))
        assertNull(subscriptionTerms(listOf(trial.copy(period = "P0D"), monthly)))
        assertNull(subscriptionTerms(listOf(monthly.copy(price = ""))))
    }

    @Test fun rejectsTamperedPurchasePayloadAndMalformedSignature() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2_048) }.generateKeyPair()
        val payload = """{"productId":"egauge_virtual_gauge","packageName":"com.lstepnio.egauge"}"""
        val signer = Signature.getInstance("SHA1withRSA").apply {
            initSign(keys.private)
            update(payload.encodeToByteArray())
        }
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        assertTrue(validPlaySignature(payload, signature, keys.public))
        assertFalse(validPlaySignature(payload.replace("egauge_virtual_gauge", "other"), signature, keys.public))
        assertFalse(validPlaySignature(payload, "not-a-signature", keys.public))
    }
}
