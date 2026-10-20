package com.lstepnio.egauge

import com.lstepnio.egauge.ui.car.AirUpTimerDraft
import com.lstepnio.egauge.ui.car.airUpSeconds
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AirUpTimerTest {
    private val reference = AirUpTimer(15, 35, 15, 35, 480)

    @Test fun fourTireEstimateUsesTheMeasuredSetupAndRoundsUp() {
        assertEquals(480, reference.sessionSeconds)
        assertEquals(240, reference.copy(currentPsi = 25).sessionSeconds)
        assertEquals(97, reference.copy(currentPsi = 31, referenceSeconds = 481).sessionSeconds)
        assertFalse(reference.outsideReferenceRange)
        assertTrue(reference.copy(currentPsi = 10).outsideReferenceRange)
        assertEquals(300, reference.copy(overrideSeconds = 300).sessionSeconds)
    }

    @Test fun invalidPressureAndOutOfLimitTimesNeverSilentlyClamp() {
        for (bad in listOf<() -> AirUpTimer>(
            { reference.copy(targetPsi = 15) },
            { reference.copy(currentPsi = -1) },
            { reference.copy(targetPsi = 151) },
            { reference.copy(referenceToPsi = 15) },
            { reference.copy(referenceSeconds = 0) },
            { reference.copy(overrideSeconds = 601) }
        )) {
            assertTrue(runCatching(bad).isFailure)
        }
        val longFill = reference.copy(referenceSeconds = 1200)
        assertEquals(1200, longFill.estimatedSeconds)
        assertTrue(runCatching { longFill.sessionSeconds }.isFailure)
        assertEquals(600, longFill.copy(overrideSeconds = 600).sessionSeconds)
        assertTrue(runCatching { reference.copy(currentPsi = 34, referenceSeconds = 10).sessionSeconds }.isFailure)
    }

    @Test fun pressurePresetRoundtripsAndOldSchemasKeepOrdinaryTimers() {
        val preset = IdlePreset(IdlePresetId.AirUp, seconds = 480, airUp = reference)
        val collection = ProfileCollection("jeep", listOf(VehicleProfile("jeep", "Jeep", Draft(idlePresets = listOf(preset)))))
        val raw = ProfileDocumentCodec.encode(collection)
        assertEquals(17, JSONObject(raw).getInt("schemaVersion"))
        assertEquals(collection, ProfileDocumentCodec.decode(raw))
        assertTrue(runCatching { ProfileDocumentCodec.decode(JSONObject(raw).put("schemaVersion", 15).toString()) }.isFailure)
        val legacy = ProfileCollection("jeep", listOf(VehicleProfile("jeep", "Jeep", Draft(idlePresets = listOf(IdlePreset(IdlePresetId.AirUp))))))
        assertEquals(legacy, ProfileDocumentCodec.decode(JSONObject(ProfileDocumentCodec.encode(legacy)).put("schemaVersion", 15).toString()))
        val corrupt = IdlePresets.json(listOf(preset)).getJSONObject(0)
        corrupt.getJSONObject("airUp").put("currentPsi", 15.5)
        assertTrue(runCatching { IdlePresets.decode(JSONArray().put(corrupt)) }.isFailure)
        assertTrue(runCatching { preset.copy(seconds = 300) }.isFailure)
        assertTrue(runCatching { preset.copy(id = IdlePresetId.Winch) }.isFailure)
    }

    @Test fun pressureSettingsResolveToTheExistingDurationOnlyWire() {
        val preset = IdlePreset(IdlePresetId.AirUp, seconds = 480, airUp = reference)
        val state = VehicleCommandStatus(VehicleCommandKind.Idle, VehicleCommandPhase.Ready, revision = 7, link = 9, boot = 17, qualified = true)
        val bytes = VehicleCommandProtocol.request(state, false, preset)
        assertEquals(24, bytes.size)
        assertEquals(480, ByteBuffer.wrap(bytes, 20, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
        assertEquals(0, bytes[22].toInt())
        assertEquals(0, bytes[23].toInt())
    }

    @Test fun editorRequiresCalibrationAndKeepsExplicitOverrides() {
        val draft = AirUpTimerDraft(null)
        draft.enabled = true
        assertFalse(draft.valid)
        draft.current = "15"
        draft.target = "35"
        draft.from = "15"
        draft.to = "35"
        draft.measured = "8:00"
        assertTrue(draft.valid)
        assertEquals(480, draft.applyTo(IdlePreset(IdlePresetId.AirUp)).seconds)
        draft.override = "4:30"
        assertEquals(270, draft.applyTo(IdlePreset(IdlePresetId.AirUp)).seconds)
        draft.target = "15"
        assertFalse(draft.valid)
        draft.enabled = false
        assertNull(draft.applyTo(IdlePreset(IdlePresetId.AirUp)).airUp)
        assertNull(airUpSeconds("5:99"))
        assertNull(airUpSeconds("NaN"))
        assertEquals(330, airUpSeconds("5:30"))
    }
}
