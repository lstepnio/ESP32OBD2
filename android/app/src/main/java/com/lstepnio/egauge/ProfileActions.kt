package com.lstepnio.egauge

import org.json.JSONArray
import org.json.JSONObject

enum class PageActionType(val wire: String) { JumpPage("jumpPage"), VehicleActions("vehicleActions") }

enum class ActionPageId(val wire: String, val label: String, val kind: VehicleCommandKind) {
    AirUp("airUp", "Air up", VehicleCommandKind.Idle),
    Winch("winch", "Winch", VehicleCommandKind.Idle),
    AbsTcs("absTcs", "Disable ABS + TCS", VehicleCommandKind.AbsTcs),
    Burnout("burnout", "Burnout Mode", VehicleCommandKind.Burnout)
}

val legacyActionPages = listOf(ActionPageId.AirUp, ActionPageId.Winch, ActionPageId.AbsTcs)

/** One recognizer, with typed destinations. Gestures open a menu; they never toggle vehicle controls. */
data class PageAction(
    val pageId: String? = null,
    val count: Int = 3,
    val windowMs: Int = 5000,
    val type: PageActionType = PageActionType.JumpPage,
    val airUpSeconds: Int = 300,
    val winchSeconds: Int = 300,
    val pages: List<ActionPageId> = legacyActionPages
) {
    init {
        require(if (type == PageActionType.JumpPage) pageId?.matches(Regex("[a-z][a-z0-9._-]{0,63}")) == true else pageId == null)
        require(count in 2..5 && windowMs in 2000..10000 && windowMs % 1000 == 0)
        require(pages.size <= 4 && pages.distinct().size == pages.size)
        require(type == PageActionType.VehicleActions || pages == legacyActionPages)
        require(airUpSeconds in 10..600 && winchSeconds in 10..600)
        require(type == PageActionType.VehicleActions || (airUpSeconds == 300 && winchSeconds == 300))
    }
    fun withoutBurnout() = if (type == PageActionType.VehicleActions) copy(pages = pages - ActionPageId.Burnout) else this

    fun json(): JSONObject = JSONObject().put("type", type.wire).put("gesture", "up").put("count", count).put("windowMs", windowMs).also {
        if (type == PageActionType.JumpPage) {
            it.put("pageId", pageId)
        } else {
            it.put("airUpSeconds", airUpSeconds).put("winchSeconds", winchSeconds)
                .put("pages", JSONArray(pages.filterNot { it == ActionPageId.Burnout }.map { page -> page.wire }))
        }
    }
}
object ProfileActions {
    fun supported(actions: List<PageAction>, version: Int?, burnoutVersion: Int? = 0): Boolean = actions.none { it.type == PageActionType.VehicleActions } ||
        (version == 1 && actions.none { it.type == PageActionType.VehicleActions && ActionPageId.Burnout in it.pages && burnoutVersion != 1 })

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
            require(
                (
                    value.keys().asSequence().toSet() == keys ||
                        (type == PageActionType.VehicleActions && value.keys().asSequence().toSet() == keys + "pages")
                    ) && value.getString("gesture") == "up"
            )
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
                if (type == PageActionType.VehicleActions) integer("winchSeconds") else 300,
                if (value.has("pages")) {
                    value.getJSONArray("pages").let { pages ->
                        require(pages.length() <= 4)
                        (0 until pages.length()).map { index -> ActionPageId.entries.firstOrNull { it.wire == pages.getString(index) } ?: error("Unsupported action page") }
                    }
                } else {
                    legacyActionPages
                }
            ).withoutBurnout()
        }
    }
    fun summary(actions: List<PageAction>) = actions.joinToString("\n") {
        "${it.count} swipes up in ${it.windowMs / 1000}s → ${if (it.type == PageActionType.VehicleActions) "Vehicle actions" else it.pageId}"
    }.ifEmpty { "None" }
}
