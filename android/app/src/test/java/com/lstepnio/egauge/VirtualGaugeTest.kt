package com.lstepnio.egauge

import com.lstepnio.egauge.virtual.*
import org.junit.Assert.*
import org.junit.Test

class VirtualGaugeTest {
    @Test fun fragmentedElmReplyDecodesOnlyThePrimaryEcuAndRequestedPid() {
        val prompt = ElmPrompt()
        assertNull(prompt.append("SEARCHING...\r7E9 04 41 0C 1A F8\r7E8 04 41".encodeToByteArray()))
        val response = requireNotNull(prompt.append(" 0C 0A 8C 00 00 00\r>".encodeToByteArray()))
        assertEquals(675.0, decodeVirtualReply(response, VirtualPid.Rpm)!!, 0.0)
        assertNull(decodeVirtualReply(response, VirtualPid.Speed))
        assertEquals(0.0, decodeVirtualReply("7e8 03 41 0d 00", VirtualPid.Speed)!!, 0.0)
        assertEquals(45.0, decodeVirtualReply("7E8 03 41 05 55", VirtualPid.Coolant)!!, 0.0)
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
        invalid.forEach { assertNull(it, decodeVirtualReply(it, VirtualPid.Rpm)) }
        assertThrows(IllegalStateException::class.java) {
            ElmPrompt().append("OK\r>NO DATA\r>".encodeToByteArray())
        }
        assertThrows(IllegalStateException::class.java) {
            ElmPrompt().append("0".repeat(2_049).encodeToByteArray())
        }
    }

    @Test fun allowlistedVehicleRequestsMatchTheSharedCatalog() {
        VirtualPid.entries.forEach { pid ->
            assertEquals(readingCatalog.single { it.id == pid.id }.request.replace(" ", ""), pid.request)
        }
    }

    @Test fun visibleReadsWinTiesWithoutStarvingOlderDueReads() {
        val rpm = VirtualPid.Rpm
        val coolant = VirtualPid.Coolant
        val pids = setOf(coolant, rpm)
        assertEquals(rpm, nextVirtualPid(pids, setOf(rpm.id), emptyMap(), 100))
        assertEquals(coolant, nextVirtualPid(pids, setOf(rpm.id), mapOf(rpm to 10L), 100))
        assertNull(nextVirtualPid(pids, setOf(rpm.id), mapOf(rpm to 101L, coolant to 101L), 100))
    }

    @Test fun liveFaceKeepsFreshSecondaryAndSuppressesExpiredPrimaryArc() {
        val page = GaugePageDraft("pair", "Engine", GaugeLayout.DualArc, listOf("rpm", "coolant"))
        val reads = mapOf(
            VirtualPid.Rpm to listOf(VirtualSample(675.0, 1_000)),
            VirtualPid.Coolant to listOf(VirtualSample(45.0, 5_000))
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
        assertEquals(45.0, decodeVirtualReply("7E9 06 62 04 FE 55 00 00", VirtualPid.TransmissionTemp)!!, 0.0)
        assertEquals(13.0, decodeVirtualReply("7E9 04 62 55 03 0D", VirtualPid.Gear)!!, 0.0)
        assertNull(decodeVirtualReply("7E8 04 62 55 03 0D", VirtualPid.Gear))
        assertNull(decodeVirtualReply("7E9 04 62 55 03 0C", VirtualPid.Gear))
    }

    @Test fun losingOneSourceLeavesTheOtherReadingVisible() {
        val page = GaugePageDraft("pair", "Engine and gear", GaugeLayout.Dual, listOf("rpm", "tcmgear"))
        val engine = VirtualLink(VirtualStatus.Waiting, mapOf(VirtualPid.Rpm to listOf(VirtualSample(675.0, 1_000))))
        val tcm = VirtualLink(VirtualStatus.Waiting, mapOf(VirtualPid.Gear to listOf(VirtualSample(13.0, 1_000))))
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
