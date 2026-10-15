package com.lstepnio.egauge

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class DiagnosticCodeFiltersTest {
    private val rule=DiagnosticCodeFilters.rule("ECM","p0420")
    private fun data(codes: List<String> = listOf("P0420")): GaugeConfigTransferClient.Diagnostics {
        return GaugeConfigTransferClient.Diagnostics(true,true,codes.size,true,codes.size,codes.firstOrNull(),true,0,null,true,0,null,
            milAgeMs=0,categories=listOf("Stored","Pending","Permanent").mapIndexed { index,name ->
                DiagnosticCategory(name,DiagnosticAvailability.Available,true,true,if(index==0)codes else emptyList(),0) })
    }
    @Test fun exactControllerFilterNeverChangesRawEvidence() {
        val raw=data();val result=DiagnosticCodeFilters.project(raw,0,listOf(rule))
        assertTrue(result.suppressMil);assertEquals(listOf("P0420"),result.hidden);assertTrue(result.visible.isEmpty())
        assertTrue(raw.milOn);assertEquals(listOf("P0420"),raw.categories!![0].codes)
        assertFalse(DiagnosticCodeFilters.project(raw,0,listOf(rule.copy(controller="TCM"))).suppressMil)
        assertFalse(DiagnosticCodeFilters.project(data(listOf("P0420","U0121")),0,listOf(rule)).suppressMil)
    }
    @Test fun unknownPartialStaleAndCountMismatchKeepWarning() {
        val raw=data()
        val invalid=listOf(raw.copy(milFresh=false),raw.copy(milAgeMs=60_001),raw.copy(reportedCount=2),
            raw.copy(reportedCount=0),raw.copy(connected=false),raw.copy(simulated=true),raw.copy(categories=null),
            raw.copy(categories=raw.categories!!.mapIndexed { i,c -> if(i==1)c.copy(fresh=false) else c }),
            raw.copy(categories=raw.categories!!.mapIndexed { i,c -> if(i==0)c.copy(truncated=true) else c }))
        invalid.forEach { assertFalse(DiagnosticCodeFilters.project(it,0,listOf(rule)).suppressMil) }
        assertFalse(DiagnosticCodeFilters.project(raw,30_001,listOf(rule)).suppressMil)
    }
    @Test fun onlyScopedDiagnosticNotificationsAndBannersAreFiltered() {
        val raw=data().copy(revision=5)
        val event=AlertEvent(1,1,1,100,32,AlertSeverity.Warning,false,AlertLifecycle.Active,1,false,1f,0f,"Check engine","codes",100,5)
        assertTrue(DiagnosticCodeFilters.filtersAlert(event,raw,0,listOf(rule)))
        assertFalse(DiagnosticCodeFilters.filtersAlert(event.copy(key=0),raw,0,listOf(rule)))
        assertFalse(DiagnosticCodeFilters.filtersAlert(event.copy(severity=AlertSeverity.Critical),raw,0,listOf(rule)))
        assertFalse(DiagnosticCodeFilters.filtersAlert(event.copy(revision=6),raw,0,listOf(rule)))
        assertFalse(DiagnosticCodeFilters.filtersAlert(event.copy(key=33),raw,0,listOf(rule)))
        val normal=com.lstepnio.egauge.ui.state.diagnosticSources(raw,0,"ECM",listOf(rule)).first()
        val expert=com.lstepnio.egauge.ui.state.diagnosticSources(raw,0,"ECM",listOf(rule),true).first()
        assertTrue(normal.categories.flatMap { it.faults }.isEmpty())
        assertEquals("Only filtered codes",normal.status.title)
        assertEquals("P0420",expert.categories.first().faults.first().code)
        assertTrue(expert.categories.first().faults.first().category.startsWith("Filtered"))
        assertEquals(com.lstepnio.egauge.core.designsystem.StatusTone.Warning,expert.status.tone)
    }
    @Test fun sharedFirmwarePacketUsesTheSameDistinctCodeAndMilPolicy() {
        val hex=javaClass.classLoader!!.getResourceAsStream("diagnostics-tcm-v15.hex")!!.bufferedReader().use { it.readText().trim() }
        val bytes=hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw=GaugeProtocolCodec.diagnostics(bytes)
        val rules=raw.categories!!.flatMap { it.codes }.distinct().map { DiagnosticCodeFilters.rule("TCM",it) }
        val projection=DiagnosticCodeFilters.project(raw,0,rules)
        assertTrue(projection.suppressMil);assertEquals(4,projection.hidden.size);assertTrue(projection.visible.isEmpty())
        assertTrue(raw.milOn);assertEquals(4,raw.reportedCount)
    }
    @Test fun childPoliciesJoinOneVehicleAndCanBeRemovedWithoutChangingRouting() {
        val childRule=rule.copy(controller="TCM")
        val parent=VehicleProfile("car","Car",Draft(),AdapterBinding("ecm","AA:BB:CC:DD:EE:01","public"),
            TransmissionConnection(AdapterBinding("tcm","AA:BB:CC:DD:EE:02","public"),TransmissionSetup.draft().copy(diagnosticFilters=listOf(childRule))))
        val dashboard=parent.dashboardDraft()
        assertEquals(listOf(childRule),dashboard.diagnosticFilters)
        val changed=parent.withDashboard(dashboard.copy(diagnosticFilters=emptyList()))
        assertTrue(changed.dashboardDraft().diagnosticFilters.isEmpty())
        assertEquals(parent.primaryAdapter,changed.primaryAdapter)
        assertEquals(parent.transmission!!.adapter,changed.transmission!!.adapter)
        assertEquals(dashboard.pages,changed.dashboardDraft().pages)
    }
    @Test fun configurationRoundTripPreservesRulesAndAllRendererCapabilities() {
        val draft=Draft(diagnosticFilters=listOf(rule))
        val template=java.io.File("src/main/assets/numeric_config_template.json").readText()
        val result=ConfigurationProjector.project(template,draft,"car",20,schemaVersion=2)
        val root=JSONObject(result.second.toString(Charsets.UTF_8))
        assertEquals(listOf(rule),DiagnosticCodeFilters.decode(root.getJSONArray("diagnosticFilters")))
        val document=GaugeConfigTransferClient.ActiveDocument(21,"a".repeat(64),result.second.size,"car",1,draft.pages.size,draft.alerts.size,root.toString())
        assertEquals(listOf(rule),GaugeDraftComparison.savedDraft(document,"car")!!.diagnosticFilters)
        assertFalse(com.lstepnio.egauge.ui.state.sameSettings(draft,draft.copy(diagnosticFilters=emptyList())))
        val capabilities=GaugeProtocolCodec.capabilities("{\"protocolMajor\":0,\"board\":\"test\",\"maxAdapterLinks\":1,\"configWrite\":false,\"ota\":false,\"cfg\":6}".toByteArray())
        assertEquals(GaugeLayout.entries.toSet(),capabilities.supportedRenderers)
        assertTrue(com.lstepnio.egauge.ui.state.presentationBlockers(draft,capabilities.copy(configurationVersion=5)).any { "CEL" in it })
        assertFalse(com.lstepnio.egauge.ui.state.presentationBlockers(draft,capabilities).any { "CEL" in it })
    }
    @Test fun boundedRulesRoundTripAndLegacyDefaults() {
        assertEquals(listOf(rule),DiagnosticCodeFilters.decode(DiagnosticCodeFilters.json(listOf(rule))))
        assertTrue(DiagnosticCodeFilters.decode(null).isEmpty())
        assertTrue(runCatching { DiagnosticCodeFilters.fromDocument(JSONObject().put("diagnosticFilters","corrupt")) }.isFailure)
        listOf("P0000","P042*","P4420","P0G20").forEach { assertTrue(runCatching { DiagnosticCodeFilters.rule("ECM",it) }.isFailure) }
        assertTrue(runCatching { DiagnosticCodeFilters.validate(listOf(rule,rule)) }.isFailure)
        val profiles=ProfileCollection("car",listOf(VehicleProfile("car","Car",Draft(diagnosticFilters=listOf(rule)))))
        assertEquals(profiles,ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(profiles)))
        val legacy=JSONObject(ProfileDocumentCodec.encode(profiles)).put("schemaVersion",12)
        legacy.getJSONArray("profiles").getJSONObject(0).getJSONObject("draft").remove("diagnosticFilters")
        assertTrue(ProfileDocumentCodec.decode(legacy.toString()).active.draft.diagnosticFilters.isEmpty())
    }
}
