package com.lstepnio.egauge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

class DiagnosticCodeFiltersTest {
    private val rule = DiagnosticCodeFilters.rule("ECM", "p0420")
    private fun data(codes: List<String> = listOf("P0420")): GaugeConfigTransferClient.Diagnostics = GaugeConfigTransferClient.Diagnostics(
        true, true, codes.size, true, codes.size, codes.firstOrNull(), true, 0, null, true, 0, null,
        milAgeMs = 0,
        categories = listOf("Stored", "Pending", "Permanent").mapIndexed {
                index,
                name
            ->
            DiagnosticCategory(
                name,
                DiagnosticAvailability.Available,
                true,
                true,
                if (index ==
                    0
                ) {
                    codes
                } else {
                    emptyList()
                },
                0
            )
        }
    )

    @Test fun countZeroStartupIsNeitherAWarningNorAnAllClear() {
        val source = com.lstepnio.egauge.ui.state.diagnosticSources(data(emptyList()), 0, "ECM").first { it.title == "Engine" }
        assertEquals(com.lstepnio.egauge.core.designsystem.StatusTone.Loading, source.status.tone)
        assertTrue(source.categories.all { it.faults.isEmpty() })
    }

    @Test fun usableVisibleCodesAppearWithoutWaitingForEveryCategory() {
        val raw = data().let { it.copy(categories = it.categories!!.mapIndexed { index, category -> if (index == 1) category.copy(fresh = false) else category }) }
        val source = com.lstepnio.egauge.ui.state.diagnosticSources(raw, 0, "ECM").first { it.title == "Engine" }
        assertEquals(com.lstepnio.egauge.core.designsystem.StatusTone.Warning, source.status.tone)
        assertEquals("P0420", source.categories.flatMap { it.faults }.single().code)
    }

    @Test fun exactControllerFilterNeverChangesRawEvidence() {
        val raw = data()
        val result = DiagnosticCodeFilters.project(raw, 0, listOf(rule))
        assertTrue(result.suppressMil)
        assertEquals(listOf("P0420"), result.hidden)
        assertTrue(result.visible.isEmpty())
        assertTrue(raw.milOn)
        assertEquals(listOf("P0420"), raw.categories!![0].codes)
        assertFalse(
            DiagnosticCodeFilters.project(raw, 0, listOf(rule.copy(controller = "TCM"))).suppressMil
        )
        assertFalse(
            DiagnosticCodeFilters.project(
                data(listOf("P0420", "U0121")),
                0,
                listOf(rule)
            ).suppressMil
        )
    }

    @Test fun unknownPartialStaleAndCountMismatchKeepWarning() {
        val raw = data()
        val invalid =
            listOf(
                raw.copy(
                    milFresh = false
                ),
                raw.copy(milAgeMs = 60_001), raw.copy(reportedCount = 2),
                raw.copy(
                    reportedCount = 0
                ),
                raw.copy(
                    connected = false
                ),
                raw.copy(simulated = true), raw.copy(categories = null),
                raw.copy(
                    categories = raw.categories!!.mapIndexed { i, c ->
                        if (i ==
                            1
                        ) {
                            c.copy(fresh = false)
                        } else {
                            c
                        }
                    }
                ),
                raw.copy(
                    categories = raw.categories!!.mapIndexed { i, c ->
                        if (i ==
                            0
                        ) {
                            c.copy(truncated = true)
                        } else {
                            c
                        }
                    }
                )
            )
        invalid.forEach {
            assertFalse(DiagnosticCodeFilters.project(it, 0, listOf(rule)).suppressMil)
        }
        assertFalse(DiagnosticCodeFilters.project(raw, 30_001, listOf(rule)).suppressMil)
    }

    @Test fun sharedFirmwarePacketUsesTheSameDistinctCodeAndMilPolicy() {
        val hex = javaClass.classLoader!!.getResourceAsStream(
            "diagnostics-tcm-v15.hex"
        )!!.bufferedReader().use {
            it.readText().trim()
        }
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val raw = GaugeProtocolCodec.diagnostics(bytes)
        val rules = raw.categories!!.flatMap {
            it.codes
        }.distinct().map { DiagnosticCodeFilters.rule("TCM", it) }
        val projection = DiagnosticCodeFilters.project(raw, 0, rules)
        assertTrue(projection.suppressMil)
        assertEquals(4, projection.hidden.size)
        assertTrue(projection.visible.isEmpty())
        assertTrue(raw.milOn)
        assertEquals(4, raw.reportedCount)
    }

    @Category(DevelopmentConfiguration::class)
    @Test
    fun childPoliciesJoinOneVehicleAndCanBeRemovedWithoutChangingRouting() {
        val childRule = rule.copy(controller = "TCM")
        val parent =
            VehicleProfile(
                "car",
                "Car",
                Draft(),
                AdapterBinding("ecm", "AA:BB:CC:DD:EE:01", "public"),
                TransmissionConnection(
                    AdapterBinding("tcm", "AA:BB:CC:DD:EE:02", "public"),
                    TransmissionSetup.draft().copy(diagnosticFilters = listOf(childRule))
                )
            )
        val dashboard = parent.dashboardDraft()
        assertEquals(listOf(childRule), dashboard.diagnosticFilters)
        val changed = parent.withDashboard(dashboard.copy(diagnosticFilters = emptyList()))
        assertTrue(changed.dashboardDraft().diagnosticFilters.isEmpty())
        assertEquals(parent.primaryAdapter, changed.primaryAdapter)
        assertEquals(parent.transmission!!.adapter, changed.transmission!!.adapter)
        assertEquals(dashboard.pages, changed.dashboardDraft().pages)
    }

    @Test fun configurationRoundTripPreservesRulesAndAllRendererCapabilities() {
        val draft = Draft(diagnosticFilters = listOf(rule))
        val template = java.io.File("src/main/assets/numeric_config_template.json").readText()
        val result = ConfigurationProjector.project(template, draft, "car", 20, schemaVersion = 2)
        val root = JSONObject(result.second.toString(Charsets.UTF_8))
        assertEquals(
            listOf(rule),
            DiagnosticCodeFilters.decode(root.getJSONArray("diagnosticFilters"))
        )
        val document = GaugeConfigTransferClient.ActiveDocument(
            21,
            "a".repeat(64),
            result.second.size,
            "car",
            1,
            draft.pages.size,
            draft.alerts.size,
            root.toString()
        )
        assertEquals(
            listOf(rule),
            GaugeDraftComparison.savedDraft(document, "car")!!.diagnosticFilters
        )
        assertFalse(
            com.lstepnio.egauge.ui.state.sameSettings(
                draft,
                draft.copy(diagnosticFilters = emptyList())
            )
        )
        val capabilities = GaugeProtocolCodec.capabilities(
            "{\"protocolMajor\":0,\"board\":\"test\",\"maxAdapterLinks\":1,\"configWrite\":false,\"ota\":false,\"cfg\":6}".toByteArray()
        )
        assertEquals(GaugeLayout.entries.filter { it != GaugeLayout.DualArc }.toSet(), capabilities.supportedRenderers)
        assertTrue(
            com.lstepnio.egauge.ui.state.presentationBlockers(
                draft,
                capabilities.copy(configurationVersion = 5)
            ).any {
                "CEL" in
                    it
            }
        )
        assertFalse(
            com.lstepnio.egauge.ui.state.presentationBlockers(draft, capabilities).any {
                "CEL" in
                    it
            }
        )
    }

    @Test fun boundedRulesRoundTripAndLegacyDefaults() {
        assertEquals(
            listOf(rule),
            DiagnosticCodeFilters.decode(DiagnosticCodeFilters.json(listOf(rule)))
        )
        assertTrue(DiagnosticCodeFilters.decode(null).isEmpty())
        assertTrue(
            runCatching {
                DiagnosticCodeFilters.fromDocument(JSONObject().put("diagnosticFilters", "corrupt"))
            }.isFailure
        )
        listOf("P0000", "P042*", "P4420", "P0G20", "P04200", "P0420 abc").forEach {
            assertTrue(runCatching { DiagnosticCodeFilters.rule("ECM", it) }.isFailure)
        }
        assertTrue(runCatching { DiagnosticCodeFilters.validate(listOf(rule, rule)) }.isFailure)
        val profiles =
            ProfileCollection(
                "car",
                listOf(VehicleProfile("car", "Car", Draft(diagnosticFilters = listOf(rule))))
            )
        assertEquals(profiles, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(profiles)))
        val legacy = JSONObject(ProfileDocumentCodec.encode(profiles)).put("schemaVersion", 12)
        legacy.getJSONArray(
            "profiles"
        ).getJSONObject(0).getJSONObject("draft").remove("diagnosticFilters")
        assertTrue(
            ProfileDocumentCodec.decode(legacy.toString()).active.draft.diagnosticFilters.isEmpty()
        )
    }
}
