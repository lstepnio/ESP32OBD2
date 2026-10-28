package com.lstepnio.egauge

import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.pageUi
import com.lstepnio.egauge.ui.state.presentationBlockers
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

class DisplayEvolutionTest {
    private val capabilities = """{"board":"board","protocolMajor":0,"maxAdapterLinks":1,"configWrite":false,"ota":false,"cfg":7,"ds":3,"sp":1,"va":1}"""
    private fun caps(extra: String = "") = GaugeProtocolCodec.capabilities((capabilities.dropLast(1) + extra + "}").toByteArray())
    private val arc = GaugePageDraft("page.dual", "Dual", GaugeLayout.DualArc, listOf("rpm", "coolant"), 1)

    @Test fun extensionsAreExplicitAndLegacyPageAndLayoutLimitsStayUnchanged() {
        assertEquals(8, caps().maxPages)
        assertFalse(GaugeLayout.DualArc in caps().supportedRenderers)
        assertEquals(10, caps(",\"mp\":10,\"dx\":1").maxPages)
        assertTrue(GaugeLayout.DualArc in caps(",\"dx\":1").supportedRenderers)
        assertEquals(3, GaugeProtocolCodec.capabilities(capabilities.replace("\"cfg\":7", "\"cfg\":1").toByteArray()).maxPages)
        listOf("0", "11", "true", "\"10\"", "1.5", "null").forEach { value ->
            assertTrue(runCatching { caps(",\"mp\":$value") }.isFailure)
        }
        listOf("2", "true", "\"1\"", "1.5", "null").forEach { value ->
            assertTrue(runCatching { caps(",\"dx\":$value") }.isFailure)
            assertTrue(runCatching { caps(",\"ao\":$value") }.isFailure)
        }
    }

    @Test fun tenPagesAndSelectedArcRoundTripWithoutWeakeningOldFirmwareDeploymentLimits() {
        val pages = (1..10).map { arc.copy(id = "page.$it") }
        val draft = Draft(pages = pages)
        val profile = VehicleProfile("vehicle", "Vehicle", draft, pageOrder = pages.map { it.id }.reversed())
        val profiles = ProfileCollection("vehicle", listOf(profile))
        val raw = ProfileDocumentCodec.encode(profiles)
        assertEquals(18, JSONObject(raw).getInt("schemaVersion"))
        assertEquals(profiles, ProfileDocumentCodec.decode(raw))
        assertTrue(ConfigurationProjector.blockers(draft).isEmpty())
        assertTrue(presentationBlockers(draft, caps()).any { "8 pages" in it })
        assertTrue(presentationBlockers(draft, caps()).any { "Dual + arc" in it })
        assertTrue(presentationBlockers(draft, caps(",\"mp\":10,\"dx\":1")).isEmpty())
        assertTrue(ConfigurationProjector.blockers(draft.copy(pages = pages + arc.copy(id = "page.11"))).isNotEmpty())
        val legacy = JSONObject(ProfileDocumentCodec.encode(ProfileCollection("v", listOf(VehicleProfile("v", "Vehicle", Draft()))))).put("schemaVersion", 17).toString()
        assertTrue(ProfileDocumentCodec.decode(legacy).active.draft.pages.all { it.arcMetricIndex == 0 })
        assertTrue(runCatching { ProfileDocumentCodec.decode(JSONObject(raw).put("schemaVersion", 19).toString()) }.isFailure)
    }

    @Test fun arcProjectsItsSelectedNumericMetricAndOrdinaryLayoutsOmitTheExtension() {
        val template = File("src/main/assets/numeric_config_template.json").readText()
        val draft = Draft(pages = listOf(arc, GaugePageDraft("page.numeric", "RPM", GaugeLayout.Numeric, listOf("rpm"))))
        val result = ConfigurationProjector.project(template, draft, "vehicle", 1, schemaVersion = 2)
        val pages = JSONObject(result.second.toString(Charsets.UTF_8)).getJSONArray("pages")
        assertEquals("dualArc", pages.getJSONObject(0).getString("renderer"))
        assertEquals(1, pages.getJSONObject(0).getInt("arcMetricIndex"))
        assertFalse(pages.getJSONObject(1).has("arcMetricIndex"))
        assertEquals(1, result.first.pages.first().arcMetricIndex)
        assertEquals(1, pageUi(arc).preview.arcMetricIndex)
        assertEquals(PreviewLayout.DualArc, pageUi(arc).preview.layout)
        assertTrue(ConfigurationProjector.blockers(draft.copy(pages = listOf(arc.copy(arcMetricIndex = 2)))).isNotEmpty())
        assertTrue(ConfigurationProjector.blockers(draft.copy(pages = listOf(arc.copy(layout = GaugeLayout.Dual)))).isNotEmpty())
    }

    @Category(DevelopmentConfiguration::class)
    @Test
    fun discreteGearCanShareADualArcPageButCannotDriveTheArc() {
        val page = arc.copy(pidIds = listOf("tcmgear", "tcmtemp"))
        assertTrue(ConfigurationProjector.blockers(Draft(pages = listOf(page))).isEmpty())
        assertTrue(ConfigurationProjector.blockers(Draft(pages = listOf(page.copy(arcMetricIndex = 0)))).any { "numeric arc" in it })
        assertTrue(pageUi(page).preview.primaryGear)
        assertFalse(pageUi(page).preview.secondaryGear)
    }

    @Test fun projectGearPaletteKeepsExplicitDriveSeparateFromNumericDecoding() {
        assertEquals(EGaugeTokens.Gauge.gearPark, gearPositionColor("P"))
        assertEquals(EGaugeTokens.Gauge.gearNeutral, gearPositionColor("N"))
        assertEquals(EGaugeTokens.Gauge.gearReverse, gearPositionColor("R"))
        (1..8).forEach { assertEquals(EGaugeTokens.Gauge.gearDrive, gearPositionColor(it.toString())) }
        assertEquals(EGaugeTokens.Gauge.gearDrive, gearPositionColor("D"))
        assertEquals(EGaugeTokens.Gauge.gearUnknown, gearPositionColor("--"))
    }

    @Test fun automaticOrientationCodecIsStrictAndIndependentOfSmartCycling() {
        assertEquals(0, caps().automaticOrientationVersion)
        assertEquals(1, caps(",\"ao\":1").automaticOrientationVersion)
        val frame = byteArrayOf(24, 1, 1, 2, 3, 2, 0, 0, 0x78, 0x56, 0x34, 0x12)
        assertEquals(GaugeOrientationSettings(true, 2, 3, 2, 0x12345678), GaugeProtocolCodec.orientationSettings(frame))
        assertTrue(GaugeProtocolCodec.isProtectedStatusFrame(frame))
        assertTrue(GaugeProtocolCodec.orientationSettings(frame).automaticAvailable)
        assertTrue(GaugeProtocolCodec.orientationSettings(frame.copyOf().apply { this[5] = 1 }).automaticAvailable)
        assertFalse(GaugeProtocolCodec.orientationSettings(frame.copyOf().apply { this[5] = 0 }).automaticAvailable)
        assertArrayEquals(byteArrayOf(0x6b, 0, 2, 0x78, 0x56, 0x34, 0x12), GaugeProtocolCodec.orientationSettingsWrite(false, 2, 0x12345678))
        listOf(0 to 23, 1 to 2, 2 to 2, 3 to 4, 4 to 4, 5 to 3, 6 to 1, 7 to 1).forEach { (index, invalid) ->
            assertTrue(runCatching { GaugeProtocolCodec.orientationSettings(frame.copyOf().apply { this[index] = invalid.toByte() }) }.isFailure)
        }
        assertTrue(runCatching { GaugeProtocolCodec.orientationSettings(frame.copyOf(11)) }.isFailure)
        assertTrue(runCatching { GaugeProtocolCodec.orientationSettingsWrite(true, 4, 1) }.isFailure)
    }
}
