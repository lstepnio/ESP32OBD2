package com.lstepnio.egauge

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.util.UUID

/** Private preference namespace; never reads/writes the owner's actual profiles or bonds. */
@RunWith(AndroidJUnit4::class)
class LocalSetupStoreTest {
    private val fixturePreferences = mutableListOf<SharedPreferences>()
    @After fun clearFixtures() { fixturePreferences.forEach { check(it.edit().clear().commit()) } }
    private fun isolated(failNextCommit: () -> Boolean = { false }): Context {
        val prefix = "fixture-${UUID.randomUUID()}-"
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val real = base.getSharedPreferences(prefix + name, mode)
                fixturePreferences += real
                return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
                    arrayOf(SharedPreferences::class.java)) { _, method, args ->
                    if (method.name != "edit") method.invoke(real, *(args ?: emptyArray()))
                    else {
                        val editor = real.edit()
                        lateinit var proxy: SharedPreferences.Editor
                        proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                            arrayOf(SharedPreferences.Editor::class.java)) { _, editMethod, editArgs ->
                            val result = editMethod.invoke(editor, *(editArgs ?: emptyArray()))
                            when {
                                editMethod.name == "commit" && failNextCommit() -> false
                                result === editor -> proxy
                                else -> result
                            }
                        } as SharedPreferences.Editor
                        proxy
                    }
                } as SharedPreferences
            }
        }
    }

    @Test fun legacyMigrationIsReadOnlyAndFirstCommitIncludesBothDocuments() {
        val context = isolated()
        val legacy = context.getSharedPreferences("draft-v1", Context.MODE_PRIVATE)
        check(legacy.edit().putString("pid", "speed").commit())
        val store = LocalSetupStore(context)
        val loaded = store.load()
        assertEquals("speed", loaded.profiles.active.draft.pidId)
        val prefs = context.getSharedPreferences("profiles-v1", Context.MODE_PRIVATE)
        assertFalse(prefs.contains("collection"))
        assertTrue(store.save(loaded.copy(associations = loaded.associations.remember("fixture", "Fixture gauge"))))
        assertTrue(prefs.contains("collection"))
        assertTrue(prefs.contains(LocalSetupStore.ASSOCIATIONS))
        assertEquals(12, org.json.JSONObject(prefs.getString("collection", null)!!).getInt("schemaVersion"))
        assertEquals("speed", legacy.getString("pid", null))
        assertEquals("fixture", LocalSetupStore(context).load().associations.selectedId)
    }

    @Test fun failedCommitRestoresThePriorPreferenceMapAndKeepsDocumentsTogether() {
        var failOnce = false
        val context = isolated { failOnce.also { failOnce = false } }
        val store = LocalSetupStore(context)
        val before = store.load()
        assertTrue(store.save(before))
        val next = before.create(VehicleProfile("fixture.new", "New car", Draft()))
        failOnce = true
        assertFalse(store.save(next))
        assertEquals(before, store.load())
        assertEquals(before, LocalSetupStore(context).load())
    }

    @Test fun unreadableUpdateJournalRequiresHealthyReadbackBeforeClearing() {
        val context = isolated()
        check(context.getSharedPreferences("update-recovery", Context.MODE_PRIVATE).edit()
            .putString("pending-update", "{broken").commit())
        val journal = UpdateRecoveryJournal(context)
        val pending = requireNotNull(journal.read())
        assertEquals("unreadable", pending.stage)
        assertFalse(reconcilePendingUpdate(pending, "fixture", "ab".repeat(32), 1).terminal)
        assertTrue(reconcilePendingUpdate(pending, "fixture", "ab".repeat(32), 2).terminal)
        assertNotNull(journal.read()) // Reconciliation itself never clears persistence.
        journal.clear()
        assertNull(journal.read())
    }

    @Test fun corruptedProfilesCannotBeReplacedByAnAssociationSave() {
        val context = isolated()
        val prefs = context.getSharedPreferences("profiles-v1", Context.MODE_PRIVATE)
        check(prefs.edit().putString("collection", "{broken").commit())
        assertThrows(IllegalStateException::class.java) { GaugeAssociationStore(context).remember("fixture") }
        assertEquals("{broken", prefs.getString("collection", null))
        assertFalse(prefs.contains(LocalSetupStore.ASSOCIATIONS))
    }
}
