package com.lstepnio.egauge

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class AlertSeverity { Inactive, Information, Advisory, Warning, Critical }
enum class AlertLifecycle { Resolved, Active, Expired }
data class AlertEvent(val sequence: Long, val boot: Long, val episode: Long, val atMs: Long,
    val key: Int, val severity: AlertSeverity, val unavailable: Boolean, val lifecycle: AlertLifecycle,
    val kind: Int, val acknowledged: Boolean, val value: Float, val limit: Float,
    val label: String, val unit: String, val observedAtMs: Long, val revision: Long, val simulated: Boolean = false, val snoozed: Boolean = false) {
    fun id(gauge: String) = "$gauge:$boot:$episode:$key"
    fun transitionId(gauge: String) = "$gauge:$boot:$sequence"
    val producer: String get() = when (key) { in 0..31 -> "Threshold"; 32 -> "Engine warning"
        33 -> "Transmission warning"; 34 -> "Engine faults"; 35 -> "Transmission faults"; else -> "Phone alert" }
}
data class AlertBatch(val boot: Long, val oldest: Long, val newest: Long, val drops: Long,
    val next: Long, val gap: Boolean, val complete: Boolean, val active: Boolean, val events: List<AlertEvent>, val storageGap: Boolean = false)
object AlertCodec {
    private fun u32(b: ByteArray, at: Int) = ByteBuffer.wrap(b, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
    fun le32(value: Long): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
    fun decode(bytes: ByteArray): AlertBatch {
        require(bytes.size >= 24 && bytes[0].toInt() == 16 && bytes[1].toInt() in 0..1)
        val count = bytes[3].toInt() and 255
        require(count <= 4 && bytes.size == 24 + count * 84 && bytes[2].toInt() and 0xf8 == 0)
        val events = (0 until count).map { index ->
            val p = 24 + index * 84
            fun b(i: Int) = bytes[p + i].toInt() and 255
            fun text(offset: Int, size: Int): String {
                val raw = bytes.copyOfRange(p + offset, p + offset + size)
                val end = raw.indexOf(0)
                require(end >= 0 && raw.drop(end).all { it == 0.toByte() })
                val decoded=runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw.copyOf(end))).toString() }.getOrNull()
                require(decoded!=null);return decoded
            }
            val key = b(16) or (b(17) shl 8)
            require(key in 0..43 && b(18) in 0..4 && b(19) in 0..1 && b(20) in 0..2 &&
                b(21) in 1..5 && b(22) in 0..1 && b(23) in 0..3)
            require(u32(bytes,p) > 0 && u32(bytes,p+4) > 0 && u32(bytes,p+8) > 0)
            AlertEvent(u32(bytes,p), u32(bytes,p+4), u32(bytes,p+8), u32(bytes,p+12), key,
                AlertSeverity.entries[b(18)], b(19) == 1, AlertLifecycle.entries[b(20)], b(21), b(22) == 1,
                Float.fromBits(u32(bytes,p+24).toInt()), Float.fromBits(u32(bytes,p+28).toInt()),
                text(32,32), text(64,12), u32(bytes,p+76), u32(bytes,p+80), b(23) and 1!=0, b(23) and 2!=0)
        }
        return AlertBatch(u32(bytes,4), u32(bytes,8), u32(bytes,12),u32(bytes,16),u32(bytes,20),
            bytes[2].toInt() and 1 != 0, bytes[2].toInt() and 2 != 0, bytes[1].toInt() == 1, events,bytes[2].toInt() and 4 != 0)
    }
    fun acknowledge(event: AlertEvent, snoozeMs: Long = 0): ByteArray {
        require(snoozeMs in 0..300_000)
        return byteArrayOf(0x3d,event.key.toByte(),0,0) + le32(event.episode) + le32(event.boot) + le32(snoozeMs)
    }
    fun external(slot: Int, boot: Long, sequence: Long, severity: AlertSeverity, ttlMs: Long,
                 label: String, context: String, simulated: Boolean = false): ByteArray {
        require(slot in 0..7 && boot > 0 && sequence in 1..0x7fffffff && ttlMs in 0..600_000 &&
            severity in setOf(AlertSeverity.Information,AlertSeverity.Advisory,AlertSeverity.Warning))
        val bytes = ByteArray(64)
        bytes[0]=0x3e;bytes[1]=slot.toByte();bytes[2]=severity.ordinal.toByte();bytes[3]=if(simulated)1 else 0
        le32(boot).copyInto(bytes,4);le32(sequence).copyInto(bytes,8);le32(ttlMs).copyInto(bytes,12)
        fun copyText(value: String, at: Int, maximum: Int) {
            require(value.none { it.isISOControl() } && value.toByteArray().size <= maximum)
            value.toByteArray().copyInto(bytes,at)
        }
        copyText(label,16,31);copyText(context,48,11)
        return bytes
    }
}
/** History imports and unavailable evidence never become fresh warning notifications. */
fun shouldNotifyAlert(event: AlertEvent, live: Boolean, foreground: Boolean): Boolean = live && !foreground &&
    event.lifecycle == AlertLifecycle.Active && !event.simulated && !event.unavailable && !event.acknowledged && !event.snoozed &&
    event.severity >= AlertSeverity.Warning && event.kind in 1..2
