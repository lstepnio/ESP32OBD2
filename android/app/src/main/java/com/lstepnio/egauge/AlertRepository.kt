package com.lstepnio.egauge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Event history has its own transactional store; vehicle setup stays in LocalSetupStore. */
class AlertRepository(context: Context) : SQLiteOpenHelper(context, "alert-history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id TEXT PRIMARY KEY, episode TEXT NOT NULL, vehicle TEXT NOT NULL, gauge TEXT NOT NULL, recorded INTEGER NOT NULL, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE episodes (id TEXT PRIMARY KEY, vehicle TEXT NOT NULL, gauge TEXT NOT NULL, recorded INTEGER NOT NULL, active INTEGER NOT NULL, json TEXT NOT NULL, context TEXT NOT NULL, pinned INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE configurations (gauge TEXT NOT NULL, revision INTEGER NOT NULL, vehicle TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY(gauge,revision))")
        db.execSQL("CREATE TABLE cursors (scope TEXT PRIMARY KEY, boot INTEGER NOT NULL, sequence INTEGER NOT NULL, drops INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE reports (id TEXT PRIMARY KEY, recorded INTEGER NOT NULL, json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX episode_vehicle ON episodes(vehicle,recorded)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Unsupported alert history migration") }
    data class Cursor(val boot: Long = 0, val sequence: Long = 0, val drops: Long = 0)
    fun cursor(scope: String): Cursor = readableDatabase.rawQuery("SELECT boot,sequence,drops FROM cursors WHERE scope=?",arrayOf(scope)).use {
        if(it.moveToFirst()) Cursor(it.getLong(0),it.getLong(1),it.getLong(2)) else Cursor()
    }
    fun ingest(vehicle: String, gauge: String, batch: AlertBatch, configuration: String?, diagnostics: String?, now: Long): List<StoredAlert> {
        val db=writableDatabase; db.beginTransaction()
        try {
            if(batch.gap) {
                // Retained events become last checked; absence across a gap does not prove resolution.
                db.execSQL("UPDATE episodes SET active=0 WHERE gauge=?",arrayOf(gauge))
            }
            configuration?.let { json ->
                val config=JSONObject(json)
                db.execSQL("INSERT OR IGNORE INTO configurations VALUES(?,?,?,?)",arrayOf<Any?>(gauge,config.getLong("baseRevision")+1,vehicle,json))
            }
            for(e in batch.events) {
                val matched=db.rawQuery("SELECT vehicle,json FROM configurations WHERE gauge=? AND revision=?",arrayOf(gauge,e.revision.toString())).use {
                    if(it.moveToFirst()) Pair(it.getString(0),it.getString(1)) else Pair("unassigned:$gauge:${e.revision}",null)
                }
                val eventVehicle=matched.first
                val episode=e.id(gauge); val json=alertJson(e).toString()
                val context=JSONObject().put("entryEvent",alertJson(e)).put("configurationRevision", e.revision).put("definitions", matched.second?.let { JSONObject(it).optJSONArray("definitions") })
                    .put("diagnostics",diagnostics?.takeIf { eventVehicle==vehicle && e.revision==configuration?.let { JSONObject(it).getLong("baseRevision")+1 } }?.let(::JSONObject)).put("coverageGap",batch.gap).put("storageGap",batch.storageGap)
                    .put("producerDrops",batch.drops).put("capturedAt",now).put("window","pending").toString()
                db.execSQL("INSERT OR IGNORE INTO events VALUES(?,?,?,?,?,?)",arrayOf(e.transitionId(gauge),episode,eventVehicle,gauge,now,json))
                // Never overwrite the original entry evidence on acknowledgment or later refresh.
                db.execSQL("INSERT OR IGNORE INTO episodes(id,vehicle,gauge,recorded,active,json,context) VALUES(?,?,?,?,?,?,?)",
                    arrayOf(episode,eventVehicle,gauge,now,if(e.lifecycle==AlertLifecycle.Active && e.boot==batch.boot)1 else 0,json,context))
                val previous=db.rawQuery("SELECT json FROM episodes WHERE id=?",arrayOf(episode)).use { it.moveToFirst();JSONObject(it.getString(0)).getLong("sequence") }
                if(e.sequence>=previous)db.execSQL("UPDATE episodes SET active=?,json=? WHERE id=?",arrayOf<Any?>(if(e.lifecycle==AlertLifecycle.Active && e.boot==batch.boot)1 else 0,json,episode))
            }
            if(!batch.active) {
                val previous=db.rawQuery("SELECT boot,sequence FROM cursors WHERE scope=?",arrayOf("$vehicle:$gauge")).use { if(it.moveToFirst())Pair(it.getLong(0),it.getLong(1))else null }
                if(previous==null || previous.first!=batch.boot || batch.next>=previous.second)
                    db.execSQL("INSERT OR REPLACE INTO cursors VALUES(?,?,?,?)",arrayOf<Any?>("$vehicle:$gauge",batch.boot,batch.next,batch.drops))
            }
            evict(db,now)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return list(vehicle,gauge)
    }
    fun saveCapture(id: String, capture: JSONObject) {
        require(capture.toString().toByteArray().size <= 65536)
        val db=writableDatabase;db.beginTransaction();try {
            val context=db.rawQuery("SELECT context FROM episodes WHERE id=?",arrayOf(id)).use {
                if(it.moveToFirst()) JSONObject(it.getString(0)) else null
            }
            context?.let { db.execSQL("UPDATE episodes SET context=? WHERE id=?",arrayOf(it.put("capture",capture).put("window","captured").toString(),id)) }
            evict(db,System.currentTimeMillis());db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun list(vehicle: String, gauge: String? = null): List<StoredAlert> = readableDatabase.rawQuery(
        "SELECT id,gauge,recorded,active,json,context,pinned FROM episodes WHERE vehicle=? ${if(gauge!=null)"AND gauge=?" else ""} ORDER BY recorded DESC LIMIT 500",
        if(gauge!=null)arrayOf(vehicle,gauge)else arrayOf(vehicle)).use { cursor -> buildList {
            while(cursor.moveToNext()) add(StoredAlert(cursor.getString(0),vehicle,cursor.getString(1),cursor.getLong(2),
                cursor.getInt(3)==1,alertFromJson(JSONObject(cursor.getString(4))),JSONObject(cursor.getString(5)),cursor.getInt(6)==1))
        } }
    fun transitions(id: String): JSONArray = readableDatabase.rawQuery("SELECT json FROM events WHERE episode=? ORDER BY recorded,rowid",arrayOf(id)).use {
        JSONArray().apply { while(it.moveToNext())put(JSONObject(it.getString(0))) }
    }
    fun deleteHistory(vehicle: String) {
        val db=writableDatabase;db.beginTransaction();try {
            db.execSQL("DELETE FROM events WHERE vehicle=? AND episode IN (SELECT id FROM episodes WHERE active=0 AND pinned=0)",arrayOf(vehicle))
            db.execSQL("DELETE FROM episodes WHERE vehicle=? AND active=0 AND pinned=0",arrayOf(vehicle));db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun pin(id: String, value: Boolean) { if(value)readableDatabase.rawQuery("SELECT count(*) FROM episodes WHERE pinned=1",null).use { it.moveToFirst();require(it.getInt(0)<100) { "At most 100 reports may be pinned" } };writableDatabase.execSQL("UPDATE episodes SET pinned=? WHERE id=?",arrayOf<Any?>(if(value)1 else 0,id)) }
    fun saveReport(id: String, report: JSONObject, now: Long) {
        require(report.toString().toByteArray().size<=65536)
        val db=writableDatabase;db.beginTransaction();try {
            db.execSQL("INSERT OR REPLACE INTO reports VALUES(?,?,?)",arrayOf<Any?>(id,now,report.toString()));evict(db,now);db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun reports(vehicle: String?): JSONArray = readableDatabase.rawQuery("SELECT json FROM reports ORDER BY recorded",null).use {
        JSONArray().apply { while(it.moveToNext()) { val report=JSONObject(it.getString(0));if(report.optString("vehicle")==vehicle)put(report) } }
    }
    private fun evict(db: SQLiteDatabase,now: Long) {
        db.execSQL("DELETE FROM episodes WHERE active=0 AND pinned=0 AND (recorded<? OR id IN (SELECT id FROM episodes WHERE active=0 AND pinned=0 ORDER BY recorded DESC LIMIT -1 OFFSET 500))",arrayOf(now-30L*86400000))
        db.execSQL("DELETE FROM events WHERE episode NOT IN (SELECT id FROM episodes)")
        db.execSQL("DELETE FROM reports WHERE recorded<? OR id IN (SELECT id FROM reports ORDER BY recorded DESC LIMIT -1 OFFSET 100)",arrayOf(now-30L*86400000))
        db.execSQL("DELETE FROM events WHERE rowid IN (SELECT rowid FROM events ORDER BY recorded DESC LIMIT -1 OFFSET 10000)")
        db.execSQL("DELETE FROM configurations WHERE rowid IN (SELECT rowid FROM configurations ORDER BY rowid DESC LIMIT -1 OFFSET 100)")
        fun usage(): Long = db.rawQuery("SELECT (SELECT coalesce(sum(length(json)+length(context)),0) FROM episodes)+(SELECT coalesce(sum(length(json)),0) FROM events)+(SELECT coalesce(sum(length(json)),0) FROM reports)+(SELECT coalesce(sum(length(json)),0) FROM configurations)",null).use { it.moveToFirst();it.getLong(0) }
        while(usage()>50L*1024*1024) {
            val oldest=db.rawQuery("SELECT id FROM episodes WHERE active=0 AND pinned=0 ORDER BY recorded LIMIT 1",null).use { if(it.moveToFirst())it.getString(0)else null }
            if(oldest!=null) { db.execSQL("DELETE FROM events WHERE episode=?",arrayOf(oldest));db.execSQL("DELETE FROM episodes WHERE id=?",arrayOf(oldest)) }
            else { db.execSQL("UPDATE episodes SET context='{" + "\"storageGap\":true}'");break }
        }
        // Compact metadata survives; sensor windows are bounded separately.
        db.execSQL("DELETE FROM events WHERE rowid IN (SELECT rowid FROM events ORDER BY recorded DESC LIMIT -1 OFFSET 10000)")
    }
}
data class StoredAlert(val id: String,val vehicle: String,val gauge: String,val recordedAt: Long,
    val current: Boolean,val event: AlertEvent,val context: JSONObject,val pinned: Boolean=false)
fun alertJson(e: AlertEvent): JSONObject = JSONObject().put("sequence",e.sequence).put("boot",e.boot)
    .put("episode",e.episode).put("atMs",e.atMs).put("key",e.key).put("severity",e.severity.ordinal)
    .put("unavailable",e.unavailable).put("lifecycle",e.lifecycle.ordinal).put("kind",e.kind)
    .put("acknowledged",e.acknowledged).put("value",e.value.takeIf { it.isFinite() })
    .put("limit",e.limit.takeIf { it.isFinite() }).put("label",e.label).put("unit",e.unit)
    .put("observedAtMs",e.observedAtMs).put("revision",e.revision).put("simulated",e.simulated).put("snoozed",e.snoozed)
fun alertFromJson(j: JSONObject) = AlertEvent(j.getLong("sequence"),j.getLong("boot"),j.getLong("episode"),j.getLong("atMs"),j.getInt("key"),
    AlertSeverity.entries[j.getInt("severity")],j.getBoolean("unavailable"),AlertLifecycle.entries[j.getInt("lifecycle")],j.getInt("kind"),j.getBoolean("acknowledged"),
    j.optDouble("value",Double.NaN).toFloat(),j.optDouble("limit",Double.NaN).toFloat(),j.getString("label"),j.getString("unit"),j.getLong("observedAtMs"),j.getLong("revision"),j.optBoolean("simulated"),j.optBoolean("snoozed"))

/** Failed persistence never advances the durable cursor or hides freshly observed alerts. */
fun volatileAlertHistory(previous: List<StoredAlert>,vehicle: String,gauge: String,batch: AlertBatch,now: Long): List<StoredAlert> {
    val records=previous.filter { it.vehicle==vehicle && it.gauge==gauge }.associateBy { it.id }.toMutableMap()
    if(batch.gap)records.replaceAll { _,entry -> entry.copy(current=false) }
    for(event in batch.events) {
        val id=event.id(gauge);val old=records[id]
        if(old!=null && old.event.sequence>event.sequence)continue
        val context=old?.context?:JSONObject().put("entryEvent",alertJson(event)).put("storageGap",true).put("window","unavailable")
        records[id]=StoredAlert(id,vehicle,gauge,old?.recordedAt?:now,event.boot==batch.boot && event.lifecycle==AlertLifecycle.Active,event,context,old?.pinned?:false)
    }
    return records.values.sortedByDescending { it.recordedAt }.take(64)
}
