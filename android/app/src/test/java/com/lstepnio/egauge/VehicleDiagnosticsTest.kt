package com.lstepnio.egauge
import com.lstepnio.egauge.ui.state.diagnosticSources
import org.junit.Assert.*
import org.junit.Test
class VehicleDiagnosticsTest {
    private fun packet() = javaClass.getResourceAsStream(
        "/diagnostics-tcm-v15.hex"
    )!!.bufferedReader().readText().trim().chunked(2).map {
        it.toInt(16).toByte()
    }.toByteArray()
    private fun rejected(b: ByteArray) {
        assertThrows(IllegalArgumentException::class.java) { GaugeProtocolCodec.diagnostics(b) }
    }

    @Test fun productionPacketPreservesAllCodesAndSource() {
        val d = GaugeProtocolCodec.diagnostics(packet())
        assertEquals("TCM", d.source)
        assertEquals(0x7e9, d.responder)
        assertEquals(28L, d.revision)
        assertEquals(listOf("U0121", "U0140", "P1DCA", "P1DF3"), d.categories!![0].codes)
        assertEquals(listOf(4, 3, 3), d.categories.map { it.codes.size })
        validateDiagnosticScope(d, "TCM", 28)
        assertThrows(IllegalArgumentException::class.java) { validateDiagnosticScope(d, "ECM", 28) }
        assertThrows(IllegalArgumentException::class.java) { validateDiagnosticScope(d, "TCM", 29) }
        val groups = diagnosticSources(d, 0, "TCM")
        assertEquals("Not checked", groups[0].status.title)
        assertEquals(4, groups[1].categories[0].faults.size)
    }

    @Test fun rejectsMalformedEvidence() {
        rejected(packet().apply { this[20] = 0xe8.toByte() })
        rejected(packet().apply { this[24] = 1 })
        rejected(packet().apply { this[34] = 33 })
        rejected(packet().apply { this[48] = 1 })
        rejected(
            packet().apply {
                this[42] = this[40]
                this[43] = this[41]
            }
        )
        rejected(packet().copyOf(247))
        rejected(packet().apply { this[2] = 3 })
    }

    @Test fun unavailableRetainsCodesAndReceiptExpires() {
        val d = GaugeProtocolCodec.diagnostics(
            packet().apply {
                this[32] = 2
                this[33] = 1
            }
        )
        assertFalse(d.categories!![0].fresh)
        val g = diagnosticSources(d, 0, "TCM")[1]
        assertTrue(g.categories[0].status.contains("Unavailable"))
        assertTrue(g.categories[0].faults.all { it.category.startsWith("Last checked") })
        assertEquals("Refreshing faults", diagnosticSources(d, 30_001, "TCM")[1].status.title)
        assertFalse(diagnosticCategoryAgeCurrent(d.categories[1], 120_001))
    }

    @Test fun emptyUnknownAndLegacyAreDistinct() {
        val e = packet().apply {
            fill(0, 32, 104)
            this[32] = 1
            this[33] = 3
            this[36] = 0x84.toByte()
            this[37] = 3
        }
        assertTrue(
            diagnosticSources(
                GaugeProtocolCodec.diagnostics(e),
                0,
                "TCM"
            )[1].categories[0].status.startsWith("No stored")
        )
        val u = packet().apply { fill(0, 32, 104) }
        assertEquals(
            "Not checked yet",
            diagnosticSources(GaugeProtocolCodec.diagnostics(u), 0, "TCM")[1].categories[0].status
        )
        val old = GaugeProtocolCodec.diagnostics(ByteArray(32).apply { this[0] = 5 })
        assertNull(old.categories)
        assertThrows(IllegalArgumentException::class.java) {
            validateDiagnosticScope(old, "TCM", 28)
        }
    }

    @Test fun maximumListAndClockWrapRemainBounded() {
        val b = packet().apply {
            this[34] = 32
            for (i in 0 until 32) {
                this[40 + 2 * i] = 0x01
                this[41 + 2 * i] = (i + 1).toByte()
            }
            fill(0, 12, 20)
            this[12] = 25
            fill(0xff.toByte(), 16, 20)
            this[16] = 0xcd.toByte()
            for (offset in listOf(36, 108, 180)) {
                fill(0xff.toByte(), offset, offset + 4)
                this[offset] =
                    0xcd.toByte()
            }
        }
        val d = GaugeProtocolCodec.diagnostics(b)
        assertEquals(32, d.categories!![0].codes.size)
        assertEquals(76L, d.categories[0].ageMs)
        assertTrue(diagnosticCategoryAgeCurrent(d.categories[0], 0))
        assertFalse(diagnosticCategoryAgeCurrent(d.categories[0].copy(ageMs = Long.MAX_VALUE), 1))
        assertThrows(IllegalArgumentException::class.java) {
            validateDiagnosticScope(d.copy(simulated = true), "TCM", 28)
        }
    }

    @Test fun liveWarningContextExpiresItsOwnReceiptAndRejectsRetiredEvidence() {
        val data = GaugeProtocolCodec.diagnostics(packet()).copy(
            connected = true,
            categories = listOf(
                DiagnosticCategory("Stored", DiagnosticAvailability.Available, true, true, listOf("P0730"), 90_000)
            )
        )
        fun category(elapsed: Long, snapshot: GaugeConfigTransferClient.Diagnostics = data) = diagnosticContextSnapshot(mapOf("TCM" to snapshot), mapOf("TCM" to 100_000L), 100_000L + elapsed, data.revision)
            .getJSONObject("TCM").getJSONArray("categories").getJSONObject(0)
        assertTrue(category(30_000).getBoolean("fresh"))
        assertFalse(category(30_001).getBoolean("fresh"))
        assertFalse(category(-1).getBoolean("fresh"))
        assertFalse(category(0, data.copy(connected = false)).getBoolean("fresh"))
        val ageExpired = data.copy(categories = data.categories!!.map { it.copy(ageMs = 120_000) })
        assertTrue(category(0, ageExpired).getBoolean("fresh"))
        assertFalse(category(1, ageExpired).getBoolean("fresh"))
        assertFalse(
            diagnosticContextSnapshot(mapOf("TCM" to data), emptyMap(), 100_000, data.revision)
                .getJSONObject("TCM").getJSONArray("categories").getJSONObject(0).getBoolean("fresh")
        )
        assertFalse(diagnosticContextSnapshot(mapOf("TCM" to data), mapOf("TCM" to 100_000), 100_000, data.revision!! + 1).has("TCM"))
        assertFalse(diagnosticContextSnapshot(mapOf("ECM" to data), mapOf("ECM" to 100_000), 100_000, data.revision).has("ECM"))
        assertFalse(diagnosticContextSnapshot(mapOf("TCM" to data.copy(simulated = true)), mapOf("TCM" to 100_000), 100_000, data.revision).has("TCM"))
    }
}
