package com.lstepnio.egauge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VehicleHierarchyTest {
    private val ecm = AdapterBinding("engine.adapter", "AA:BB:CC:DD:EE:01", "public")
    private val tcm = AdapterBinding("transmission.adapter", "AA:BB:CC:DD:EE:02", "public")
    private val jeep = VehicleProfile("jeep", "Jeep", Draft(), ecm, TransmissionConnection(tcm))
    private val other = VehicleProfile("other", "Other car", Draft(), ecm.copy(id = "other.adapter", address = "AA:BB:CC:DD:EE:03"))
    private val profiles = ProfileCollection(jeep.id, listOf(jeep, other))

    @Test fun nestedTransmissionRoundTripsWithoutConsumingAnotherVehicleSlot() {
        val decoded = ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(profiles))
        assertEquals(profiles, decoded)
        assertEquals(2, decoded.profiles.size)
        assertEquals(ecm, decoded.active.adapterFor("ECM"))
        assertEquals(tcm, decoded.active.adapterFor("TCM"))
        assertNull(decoded.profiles[1].draftFor("TCM"))
        val updated = jeep.withDraft(jeep.transmission!!.draft.copy(pages = listOf(TransmissionSetup.combinedPage("page.changed"))))
        assertEquals(jeep.draft, updated.draft)
        assertNotEquals(jeep.transmission, updated.transmission)
    }

    @Test fun legacyEngineAndTransmissionProfilesStaySeparateUntilExplicitlyAttached() {
        val legacy = VehicleProfile("legacy.tcm", "Transmission", TransmissionSetup.draft(), tcm)
        val saved = ProfileCollection(legacy.id, listOf(jeep.copy(transmission = null), legacy))
        val old = JSONObject(ProfileDocumentCodec.encode(saved)).put("schemaVersion", 5).toString()
        val decoded = ProfileDocumentCodec.decode(old)
        assertEquals(saved, decoded)
        val attached = decoded.attachTransmission(legacy.id, jeep.id)
        assertEquals(jeep.id, attached.activeId)
        assertEquals(1, attached.profiles.size)
        assertEquals(legacy.draft, attached.active.transmission?.draft)
        assertEquals(tcm, attached.active.adapterFor("TCM"))
    }

    @Test fun invalidParentDuplicateRadioAndWrongChildSourceAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { jeep.copy(primaryAdapter = null) }
        assertThrows(IllegalArgumentException::class.java) { jeep.copy(draft = TransmissionSetup.draft()) }
        assertThrows(IllegalArgumentException::class.java) { jeep.withAdapter("TCM", ecm.copy(id = "different.id", addressType = "random")) }
        assertThrows(IllegalArgumentException::class.java) { jeep.withAdapter("TCM", tcm.copy(id = ecm.id)) }
        assertThrows(IllegalArgumentException::class.java) { TransmissionConnection(tcm, Draft()) }
        assertEquals(jeep.transmission, jeep.withAdapter("ECM", other.primaryAdapter).transmission)
    }

    @Test fun childRemovalDoesNotChangeAnotherCarOrItsPrimarySetup() {
        val removed = jeep.copy(transmission = null)
        assertEquals(jeep.draft, removed.draft)
        assertEquals(ecm, removed.adapterFor("ECM"))
        assertNull(removed.draftFor("TCM"))
        val malformed = JSONObject(ProfileDocumentCodec.encode(profiles))
        malformed.getJSONArray("profiles").getJSONObject(0).remove("primaryAdapter")
        assertThrows(IllegalArgumentException::class.java) { ProfileDocumentCodec.decode(malformed.toString()) }
    }

    @Test fun gaugesRestoreTheirOwnVehicleAndSourceWithoutSubstitutingOtherContexts() {
        val saved = GaugeAssociations(null, emptyList()).remember("gauge-one", "Main gauge")
            .assign("gauge-one", jeep.id, "ECM").remember("gauge-two", "Transmission gauge")
            .assign("gauge-two", jeep.id, "TCM").remember("gauge-three", "Other gauge")
            .assign("gauge-three", other.id, "ECM")
        assertEquals(saved, GaugeAssociationDocumentCodec.decode(GaugeAssociationDocumentCodec.encode(saved)))
        assertEquals(jeep.id to jeep.draft, saved.context("gauge-one", profiles))
        assertEquals(jeep.id to jeep.transmission!!.draft, saved.context("gauge-two", profiles))
        assertEquals(other.id to other.draft, saved.context("gauge-three", profiles))
        assertNull(saved.context("unknown", profiles))
        assertNull(saved.context("gauge-two", profiles.copy(profiles = listOf(jeep.copy(transmission = null), other))))
        assertEquals(1, saved.remember("gauge-one", "Scan name").gauges.count { it.id == "gauge-one" })
        assertEquals("Main gauge", saved.remember("gauge-one", "Scan name").gauges.first().name)
        assertThrows(IllegalArgumentException::class.java) { saved.assign("unknown", jeep.id, "ECM") }
    }

    @Test fun gaugeAssociationCorruptionFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) { GaugeAssociations("missing", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { GaugeAssociations(null, listOf(KnownGauge("one", "One"), KnownGauge("one", "Two"))) }
        assertThrows(IllegalArgumentException::class.java) { KnownGauge("one", "One", jeep.id, "guess") }
        assertThrows(IllegalArgumentException::class.java) { GaugeAssociationDocumentCodec.decode("""{"schemaVersion":2,"gauges":[]}""") }
    }

    @Test fun singleSourceWireProjectionUsesChildBindingAndSameVehicleIdentity() {
        val template = File("src/main/assets/numeric_config_template.json").readText()
        val parent = JSONObject(ConfigurationProjector.project(template, jeep.draft, jeep.id, 10, jeep.adapterFor("ECM"), 2).second.toString(Charsets.UTF_8))
        val child = JSONObject(ConfigurationProjector.project(template, jeep.draftFor("TCM")!!, jeep.id, 10, jeep.adapterFor("TCM"), 2).second.toString(Charsets.UTF_8))
        assertEquals("jeep", parent.getString("vehicleProfileId"))
        assertEquals("jeep", child.getString("vehicleProfileId"))
        assertEquals(1, child.getJSONArray("sources").length())
        assertEquals("tcm", child.getJSONArray("sources").getJSONObject(0).getString("role"))
        assertEquals(tcm, AdapterBinding.decode(child.getJSONArray("sources").getJSONObject(0).getJSONObject("adapter")))
        assertEquals(ecm, AdapterBinding.decode(parent.getJSONArray("sources").getJSONObject(0).getJSONObject("adapter")))
    }
}
