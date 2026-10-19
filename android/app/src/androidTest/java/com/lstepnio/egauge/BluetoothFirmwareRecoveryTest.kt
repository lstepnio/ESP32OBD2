package com.lstepnio.egauge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.connection.ConnectionPhase
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in installation through the App owner, signed parser and durable update journal. */
@RunWith(AndroidJUnit4::class)
class BluetoothFirmwareRecoveryTest {
    private val args = InstrumentationRegistry.getArguments()
    private val allowed = args.getString("allowBluetoothFirmwareRecovery") == "true"

    @get:Rule val compose = activityTestRule(automaticConnection = allowed)

    @Test fun authorizedSignedBluetoothRecoveryPreservesSetup() {
        assumeTrue("Requires explicit owner authorization for signed Bluetooth recovery", allowed)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedVersion = requireNotNull(args.getString("expectedFirmwareVersion"))
        val expectedElf = requireNotNull(args.getString("expectedFirmwareElf"))
        val bundleFile =
            File(context.getExternalFilesDir(null), "authorized-recovery.egauge-dev-update")
        val bundle = bundleFile.inputStream().use { DevUpdateBundle.read(context, it) }
        assertEquals(expectedElf, bundle.elfSha256.joinToString("") { "%02x".format(it) })
        assertEquals(
            expectedVersion,
            bundle.image.copyOfRange(48, 80).takeWhile {
                it != 0.toByte()
            }.toByteArray().toString(Charsets.US_ASCII)
        )
        val model = (compose.activity.application as EGaugeApplication).model
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready &&
                model.activeDocument != null && model.displaySettings != null
        }
        val before = requireNotNull(model.activeDocument)
        val settings = requireNotNull(model.displaySettings)
        val profiles = model.profileCollection.profiles
        compose.runOnUiThread {
            model.updatePackageLoaded(bundle)
            model.installBluetoothRecoveryUpdate()
        }
        compose.waitUntil(1_200_000) {
            model.operation.kind == OperationKind.UPDATE &&
                model.operation.terminal
        }
        assertEquals("Firmware installed", model.operation.title)
        assertEquals(expectedVersion, model.bootIdentity?.version)
        assertEquals(expectedElf, model.bootIdentity?.elfSha256)
        assertEquals(2, model.bootIdentity?.otaState)
        compose.waitUntil(90_000) {
            model.connection.phase == ConnectionPhase.Ready &&
                model.activeDocument != null && model.displaySettings != null
        }
        assertEquals(before.revision, model.activeDocument?.revision)
        assertEquals(before.sha256, model.activeDocument?.sha256)
        assertEquals(settings, model.displaySettings)
        assertEquals(profiles, model.profileCollection.profiles)
        assertNull(UpdateRecoveryJournal(context).read())
        File(context.getExternalFilesDir(null), "bluetooth-recovery-result.txt").writeText(
            "Signed App/Bluetooth recovery verified: $expectedVersion\nELF: $expectedElf\n" +
                "OTA health 2; configuration revision ${before.revision}, digest, display settings and profiles preserved\n"
        )
    }
}
