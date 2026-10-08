package com.lstepnio.egauge

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Uses real SQLite in a disposable emulator; no Bluetooth or vehicle writes. */
class AlertRepositoryTest {
    private lateinit var repository: AlertRepository
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val now=System.currentTimeMillis()
    private fun event(sequence: Long=1,revision: Long=10)=AlertEvent(sequence,7,1,1000,0,AlertSeverity.Warning,false,AlertLifecycle.Active,1,false,108f,105f,"Coolant","degC",1000,revision)
    private fun batch(e: AlertEvent, gap: Boolean=false)=AlertBatch(7,1,e.sequence,0,e.sequence,gap,true,false,listOf(e))
    private fun config(revision: Long=10)="""{"baseRevision":${revision-1},"definitions":[{"name":"Coolant","unit":"degC"}]}"""
    @Before fun setup() {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish")) { "These destructive fixture tests require an emulator" }
        context.deleteDatabase("alert-history.db");repository=AlertRepository(context)
    }
    @After fun cleanup() { if(::repository.isInitialized)repository.close() }
    @Test fun duplicateImportCursorAndOriginalEvidenceSurviveRestart() {
        val first=repository.ingest("car","gauge",batch(event()),config(),null,now).single()
        repository.ingest("car","gauge",batch(event()),config(),null,now+1)
        assertEquals(1,repository.transitions(first.id).length())
        repository.ingest("car","gauge",batch(event(2).copy(kind=5,acknowledged=true)),config(),null,now+2)
        repository.close();repository=AlertRepository(context)
        val restored=repository.list("car","gauge").single()
        assertEquals(now,restored.recordedAt);assertTrue(restored.event.acknowledged)
        assertEquals(2L,repository.cursor("car:gauge").sequence)
        assertEquals(now,restored.context.getLong("capturedAt"))
    }
    @Test fun retiredRevisionAndGapCannotBecomeHealthyCurrentVehicle() {
        repository.ingest("car-a","gauge",batch(event()),config(),null,now)
        repository.ingest("car-b","gauge",batch(event(2,11).copy(episode=2)),config(11),null,now+1)
        repository.ingest("car-b","gauge",batch(event(3,10).copy(kind=4),true),config(11),null,now+2)
        assertEquals(10L,repository.list("car-a").single().event.revision)
        assertFalse(repository.list("car-b").single().current)
        val unknown=repository.ingest("car-b","gauge",batch(event(4,99).copy(episode=3)),config(11),null,now+3)
        assertTrue(unknown.none { it.event.revision==99L })
        assertEquals(1,repository.list("unassigned:gauge:99").size)
    }
    @Test fun pinActiveHistoryDeletionAndBoundedCaptureRemainSeparateFromCodes() {
        val a=repository.ingest("car","gauge",batch(event()),config(),null,now).single()
        repository.saveCapture(a.id,JSONObject().put("available",true).put("complete",true))
        repository.pin(a.id,true)
        repository.ingest("car","gauge",batch(event(2).copy(lifecycle=AlertLifecycle.Resolved,kind=3)),config(),null,now+1)
        repository.deleteHistory("car");assertEquals(1,repository.list("car").size)
        assertTrue(repository.list("car").single().context.getJSONObject("capture").getBoolean("complete"))
        repository.pin(a.id,false);repository.deleteHistory("car");assertTrue(repository.list("car").isEmpty())
    }
    @Test fun outOfOrderRecordsAndReportsAreScoped() {
        repository.ingest("car","gauge",batch(event(3).copy(lifecycle=AlertLifecycle.Resolved,kind=3)),config(),null,now)
        repository.ingest("car","gauge",batch(event(1)),config(),null,now+1)
        assertFalse(repository.list("car").single().current);assertEquals(3L,repository.cursor("car:gauge").sequence)
        repository.saveReport("a",JSONObject().put("vehicle","car").put("stage","before clear"),now)
        repository.saveReport("b",JSONObject().put("vehicle","other"),now)
        assertEquals(1,repository.reports("car").length())
    }
}
