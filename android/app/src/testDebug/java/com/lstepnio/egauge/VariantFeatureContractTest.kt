package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test

class VariantFeatureContractTest {
    @Test fun developmentCatalogContainsEngineAndTransmissionReadings() {
        assertTrue(BuildConfig.DEBUG)
        assertTrue(ConfigurationProjector.transmissionPidIds.isNotEmpty())
        assertEquals(ConfigurationProjector.supportedPidIds + ConfigurationProjector.transmissionPidIds, ConfigurationProjector.pagePidIds("ECM"))
        assertEquals(ConfigurationProjector.transmissionPidIds, ConfigurationProjector.pagePidIds("TCM"))
    }
}
