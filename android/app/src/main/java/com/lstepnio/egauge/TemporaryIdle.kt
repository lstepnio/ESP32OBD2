package com.lstepnio.egauge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

enum class IdlePresetId(val wire: Int, val label: String) { AirUp(1, "Air up"), Winch(2, "Winch") }

/** Local draft limits bound storage, not controller support. No unverified RPM default. */
data class IdlePreset(val id: IdlePresetId, val rpm: Int? = null, val seconds: Int = 300) {
    init {
        require(rpm == null || rpm in 400..3000)
        require(seconds in 10..600)
    }
}

object IdlePresets {
    fun validate(values: List<IdlePreset>) {
        require(values.size <= 2 && values.map { it.id }.distinct().size == values.size)
    }
    fun json(values: List<IdlePreset>): JSONArray {
        validate(values)
        return JSONArray().also { array ->
            values.forEach { value ->
                array.put(JSONObject().put("id", value.id.name).put("rpm", value.rpm ?: JSONObject.NULL).put("seconds", value.seconds))
            }
        }
    }
    fun decode(values: JSONArray): List<IdlePreset> {
        require(values.length() <= 2)
        fun integer(item: JSONObject, key: String): Int {
            val n = item.get(key)
            require(n is Number && n.toDouble() == n.toInt().toDouble())
            return n.toInt()
        }
        return (0 until values.length()).map { i ->
            val item = values.getJSONObject(i)
            require(item.keys().asSequence().toSet() == setOf("id", "rpm", "seconds"))
            IdlePreset(IdlePresetId.valueOf(item.getString("id")), if (item.isNull("rpm")) null else integer(item, "rpm"), integer(item, "seconds"))
        }.also(::validate)
    }
}

enum class IdlePhase { Unavailable, Ready, Starting, Active, Stopping, Restored, Rejected, Unknown }
data class IdleControlState(val phase: IdlePhase = IdlePhase.Unavailable, val preset: IdlePresetId? = null, val rpm: Int? = null, val remainingSeconds: Int = 0) {
    val configurationLocked get() = phase in setOf(IdlePhase.Starting, IdlePhase.Active, IdlePhase.Stopping, IdlePhase.Unknown)
    val canStart get() = phase in setOf(IdlePhase.Ready, IdlePhase.Restored, IdlePhase.Rejected)
    val canStop get() = phase in setOf(IdlePhase.Starting, IdlePhase.Active)
    val message get() = when (phase) {
        IdlePhase.Unavailable -> "Presets stay on this phone until idle control is ready for this car."
        IdlePhase.Ready -> "Ready to start"
        IdlePhase.Restored -> "Normal idle restored"
        IdlePhase.Starting -> "Starting ${preset?.label ?: "idle control"}"
        IdlePhase.Active -> "${preset?.label ?: "Raised idle"} · $rpm rpm · ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')} remaining"
        IdlePhase.Stopping -> "Returning to normal idle"
        IdlePhase.Rejected -> "Idle control could not start"
        IdlePhase.Unknown -> "Idle status is unknown. Check the vehicle."
    }
}

/** Offline inner contract only. There is no registered BLE opcode or production driver. */
object IdleCommandProtocol {
    fun request(command: Int, operation: Long, revision: Long, link: Long, preset: IdlePreset? = null): ByteArray {
        require(command in 1..3 && listOf(operation, revision, link).all { it in 1..0xffffffffL })
        require(if (command == 1) preset?.rpm != null else preset == null)
        return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(1)
            put(command.toByte())
            put((preset?.id?.wire ?: 0).toByte())
            put(0)
            putInt(operation.toInt())
            putInt(revision.toInt())
            putInt(link.toInt())
            putShort((preset?.rpm ?: 0).toShort())
            putShort((preset?.seconds ?: 0).toShort())
            putInt(0)
        }.array()
    }
}
