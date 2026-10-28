package com.lstepnio.egauge

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Owner opt-in: hosted signed download and the ordinary App/Wi-Fi installation owner. */
@RunWith(AndroidJUnit4::class)
class WifiFirmwareJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowWifiFirmwareInstall") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun hostedSignedWifiInstallPreservesSetup() {
        assumeTrue("Requires owner authorization for full Wi-Fi firmware installation", allowed)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = (compose.activity.application as EGaugeApplication).model
        val expectedVersion = requireNotNull(args.getString("expectedFirmwareVersion"))
        val expectedElf = requireNotNull(args.getString("expectedFirmwareElf"))
        val activationPauseMs = args.getString("pauseBeforeActivationMs")?.toLong() ?: 0L
        require(activationPauseMs in 0..60_000)
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready &&
                model.activeDocument != null && model.displaySettings != null &&
                model.bootIdentity != null
        }
        assertTrue(
            model.capabilities?.wifiBulk in setOf(
                "experimental-softap-aead-v1",
                "experimental-softap-aead-v2"
            )
        )
        assertFalse(model.alertMonitoring)
        assertFalse(model.phoneAlertsEnabled)
        val beforeBoot = requireNotNull(model.bootIdentity)
        assertNotEquals(
            "A full transfer needs a different signed image",
            expectedElf,
            beforeBoot.elfSha256
        )
        val before = requireNotNull(model.activeDocument)
        val settings = requireNotNull(model.displaySettings)
        val profiles = model.profileCollection.profiles
        compose.runOnUiThread { model.checkHostedFirmware() }
        compose.waitUntil(120_000) { !model.hostedUpdateBusy }
        assertEquals(
            model.hostedUpdateMessage,
            expectedVersion,
            model.hostedUpdate?.release?.version
        )
        compose.runOnUiThread { model.downloadHostedFirmware() }
        compose.waitUntil(120_000) { !model.hostedUpdateBusy }
        val bundle = model.updateBundle()
        assertEquals(expectedElf, bundle.elfSha256.joinToString("") { "%02x".format(it) })
        val started = SystemClock.elapsedRealtime()
        val priorOperation = model.operation.id
        val observations = mutableListOf<Pair<Long, OperationState>>()
        compose.runOnUiThread {
            model.setDebugOtaPauseBeforeActivationMs(activationPauseMs)
            model.installSelectedUpdate()
        }
        try {
            compose.waitUntil(1_200_000) {
                val state = model.operation
                if (state.kind == OperationKind.UPDATE && state.id > priorOperation &&
                    observations.lastOrNull()?.second != state
                ) {
                    observations += SystemClock.elapsedRealtime() - started to state
                }
                state.kind == OperationKind.UPDATE && state.id > priorOperation && state.terminal
            }
        } finally {
            compose.runOnUiThread { model.setDebugOtaPauseBeforeActivationMs(0) }
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        assertEquals(model.operation.detail, "Firmware installed", model.operation.title)
        assertTrue(
            "Observed ordinary Wi-Fi sending path",
            observations.any { (_, state) ->
                state.stage == OperationStage.SENDING && state.detail?.contains("Wi-Fi") == true
            }
        )
        assertEquals(expectedVersion, model.bootIdentity?.version)
        assertEquals(expectedElf, model.bootIdentity?.elfSha256)
        assertEquals(2, model.bootIdentity?.otaState)
        assertNotEquals(
            "Alternate OTA slot is running",
            beforeBoot.partitionAddress,
            model.bootIdentity?.partitionAddress
        )
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready &&
                model.activeDocument != null && model.displaySettings != null
        }
        assertEquals(before.revision, model.activeDocument?.revision)
        assertEquals(before.sha256, model.activeDocument?.sha256)
        val afterSettings = requireNotNull(model.displaySettings)
        assertTrue("Settings support must not regress", afterSettings.smartPolicyVersion >= settings.smartPolicyVersion)
        // An upgrade may add negotiated policy metadata without changing saved preferences.
        assertEquals(settings, afterSettings.copy(smartPolicyVersion = settings.smartPolicyVersion))
        assertEquals(profiles, model.profileCollection.profiles)
        assertNull(UpdateRecoveryJournal(context).read())
        val stages = observations.distinctBy { it.second.stage }.joinToString("\n") { (ms, state) ->
            "$ms ms: ${state.stage}; ${state.title}; ${state.detail.orEmpty()}"
        }
        File(context.getExternalFilesDir(null), "wifi-firmware-result.txt").writeText(
            "Hosted signed App/Wi-Fi installation verified: $expectedVersion\nELF: $expectedElf\n" +
                "Image bytes: ${bundle.image.size}; install journey: $elapsed ms; debug activation pause: $activationPauseMs ms\n$stages\n" +
                "Alternate OTA slot, health 2 and cleared journal confirmed\n" +
                "Revision ${before.revision}, digest, settings and profiles preserved\n"
        )
    }
}
