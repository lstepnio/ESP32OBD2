package com.lstepnio.egauge

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionalUnitsTest {
    @Test fun newPreferencesFollowRegionRatherThanLanguage() {
        listOf("en-US", "es-US", "en-BS", "en-BZ", "en-KY", "es-PR", "en-PW").forEach {
            assertEquals(it, MeasurementSystem.Imperial, preferredMeasurementSystem(null, Locale.forLanguageTag(it)))
        }
        listOf("en-GB", "en-CA", "en-AU", "de-DE", "en-LR", "en-MM", "en", "und").forEach {
            assertEquals(it, MeasurementSystem.Metric, preferredMeasurementSystem(null, Locale.forLanguageTag(it)))
        }
    }
    @Test fun savedUnitsAndExplicitLocalePreferencesWinOverRegionalDefaults() {
        assertEquals(MeasurementSystem.Metric, preferredMeasurementSystem("Metric", Locale.US))
        assertEquals(MeasurementSystem.Imperial, preferredMeasurementSystem("Imperial", Locale.CANADA))
        assertEquals(MeasurementSystem.Imperial, preferredMeasurementSystem("invalid", Locale.US))
        assertEquals(MeasurementSystem.Metric, preferredMeasurementSystem(null, Locale.forLanguageTag("en-US-u-mu-celsius")))
        assertEquals(MeasurementSystem.Imperial, preferredMeasurementSystem(null, Locale.forLanguageTag("en-GB-u-mu-fahrenhe")))
        assertEquals(MeasurementSystem.Imperial, preferredMeasurementSystem(null, Locale.forLanguageTag("en-GB-u-rg-uszzzz")))
    }
}
