package com.lstepnio.egauge

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CatalogSearchOutcomesTest {
    @Test fun emptyVerifiedSetDistinguishesUnavailableTrustAndExpiredFromIncompatible() {
        CatalogSearchOutcomes().requireCandidates(false) // No rejection: ordinary incompatibility selection owns the error.
        for ((error, expected) in listOf(
            IOException("private URL") to "downloaded",
            CatalogTrustFailure() to "verified",
            CatalogDateFailure() to "expired",
            IllegalArgumentException("private payload") to "verified"
        )) {
            val outcomes = CatalogSearchOutcomes()
            outcomes.reject(error)
            try {
                outcomes.requireCandidates(false)
                fail("Failed catalogs must retain the failure category")
            } catch (failure: IOException) {
                assertTrue(failure.message!!.contains(expected))
                assertFalse(failure.message!!.contains("private"))
            }
            outcomes.requireCandidates(true) // A verified compatible candidate still wins.
            assertFalse(outcomes.summary().contains("private"))
        }
    }

    @Test fun cancellationPropagatesAndMixedFailuresRemainBoundedAndCategorized() {
        val outcomes = CatalogSearchOutcomes()
        repeat(20) { outcomes.reject(IOException("private URL $it")) }
        outcomes.reject(CatalogDateFailure())
        outcomes.reject(CatalogTrustFailure())
        assertEquals("network=20 date=1 trust=1", outcomes.summary())
        val cancellation = CancellationException("cancel")
        try {
            outcomes.reject(cancellation)
            fail("Cancellation must propagate")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
        try {
            outcomes.requireCandidates(false)
            fail("Must reject")
        } catch (failure: IOException) {
            assertTrue(failure.message!!.contains("verified"))
        }
    }
}
