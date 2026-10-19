package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject

/** Only local actions are executable. Controller candidates have no wire commands. */
data class PageAction(val pageId: String, val count: Int = 3, val windowMs: Int = 5000) {
    init {
        require(pageId.matches(Regex("[a-z][a-z0-9._-]{0,63}")))
        require(count in 2..5 && windowMs in 2000..10000 && windowMs % 1000 == 0)
    }
    fun json(): JSONObject = JSONObject().put("type", "jumpPage").put("pageId", pageId)
        .put("gesture", "up").put("count", count).put("windowMs", windowMs)
}

object ProfileActions {
    fun validate(actions: List<PageAction>, pages: List<GaugePageDraft>) {
        require(actions.size <= 1) { "Assign only one action to the upward swipe" }
        require(actions.all { action -> pages.any { it.id == action.pageId } }) {
            "Choose an existing page for the action"
        }
    }
    fun json(actions: List<PageAction>) = JSONArray().also { array -> actions.forEach { array.put(it.json()) } }
    fun decode(array: JSONArray?): List<PageAction> {
        if (array == null) return emptyList()
        require(array.length() <= 1)
        return (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            require(value.keys().asSequence().toSet() == setOf("type", "pageId", "gesture", "count", "windowMs"))
            require(value.getString("type") == "jumpPage" && value.getString("gesture") == "up")
            fun integer(key: String): Int {
                val raw = value.get(key)
                require(raw is Number && raw.toDouble() == raw.toInt().toDouble())
                return raw.toInt()
            }
            PageAction(value.getString("pageId"), integer("count"), integer("windowMs"))
        }
    }
    fun summary(actions: List<PageAction>) = actions.joinToString("\n") {
        "${it.count} swipes up in ${it.windowMs / 1000}s → ${it.pageId}"
    }.ifEmpty { "None" }
}
