package com.lstepnio.egauge

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.ui.state.PresentationPreferenceStore
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Isolated emulator only. Exercises the installed APK stores without Activity/radio startup. */
class WebsiteUpgradePreservationTest {
    @Test fun preserveLegacyFixture() {
        val phase = InstrumentationRegistry.getArguments().getString("upgradePhase")
        assumeTrue("Run explicitly with upgradePhase=seed or verify on a disposable emulator", phase in setOf("seed", "verify"))
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val seed = phase == "seed"
        val raw = """{"schemaVersion":1,"activeId":"website-upgrade","profiles":[{"id":"website-upgrade","name":"Upgrade example","draft":{"pidId":"rpm","layout":"Arc","source":"ECM","warning":103,"critical":113}}]}"""
        val preferences = context.getSharedPreferences("profiles-v1", 0)
        if (seed) {
            check(preferences.edit().putString("collection", raw).commit())
            check(context.getSharedPreferences("presentation", 0).edit().putBoolean("advanced", false).putBoolean("dynamic-colour", false).putString("gauge-name", "Upgrade gauge").putString("measurement-system", "Metric").commit())
        }
        val loaded = ProfileStore(context).load()
        check(loaded.error == null) { loaded.error.orEmpty() }
        check(loaded.collection.activeId == "website-upgrade")
        check(loaded.collection.active.name == "Upgrade example")
        check(loaded.collection.active.draft.pidId == "rpm")
        check(loaded.collection.active.draft.alerts.single().warning == 103.0)
        val appearance = PresentationPreferenceStore(context).read()
        check(appearance.gaugeName == "Upgrade gauge" && !appearance.advanced && !appearance.dynamicColor)
        val repository = AlertRepository(context)
        val event = AlertEvent(1, 1, 1, 0, 0, AlertSeverity.Warning, false, AlertLifecycle.Active, 1, false, 108f, 105f, "Coolant", "degC", 0, 1)
        val eventJson = alertJson(event).toString()
        val db = repository.writableDatabase
        if (seed) {
            db.execSQL("INSERT INTO episodes(id,vehicle,gauge,recorded,active,json,context) VALUES(?,?,?,?,?,?,?)", arrayOf<Any>("website-history", "website-upgrade", "synthetic-gauge", 1700000000000L, 0, eventJson, "{}"))
            db.execSQL("INSERT INTO events VALUES(?,?,?,?,?,?)", arrayOf<Any>("website-event", "website-history", "website-upgrade", "synthetic-gauge", 1700000000000L, eventJson))
        }
        check(repository.list("website-upgrade").size == 1)
        db.rawQuery("SELECT json FROM events WHERE id='website-event'", null).use {
            check(it.moveToFirst() && it.getString(0) == eventJson)
        }
        check(preferences.getString("collection", null) == raw)
        val evidence = JSONObject().put("phase", if (seed) "old51-seeded-and-decoded" else "new53-decoded")
            .put("installedVersion", context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
            .put("rawProfile", JSONObject(raw)).put("profileError", loaded.error)
            .put("decodedProfile", ProfileDocumentCodec.encode(loaded.collection))
            .put("presentation", context.getSharedPreferences("presentation", 0).all.toString())
            .put("historyEvent", JSONObject(eventJson)).put("historyCount", repository.list("website-upgrade").size)
            .put("databaseVersion", db.version)
        context.filesDir.resolve("website-upgrade-${if (seed) "before" else "after"}.json").writeText(evidence.toString(2))
        repository.close()
    }
}
