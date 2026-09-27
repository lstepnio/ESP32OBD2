package com.lstepnio.egauge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class AppCoreTest {
    @Test fun hostedFirmwareComparisonFollowsSemanticVersionPrecedence() {
        assertTrue(isFirmwareNewer("0.2.0-dev.9", "0.2.0-dev.8"))
        assertTrue(isFirmwareNewer("0.2.0", "0.2.0-dev.99"))
        assertTrue(isFirmwareNewer("1.0.0-beta.1", "0.99.99"))
        assertFalse(isFirmwareNewer("0.2.0-dev.8", "0.2.0-dev.8"))
        assertFalse(isFirmwareNewer("0.2.0-dev.7", "0.2.0-dev.8"))
        assertThrows { isFirmwareNewer("unknown", "0.2.0-dev.8") }
        assertThrows { isFirmwareNewer("0.2.0-dev.9", "unknown") }
    }

    @Test fun runtimeMustMatchAndCompleteTrialBeforeActive() {
        val hash = "ab".repeat(32)
        assertEquals(RuntimeConfirmation.WAITING,
            confirmRuntime(4, hash, runtime(revision = 4, stored = 4, hash = hash, trial = true)))
        assertEquals(RuntimeConfirmation.ACTIVE,
            confirmRuntime(4, hash, runtime(revision = 4, stored = 4, hash = hash)))
        assertEquals(RuntimeConfirmation.RECOVERED,
            confirmRuntime(4, hash, runtime(revision = 3, stored = 4, hash = "cd".repeat(32), previous = true)))
        assertEquals(RuntimeConfirmation.REJECTED,
            confirmRuntime(4, hash, runtime(revision = 3, stored = 3, hash = hash)))
    }

    @Test fun observationsAgeAndNeverBecomeFreshAcrossClockReset() {
        val observed = Observed("gauge-a", 7, 1_000, "value")
        assertTrue(observed.isFresh(31_000, 30_000))
        assertFalse(observed.isFresh(31_001, 30_000))
        assertFalse(observed.isFresh(999, 30_000))
    }

    @Test fun operationCoordinatorRejectsOverlapAndReleasesLease() = runBlocking {
        val coordinator = OperationCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            coordinator.run(OperationKind.CONFIGURATION) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        var rejected = false
        try {
            coordinator.run(OperationKind.READ) { }
        } catch (_: OperationBusyException) {
            rejected = true
        }
        assertTrue(rejected)
        release.complete(Unit)
        first.await()
        coordinator.run(OperationKind.READ) { }
    }

    @Test fun projectionAndPayloadDescribeTheSameNumericPages() {
        val draft = Draft(pidId = "coolant", layout = GaugeLayout.Numeric, warning = 104,
            critical = 114, hysteresis = 3, triggerDwellMs = 2000, clearDwellMs = 5000)
        val (projection, bytes) = ConfigurationProjector.project(template, draft, "vehicle-1", 9)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals("engine.coolant", projection.pages.first().pidId)
        assertEquals("engine.coolant", json.getJSONArray("pages").getJSONObject(0)
            .getJSONArray("pidIds").getString(0))
        assertTrue(projection.pages.all { it.renderer == "numeric" })
        assertEquals(104, json.getJSONArray("alerts").getJSONObject(0).getInt("warning"))
        assertTrue(ConfigurationProjector.blockers(draft.copy(layout = GaugeLayout.Arc)).isNotEmpty())
        assertTrue(ConfigurationProjector.blockers(draft.copy(source = "TCM")).isNotEmpty())
    }

    @Test fun legacyTransmissionProfileMigratesToAdvancedTopologyWithoutChangingDraft() {
        val legacy = """{
          "schemaVersion":1,"activeId":"v1","profiles":[{
            "id":"v1","name":"Swap","draft":{"pidId":"tcm","layout":"Numeric",
            "warning":105,"critical":115,"hysteresis":3,"triggerDwellMs":1000,
            "clearDwellMs":2000,"source":"TCM"}}]}
        """.trimIndent()
        val migrated = ProfileDocumentCodec.decode(legacy)
        assertTrue(migrated.active.secondAdapterEnabled)
        assertEquals("TCM", migrated.active.draft.source)
        val roundTrip = ProfileDocumentCodec.decode(ProfileDocumentCodec.encode(migrated))
        assertEquals(migrated, roundTrip)
    }

    @Test fun protocolCodecRejectsMalformedBoundsAndParsesRuntime() {
        val capabilities = """{"board":"board","protocolMajor":0,"maxAdapterLinks":2,
          "configWrite":false,"ota":false,"experimentalNumericConfig":true,
          "savedStateRead":true,"quickSelect":true,
          "displayRotationWrite":true}""".trimIndent().toByteArray()
        val parsedCapabilities = GaugeProtocolCodec.capabilities(capabilities)
        assertEquals(2, parsedCapabilities.maxAdapterLinks)
        assertFalse(parsedCapabilities.simultaneousVerified)
        val invalid = capabilities.toString(Charsets.UTF_8).replace("\"maxAdapterLinks\":2", "\"maxAdapterLinks\":3")
        assertThrows { GaugeProtocolCodec.capabilities(invalid.toByteArray()) }

        val runtimeBytes = ByteArray(44)
        runtimeBytes[0] = 8
        runtimeBytes[1] = 1
        putU32(runtimeBytes, 4, 7)
        putU32(runtimeBytes, 8, 7)
        repeat(32) { runtimeBytes[12 + it] = it.toByte() }
        assertEquals(7, GaugeProtocolCodec.runtimeIdentity(runtimeBytes).revision)
        runtimeBytes[1] = 0x81.toByte()
        assertThrows { GaugeProtocolCodec.runtimeIdentity(runtimeBytes) }
    }

    @Test fun hostedCatalogRequiresValidSignatureAndExactCompatibility() {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val raw = """{"schemaVersion":1,"repository":"lstepnio/ESP32OBD2","generation":9,
          "generatedAt":"2026-09-26T00:00:00Z","expiresAt":"2027-03-25T00:00:00Z","releases":[{
          "version":"0.2.0-dev.4","releaseSequence":9,"channel":"development",
          "boardId":"waveshare-esp32-s3-touch-lcd-1.28","boardRevisions":["all"],
          "partitionLayout":"egauge-16m-ab-v1","transferProtocol":0,"bundleBytes":2048,
          "bundleSha256":"${"12".repeat(32)}",
          "bundleUrl":"https://github.com/lstepnio/ESP32OBD2/releases/download/dev-v0.2.0-dev.4/firmware.egauge-dev-update",
          "releaseNotes":"Test release","sourceTag":"dev-v0.2.0-dev.4"}]}""".trimIndent().toByteArray()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(raw)
        val signature = signer.sign()
        val catalog = HostedFirmwareCatalogCodec.verifyAndParse(raw, signature, keyPair.public, 1_796_000_000)
        assertEquals(9, catalog.generation)
        assertEquals("0.2.0-dev.4", HostedFirmwareCatalogCodec.select(catalog,
            "waveshare-esp32-s3-touch-lcd-1.28", "all", "egauge-16m-ab-v1",
            FirmwareChannel.DEVELOPMENT, 0)?.version)
        assertEquals(null, HostedFirmwareCatalogCodec.select(catalog,
            "another-board", "all", "egauge-16m-ab-v1", FirmwareChannel.DEVELOPMENT, 0))
        val tampered = raw.copyOf().also { it[it.lastIndex - 10] = 'X'.code.toByte() }
        assertThrows { HostedFirmwareCatalogCodec.verifyAndParse(tampered, signature, keyPair.public, 1_796_000_000) }
    }

    private fun runtime(revision: Long, stored: Long, hash: String, trial: Boolean = false,
                        previous: Boolean = false) = GaugeConfigTransferClient.RuntimeIdentity(
        running = true,
        usedPreviousGeneration = previous,
        trial = trial,
        revision = revision,
        storedRevision = stored,
        sha256 = hash,
    )

    private fun assertThrows(block: () -> Unit) {
        var threw = false
        try { block() } catch (_: Exception) { threw = true }
        assertTrue(threw)
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }

    private val template = """{
      "schemaVersion":1,"baseRevision":0,"vehicleProfileId":"default",
      "sources":[{"id":"source.ecm","role":"ecm"}],
      "definitions":[
        {"id":"engine.rpm","sourceId":"source.ecm"},
        {"id":"engine.coolant","sourceId":"source.ecm"},
        {"id":"vehicle.speed","sourceId":"source.ecm"}],
      "pages":[
        {"id":"page.engine","label":"Engine RPM","renderer":"numeric","pidIds":["engine.rpm"]},
        {"id":"page.thermal","label":"Coolant","renderer":"numeric","pidIds":["engine.coolant"]},
        {"id":"page.speed","label":"Speed","renderer":"numeric","pidIds":["vehicle.speed"]}],
      "alerts":[{"pidId":"engine.coolant","direction":"above","warning":105,"critical":115,
        "hysteresis":3,"triggerDwellMs":1000,"clearDwellMs":2000}]
    }""".trimIndent()
}
