package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject

enum class PageActionType(val wire: String) { JumpPage("jumpPage"), VehicleActions("vehicleActions") }

/** One recognizer, with typed destinations. Gestures open a menu; they never toggle vehicle controls. */
data class PageAction(
    val pageId: String? = null,
    val count: Int = 3,
    val windowMs: Int = 5000,
    val type: PageActionType = PageActionType.JumpPage,
    val airUpSeconds: Int = 300,
    val winchSeconds: Int = 300
) {
    init {
        require(if (type == PageActionType.JumpPage) pageId?.matches(Regex("[a-z][a-z0-9._-]{0,63}")) == true else pageId == null)
        require(count in 2..5 && windowMs in 2000..10000 && windowMs % 1000 == 0)
        require(airUpSeconds in 10..600 && winchSeconds in 10..600)
        require(type == PageActionType.VehicleActions || (airUpSeconds == 300 && winchSeconds == 300))
    }
    fun json(): JSONObject = JSONObject().put("type", type.wire).put("gesture", "up").put("count", count).put("windowMs", windowMs).also {
        if (type == PageActionType.JumpPage) {
            it.put("pageId", pageId)
        } else {
            it.put("airUpSeconds", airUpSeconds).put("winchSeconds", winchSeconds)
        }
    }
}
object ProfileActions {
    fun validate(actions: List<PageAction>, pages: List<GaugePageDraft>) {
        require(actions.size <= 1) { "Assign only one action to the upward swipe" }
        require(actions.all { it.type == PageActionType.VehicleActions || pages.any { page -> page.id == it.pageId } }) { "Choose an existing page for the action" }
    }
    fun json(actions: List<PageAction>) = JSONArray().also { array -> actions.forEach { array.put(it.json()) } }
    fun decode(array: JSONArray?): List<PageAction> {
        if (array == null) return emptyList()
        require(array.length() <= 1)
        return (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            val type = PageActionType.entries.firstOrNull { it.wire == value.getString("type") } ?: throw IllegalArgumentException("Unknown gesture action")
            val keys = setOf("type", "gesture", "count", "windowMs") + if (type == PageActionType.JumpPage) setOf("pageId") else setOf("airUpSeconds", "winchSeconds")
            require(value.keys().asSequence().toSet() == keys && value.getString("gesture") == "up")
            fun integer(key: String): Int {
                val raw = value.get(key)
                require(raw is Number && raw.toDouble() == raw.toInt().toDouble())
                return raw.toInt()
            }
            PageAction(
                if (type == PageActionType.JumpPage) value.getString("pageId") else null,
                integer("count"),
                integer("windowMs"),
                type,
                if (type == PageActionType.VehicleActions) integer("airUpSeconds") else 300,
                if (type == PageActionType.VehicleActions) integer("winchSeconds") else 300
            )
        }
    }
    fun summary(actions: List<PageAction>) = actions.joinToString("\n") {
        "${it.count} swipes up in ${it.windowMs / 1000}s → ${if (it.type == PageActionType.VehicleActions) "Vehicle actions" else it.pageId}"
    }.ifEmpty { "None" }
}
