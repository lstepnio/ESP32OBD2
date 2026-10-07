package com.lstepnio.egauge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AdapterIntegrationTest {
    private val adapter = AdapterBinding("adapter-aabbccddeeff", "AA:BB:CC:DD:EE:FF", "random")
    private fun template() = File("src/main/assets/numeric_config_template.json").readText()

    @Test fun oldProfilesMigrateWithoutInventingAnAdapterAndNewProfilesKeepTheirSelection() {
        val collection = ProfileCollection("default", listOf(VehicleProfile("default", "Jeep", Draft(), adapter)))
        val encoded = ProfileDocumentCodec.encode(collection)
        assertEquals(collection, ProfileDocumentCodec.decode(encoded))
        val old = JSONObject(encoded).put("schemaVersion", 4).toString()
        assertNull(ProfileDocumentCodec.decode(old).active.primaryAdapter)
        assertEquals(collection.active.draft, ProfileDocumentCodec.decode(old).active.draft)
    }

    @Test fun bindingTravelsWithPagesAndProfileInTheSameDocumentAndCanBeRemoved() {
        val (_, bytes) = ConfigurationProjector.project(template(), Draft(), "vehicle-jeep", 21, adapter, 2)
        val document = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(2, document.getInt("schemaVersion"))
        assertEquals("vehicle-jeep", document.getString("vehicleProfileId"))
        assertEquals(adapter, AdapterBinding.decode(document.getJSONArray("sources").getJSONObject(0).getJSONObject("adapter")))
        val (_, removed) = ConfigurationProjector.project(template(), Draft(), "vehicle-other", 22, null, 2)
        assertFalse(JSONObject(removed.toString(Charsets.UTF_8)).getJSONArray("sources").getJSONObject(0).has("adapter"))
        assertEquals(adapter, ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(
            ProfileCollection("other", listOf(VehicleProfile("jeep", "Jeep", Draft(), adapter), VehicleProfile("other", "Other", Draft()))))).profiles[0].primaryAdapter)
    }

    @Test fun candidateAddressTypeIsPreservedAndMalformedTransportIsRejected() {
        val bytes = ByteArray(140)
        bytes[0] = 13; bytes[8] = 1
        byteArrayOf(0xff.toByte(), 0xee.toByte(), 0xdd.toByte(), 0xcc.toByte(), 0xbb.toByte(), 0xaa.toByte()).copyInto(bytes, 12)
        bytes[18] = 1; bytes[19] = 1
        "Test adapter".toByteArray().copyInto(bytes, 20)
        assertEquals(adapter, GaugeConfigTransferClient.decodeAdapters(bytes).single().binding)
        bytes[18] = 2
        assertThrows(IllegalArgumentException::class.java) { GaugeConfigTransferClient.decodeAdapters(bytes) }
        assertThrows(IllegalArgumentException::class.java) { adapter.copy(address = "aa:bb:cc:dd:ee:ff") }
        assertThrows(IllegalArgumentException::class.java) { adapter.copy(driver = "guess-from-name") }
    }

    @Test fun supportEvidenceIsAdvertisedPerEcuAndNeverPromotedToLiveReadings() {
        val bytes = ByteArray(160)
        bytes[0] = 14; bytes[1] = 4; bytes[3] = 1
        "vehicle-jeep".toByteArray().copyInto(bytes, 16)
        bytes[12] = 0xe8.toByte(); bytes[13] = 7
        bytes[120] = 1
        bytes[121] = 0x98.toByte(); bytes[122] = 0x18; bytes[124] = 1
        val status = GaugeConfigTransferClient.decodeAdapterStatus(bytes)
        assertEquals(0x7e8L, status.ecu)
        assertEquals(mapOf(0 to 0x98180001L), status.supportMaps)
        assertTrue(status.message.contains("still need"))
        assertEquals("vehicle-jeep", status.vehicleId)
        bytes[1] = 7
        assertThrows(IllegalArgumentException::class.java) { GaugeConfigTransferClient.decodeAdapterStatus(bytes) }
    }

    @Test fun simulatorLossIsUnavailableAndNeverShownAsVehicleReadiness() {
        val status = AdapterSourceStatus(4, 0, true, 1, 0, 0x7e8, "bench", "engine", true, emptyMap(), 100)
        assertTrue(status.message.contains("example readings"))
        assertTrue(status.copy(phase = 1).message.contains("unavailable"))
        assertFalse(status.copy(phase = 1).message.contains("Adapter ready"))
    }

    @Test fun newDevelopmentContractKeepsPublicClaimsDisabled() {
        val caps = GaugeProtocolCodec.capabilities("""{"protocolMajor":0,"board":"ESP32-S3-Touch-LCD-1.28","maxAdapterLinks":2,"configWrite":false,"ota":false,"cfg":3,"ad":1}""".toByteArray())
        assertEquals(1, caps.adapterRegistryVersion)
        assertEquals(GaugeLayout.entries.toSet(), caps.supportedRenderers)
        assertFalse(caps.configWrite); assertFalse(caps.simultaneousVerified); assertFalse(caps.ota)
    }
}
