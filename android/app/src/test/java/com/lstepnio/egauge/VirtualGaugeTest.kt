package com.lstepnio.egauge

import com.lstepnio.egauge.virtual.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VirtualGaugeTest {
    @Test fun fragmentedElmReplyDecodesOnlyThePrimaryEcuAndRequestedPid() {
        val prompt = ElmPrompt()
        assertNull(prompt.append("SEARCHING...\r7E9 04 41 0C 1A F8\r7E8 04 41".encodeToByteArray()))
        val response = requireNotNull(prompt.append(" 0C 0A 8C 00 00 00\r>".encodeToByteArray()))
        assertEquals(675.0, decodeVirtualReply(response, virtualPidsById.getValue("rpm"))!!, 0.0)
        assertNull(decodeVirtualReply(response, virtualPidsById.getValue("speed")))
        assertEquals(0.0, decodeVirtualReply("7e8 03 41 0d 00", virtualPidsById.getValue("speed"))!!, 0.0)
        assertEquals(45.0, decodeVirtualReply("7E8 03 41 05 55", virtualPidsById.getValue("coolant"))!!, 0.0)
    }

    @Test fun malformedAndAmbiguousFramesNeverBecomeMeasurements() {
        val invalid = listOf(
            "NO DATA",
            "STOPPED",
            "410C0A8C",
            "7E9 04 41 0C 0A 8C",
            "7E8 03 41 0C 0A 8C",
            "7E8 04 41 0C 0A",
            "7E8 04 41 0C +A 8C",
            "7E8 04 41 0C 0A 8C\r7E8 04 41 0C 0B 8C"
        )
        invalid.forEach { assertNull(it, decodeVirtualReply(it, virtualPidsById.getValue("rpm"))) }
        assertThrows(IllegalStateException::class.java) {
            ElmPrompt().append("OK\r>NO DATA\r>".encodeToByteArray())
        }
        assertThrows(IllegalStateException::class.java) {
            ElmPrompt().append("0".repeat(2_049).encodeToByteArray())
        }
    }

    @Test fun directDecoderUsesTheSharedCatalogVectors() {
        val definitions = JSONObject(File("src/main/assets/numeric_config_template.json").readText())
            .getJSONArray("definitions")
        val pids = readingCatalog.associateBy { it.definitionId }
        assertEquals(pids.values.map { it.id }.toSet(), virtualPidsById.keys)
        for (i in 0 until definitions.length()) {
            val definition = definitions.getJSONObject(i)
            val pid = virtualPidsById.getValue(pids.getValue(definition.getString("id")).id)
            assertEquals(pids.getValue(definition.getString("id")).request.replace(" ", ""), pid.request)
            val prefix = definition.getJSONObject("response").getString("prefix")
            val vectors = definition.getJSONArray("vectors")
            for (v in 0 until vectors.length()) {
                val vector = vectors.getJSONObject(v)
                val payload = (prefix + vector.getString("payloadHex")).chunked(2)
                val reply = "${pid.source.responder} 0${payload.size} ${payload.joinToString(" ")}"
                assertEquals(pid.id, vector.getDouble("expected"), decodeVirtualReply(reply, pid)!!, 1e-9)
            }
        }
        val oxygen = virtualPidsById.getValue("o2s1voltage")
        assertNull(decodeVirtualReply("7E8 03 41 14 81 FF", oxygen))
        assertEquals(0.645, decodeVirtualReply("7E8 04 41 14 81 FF", oxygen)!!, 0.0)
    }

    @Test fun reportedSupportFiltersChoicesButMissingOrConflictingBlocksStayUnknown() = runBlocking {
        val commands = mutableListOf<String>()
        val support = discoverVirtualEngineSupport {
            commands.add(it)
            when (it) {
                "0100" -> "7E9 06 41 00 FF FF FF FF\r7E8 06 41 00 00 10 00 01"
                "0120" -> "7E8 06 41 20 00 00 00 00\r7E8 06 41 20 FF FF FF FF"
                else -> error("Unexpected probe $it")
            }
        }
        assertEquals(listOf("0100", "0120"), commands)
        val choices = virtualReadingChoices(support)
        assertTrue("rpm" in choices)
        assertFalse("speed" in choices)
        assertTrue("voltage" in choices)
        assertEquals(BuildConfig.DEBUG, "tcmgear" in choices)

        val complete = discoverVirtualEngineSupport {
            assertEquals("0100", it)
            "7E8 06 41 00 04 10 00 00"
        }
        val enhanced = ConfigurationProjector.transmissionPidIds.takeIf { BuildConfig.DEBUG }.orEmpty()
        assertEquals(setOf("stft1", "rpm") + enhanced, virtualReadingChoices(complete))
        val offline = virtualReadingChoices(null)
        assertEquals(ConfigurationProjector.supportedPidIds + enhanced, offline)
        val noEngineReply = discoverVirtualEngineSupport { "7E9 06 41 00 00 00 00 00" }
        assertEquals(offline, virtualReadingChoices(noEngineReply))
    }

    @Test fun visibleReadsWinTiesWithoutStarvingOlderDueReads() {
        val rpm = virtualPidsById.getValue("rpm")
        val coolant = virtualPidsById.getValue("coolant")
        val pids = setOf(coolant, rpm)
        assertEquals(rpm, nextVirtualPid(pids, setOf(rpm.id), emptyMap(), 100))
        assertEquals(coolant, nextVirtualPid(pids, setOf(rpm.id), mapOf(rpm to 10L), 100))
        assertNull(nextVirtualPid(pids, setOf(rpm.id), mapOf(rpm to 101L, coolant to 101L), 100))
    }

    @Test fun liveFaceKeepsFreshSecondaryAndSuppressesExpiredPrimaryArc() {
        val page = GaugePageDraft("pair", "Engine", GaugeLayout.DualArc, listOf("rpm", "coolant"))
        val reads = mapOf(
            virtualPidsById.getValue("rpm") to listOf(VirtualSample(675.0, 1_000)),
            virtualPidsById.getValue("coolant") to listOf(VirtualSample(45.0, 5_000))
        )
        val face = virtualFace(page, reads, 6_000, MeasurementSystem.Imperial)
        assertEquals("--", face.shownValue)
        assertEquals("113", face.secondaryValue)
        assertEquals("°F", face.secondaryUnit)
        assertFalse(face.arcAvailable)
        assertTrue(face.trendFractions.isEmpty())
        assertTrue(face.description.contains("Engine speed, unavailable"))
        assertFalse(face.description.contains("Preview"))
        assertTrue(virtualFace(page.copy(arcMetricIndex = 1), reads, 6_000, MeasurementSystem.Imperial).arcAvailable)
    }

    @Test fun transmissionRequiresTheCapturedIdentityAndExactHeader() {
        val calibration = "7E9 10 13 49 04 01 36 38 32\r7E9 21 37 34 38 36 37 41 45\r7E9 22 00 00 00 00 00 00"
        val verification = "7E9 07 49 06 01 82 40 DA E8"
        assertTrue(matchesVirtualTransmission(calibration, verification))
        assertFalse(matchesVirtualTransmission(calibration.replace("7E9 22", "7E9 23"), verification))
        assertFalse(matchesVirtualTransmission(calibration, verification.replace("DA", "00")))
        assertFalse(matchesVirtualTransmission(calibration, verification.replace("7E9", "7E8")))
        assertEquals(45.0, decodeVirtualReply("7E9 06 62 04 FE 55 00 00", virtualPidsById.getValue("tcmtemp"))!!, 0.0)
        assertEquals(13.0, decodeVirtualReply("7E9 04 62 55 03 0D", virtualPidsById.getValue("tcmgear"))!!, 0.0)
        assertNull(decodeVirtualReply("7E8 04 62 55 03 0D", virtualPidsById.getValue("tcmgear")))
        assertNull(decodeVirtualReply("7E9 04 62 55 03 0C", virtualPidsById.getValue("tcmgear")))
    }

    @Test fun losingOneSourceLeavesTheOtherReadingVisible() {
        val page = GaugePageDraft("pair", "Engine and gear", GaugeLayout.Dual, listOf("rpm", "tcmgear"))
        val engine = VirtualLink(VirtualStatus.Waiting, mapOf(virtualPidsById.getValue("rpm") to listOf(VirtualSample(675.0, 1_000))))
        val tcm = VirtualLink(VirtualStatus.Waiting, mapOf(virtualPidsById.getValue("tcmgear") to listOf(VirtualSample(13.0, 1_000))))
        val units = MeasurementSystem.Imperial
        assertEquals("P", virtualFace(page, engine.readings + tcm.readings, 1_000, units).secondaryValue)
        val engineOnly = virtualFace(page, engine.readings, 1_000, units)
        assertEquals("675", engineOnly.shownValue)
        assertEquals("--", engineOnly.secondaryValue)
        val transmissionOnly = virtualFace(page, tcm.readings, 1_000, units)
        assertEquals("--", transmissionOnly.shownValue)
        assertEquals("P", transmissionOnly.secondaryValue)
        assertFalse(VirtualBindings("main", "child").accepts(VirtualSource.Transmission, "main"))
        assertNull(VirtualBindings(null, "child").address(VirtualSource.Transmission))
    }
}
