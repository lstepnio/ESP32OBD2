package com.lstepnio.egauge

import com.lstepnio.egauge.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class PresentationMapperTest {
    @Test fun browsingPagesDoesNotChangeWhatWasSent() {
        val sent = Draft()
        assertTrue(sameSettings(sent, sent.copy(pidId = "coolant", layout = GaugeLayout.Numeric)))
        assertFalse(sameSettings(sent, sent.copy(warning = 106)))
        assertFalse(sameSettings(sent, sent.copy(pages = sent.pages.reversed())))
        assertFalse(sameSettings(sent, sent.copy(source = "TCM")))
    }
    @Test fun unsupportedFieldsBlockSendInPlainLanguage() {
        val bad = Draft(source = "TCM", warning = 115, critical = 105)
        val blockers = presentationBlockers(bad, null)
        assertFalse(blockers.isEmpty())
        assertTrue(blockers.all { "TCM" !in it && "ECM" !in it && "PID" !in it && "hysteresis" !in it })
    }
    @Test fun previewUsesExactPageBindingsIncludingSecondReading() {
        val page = GaugePageDraft("two", "Two values", GaugeLayout.Dual, listOf("speed", "coolant"))
        val ui = pageUi(page)
        assertEquals("Vehicle speed", ui.preview.name)
        assertEquals("Coolant temperature", ui.preview.secondaryName)
        assertEquals("coolant", ui.secondaryId)
        assertTrue(ui.preview.description.startsWith("Preview."))
    }
    @Test fun unknownFaultsDoNotInventAnExplanation() {
        assertTrue(faultDescription("P1999").contains("not available"))
        assertTrue(faultDescription("P0301").contains("cylinder 1"))
    }
}
