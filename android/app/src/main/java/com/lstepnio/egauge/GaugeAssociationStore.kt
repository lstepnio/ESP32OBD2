package com.lstepnio.egauge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A local desired setup. The bond and protected gauge readback remain authoritative. */
data class KnownGauge(val id: String, val name: String, val vehicleId: String? = null,
                      val source: String? = null) {
    init {
        require(id.isNotBlank() && name.trim().length in 1..32)
        require((vehicleId == null && source == null) ||
            (!vehicleId.isNullOrBlank() && source in setOf("ECM", "TCM")))
    }
}

data class GaugeAssociations(val selectedId: String?, val gauges: List<KnownGauge>) {
    init {
        require(gauges.size <= 16 && gauges.map { it.id }.distinct().size == gauges.size)
        require(selectedId == null || gauges.any { it.id == selectedId })
    }
    fun remember(id: String, name: String): GaugeAssociations = copy(selectedId = id,
        gauges = if (gauges.any { it.id == id }) gauges else gauges + KnownGauge(id, name.take(32)))
    fun assign(id: String, vehicleId: String, source: String): GaugeAssociations {
        require(gauges.any { it.id == id })
        return copy(gauges = gauges.map { if (it.id == id) it.copy(vehicleId = vehicleId, source = source) else it })
    }
    fun context(id: String, profiles: ProfileCollection): Pair<String, Draft>? {
        val gauge = gauges.firstOrNull { it.id == id } ?: return null
        val profile = profiles.profiles.firstOrNull { it.id == gauge.vehicleId } ?: return null
        return profile.draftFor(gauge.source ?: return null)?.let { profile.id to it }
    }
}

object GaugeAssociationDocumentCodec {
    fun encode(value: GaugeAssociations): String = JSONObject().put("schemaVersion", 1)
        .put("selectedId", value.selectedId).put("gauges", JSONArray().also { items ->
            value.gauges.forEach { gauge -> items.put(JSONObject().put("id", gauge.id).put("name", gauge.name)
                .put("vehicleId", gauge.vehicleId).put("source", gauge.source)) }
        }).toString()
    fun decode(raw: String): GaugeAssociations {
        val root = JSONObject(raw)
        require(root.getInt("schemaVersion") == 1) { "Gauge format is newer than this app" }
        val items = root.getJSONArray("gauges")
        require(items.length() <= 16)
        return GaugeAssociations(root.optString("selectedId").takeIf { it.isNotBlank() },
            (0 until items.length()).map { i -> items.getJSONObject(i).let {
                KnownGauge(it.getString("id"), it.getString("name"),
                    it.optString("vehicleId").takeIf(String::isNotBlank), it.optString("source").takeIf(String::isNotBlank))
            } })
    }
}

/** Multiple remembered identities, one selected phone connection. Never stores bond credentials. */
class GaugeAssociationStore(context: Context) {
    private val legacyName = context.getSharedPreferences("presentation", Context.MODE_PRIVATE)
        .getString("gauge-name", "eGauge")?.trim()?.takeIf { it.length in 1..32 } ?: "eGauge"
    private val preferences = context.getSharedPreferences("gauge-association", Context.MODE_PRIVATE)
    fun load(): GaugeAssociations {
        preferences.getString("collection", null)?.let { return GaugeAssociationDocumentCodec.decode(it) }
        val legacy = preferences.getString("selected-gauge-id", null)
        return GaugeAssociations(legacy, legacy?.let { listOf(KnownGauge(it, legacyName)) } ?: emptyList())
    }
    fun rememberedId(): String? = load().selectedId
    fun save(value: GaugeAssociations) {
        check(preferences.edit().putString("collection", GaugeAssociationDocumentCodec.encode(value)).commit()) {
            "Could not save your gauges"
        }
    }
    fun remember(id: String, name: String = "eGauge") = save(load().remember(id, name))
    fun assign(id: String, vehicleId: String, source: String) = save(load().assign(id, vehicleId, source))
}
