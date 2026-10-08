package com.lstepnio.egauge

import android.os.Build
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Mutates a disposable emulator only. Never opt this test into an owner's phone. */
class LocalSaveJourneyTest {
    @get:Rule val compose = activityTestRule()

    @Test fun asynchronousVehicleAndPageSavesPublishTheCommittedStateAndCursor() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val before = model.profileCollection
        compose.runOnUiThread { model.editProfileName("Fixture saved car"); model.createProfile() }
        compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.active.name == "Fixture saved car" }
        assertNull(model.profileError)
        val created = model.profileCollection.activeId
        val count = model.editorDraft.pages.size
        compose.runOnUiThread { model.addPage("voltage") }
        compose.waitUntil(10_000) { !model.localSaveBusy && model.editorDraft.pages.size == count + 1 }
        assertEquals(count, model.editingPageIndex)
        assertEquals(listOf("voltage"), model.editorDraft.pages.last().pidIds)
        assertEquals(model.profileCollection, ProfileStore(compose.activity).load().collection)
        val action = PageAction(model.editorDraft.pages.last().id)
        compose.runOnUiThread { model.savePageAction(action) }
        compose.waitUntil(10_000) { !model.localSaveBusy && model.editorDraft.actions == listOf(action) }
        assertEquals(model.profileCollection, ProfileStore(compose.activity).load().collection)
        compose.runOnUiThread { model.selectProfile(before.activeId) }
        compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.activeId == before.activeId }
        compose.runOnUiThread { model.deleteVehicle(created) }
        compose.waitUntil(10_000) { !model.localSaveBusy && model.profileCollection.profiles.none { it.id == created } }
        assertEquals(before, model.profileCollection)
    }
}
