package com.lstepnio.egauge
import org.junit.Assert.*
import org.junit.Test
class AlertEventsTest {
    private fun bytes()=javaClass.getResourceAsStream("/alert-events-v16.hex")!!.bufferedReader().readText().trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun productionParity() {
        val batch=AlertCodec.decode(bytes());assertEquals(7L,batch.boot);assertTrue(batch.complete)
        val e=batch.events.single();assertEquals(AlertSeverity.Critical,e.severity);assertEquals(118f,e.value);assertEquals(50L,e.revision)
        assertEquals("Coolant",e.label);assertEquals("gauge:7:1:0",e.id("gauge"))
        assertEquals(e,alertFromJson(alertJson(e)))
    }
    @Test fun notificationsOnlyForLiveTransitions() {
        val e=AlertCodec.decode(bytes()).events.single()
        assertTrue(shouldNotifyAlert(e,true,false))
        assertFalse(shouldNotifyAlert(e,false,false));assertFalse(shouldNotifyAlert(e,true,true))
        assertFalse(shouldNotifyAlert(e.copy(unavailable=true),true,false))
        assertFalse(shouldNotifyAlert(e.copy(acknowledged=true),true,false))
        assertFalse(shouldNotifyAlert(e.copy(simulated=true),true,false))
        assertFalse(shouldNotifyAlert(e.copy(kind=5),true,false))
        assertFalse(shouldNotifyAlert(e.copy(lifecycle=AlertLifecycle.Resolved),true,false))
    }
    @Test fun rejectMalformedFramesAndActions() {
        for(offset in listOf(0,1,2,3,42,43,44,45,46,47))assertThrows(IllegalArgumentException::class.java) {
            AlertCodec.decode(bytes().also { it[offset]=127 })
        }
        assertThrows(IllegalArgumentException::class.java) { AlertCodec.external(0,7,1,AlertSeverity.Critical,1000,"Hazard","Ahead") }
        assertThrows(IllegalArgumentException::class.java) { AlertCodec.acknowledge(AlertCodec.decode(bytes()).events.single(),300001) }
    }
    @Test fun storageFailureKeepsBoundedCurrentEvidenceWithoutInventingResolution() {
        val event=AlertCodec.decode(bytes()).events.single()
        val batch=AlertCodec.decode(bytes())
        val first=volatileAlertHistory(emptyList(),"car","gauge",batch,1000).single()
        assertTrue(first.current);assertTrue(first.context.getBoolean("storageGap"))
        val later=volatileAlertHistory(listOf(first),"car","gauge",batch.copy(events=listOf(event.copy(sequence=2,kind=4,unavailable=true))),2000).single()
        assertEquals(1000L,later.recordedAt);assertTrue(later.current);assertTrue(later.event.unavailable)
        assertEquals(1L,later.context.getJSONObject("entryEvent").getLong("sequence"))
        assertTrue(volatileAlertHistory(listOf(first),"other","gauge",batch.copy(events=emptyList()),2000).isEmpty())
    }
    @Test fun crowdProviderScopeExpiryAndSeverityCaps() {
        val alert=PhoneAlert("synthetic","id","car","Road hazard","Ahead",AlertSeverity.Warning,30000,1000,true)
        val normalized=PhoneAlertBridge.fixtures.validate(alert,"car",2000)
        assertEquals(AlertSeverity.Advisory,normalized.severity);assertEquals(29000L,normalized.ttlMs)
        assertThrows(IllegalArgumentException::class.java) { PhoneAlertBridge.fixtures.validate(alert,"other",2000) }
        assertThrows(IllegalArgumentException::class.java) { PhoneAlertBridge.fixtures.validate(alert,"car",31000) }
        assertThrows(IllegalStateException::class.java) { PhoneAlertBridge.fixtures.validate(alert.copy(provider="unknown"),"car",2000) }
    }
    @org.junit.Test fun resumedOwnerAcceptsNewProtectedFramesAndRejectsMalformedLengths() {
        fun frame(version: Int, size: Int, count: Int = 0) = ByteArray(size).apply { this[0]=version.toByte();if(size>3)this[3]=count.toByte() }
        // The next session first reads the previous command's protected response.
        listOf(frame(16,24),frame(16,360,4),frame(17,24),frame(17,216),frame(18,32),frame(19,24)).forEach {
            org.junit.Assert.assertTrue(GaugeProtocolCodec.isProtectedStatusFrame(it))
        }
        listOf(frame(16,108,0),frame(16,361,4),frame(17,25),frame(18,31),frame(19,25),frame(20,24)).forEach {
            org.junit.Assert.assertFalse(GaugeProtocolCodec.isProtectedStatusFrame(it))
        }
    }
}
