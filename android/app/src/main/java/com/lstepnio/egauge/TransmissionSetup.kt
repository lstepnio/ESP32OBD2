package com.lstepnio.egauge

/** Reuse the saved adapter/profile identity and existing page editor. */
object TransmissionSetup {
    fun combinedPage(id: String = "page.tcm.overview") = GaugePageDraft(
        id, "TRANSMISSION", GaugeLayout.Dual, listOf("tcmgear", "tcmtemp"))

    fun draft(previous: Draft? = null): Draft {
        if (previous == null) return Draft(pidId = "tcmgear", source = "TCM",
            layout = GaugeLayout.Dual, pages = listOf(combinedPage()), alerts = emptyList())
        if (previous.pages.any { "tcmgear" in it.pidIds }) return previous
        val pages = if (previous.pages.size == 1 && previous.pages[0].pidIds == listOf("tcmtemp") &&
            previous.pages[0].layout == GaugeLayout.Numeric && previous.pages[0].name == "TCM temp (test)")
            listOf(combinedPage(previous.pages[0].id))
        else if (previous.pages.size < 8) previous.pages + GaugePageDraft(
            "page.tcm.gear".let { base -> generateSequence(base) { "$it.next" }.first { id -> previous.pages.none { it.id == id } } },
            "GEAR", GaugeLayout.Numeric, listOf("tcmgear"))
        else previous.pages
        val first = pages.first()
        return previous.copy(pidId = first.pidIds.first(), layout = first.layout, source = "TCM", pages = pages)
    }
}
