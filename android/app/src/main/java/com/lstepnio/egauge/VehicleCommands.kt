package com.lstepnio.egauge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val VEHICLE_COMMAND_GUARD = 5
private const val VEHICLE_COMMAND_COOLANT_TIMER = 9

enum class VehicleCommandKind(val wire: Int, val title: String) {
    Idle(1, "Raised idle"),
    AbsTcs(2, "ABS & traction control"),
    Burnout(3, "Burnout Mode");

    companion object {
        fun supported(version: Int?, burnoutVersion: Int? = 0) = entries.filter { version == 1 && (it != Burnout || burnoutVersion == 1) }
    }
}
enum class VehicleCommandPhase {
    Unavailable,
    Ready,
    Starting,
    Active,
    Stopping,
    Verifying,
    Restored,
    RestartRequired,
    Rejected,
    Unknown
}
data class VehicleCommandStatus(
    val kind: VehicleCommandKind,
    val phase: VehicleCommandPhase = VehicleCommandPhase.Unavailable,
    val reason: Int = 0,
    val operation: Long = 0,
    val revision: Long = 0,
    val link: Long = 0,
    val boot: Long = 0,
    val sequence: Long = 0,
    val remainingSeconds: Int = 0,
    val preset: IdlePresetId? = null,
    val qualified: Boolean = false,
    val restartRequired: Boolean = false,
    val lastOperation: Long = 0,
    val timedFallback: Boolean = false,
    val gaugeStartRequired: Boolean = false,
    val coolantUnavailable: Boolean = false,
    val ownerSupervised: Boolean = false
) {
    val busy get() = phase in setOf(VehicleCommandPhase.Starting, VehicleCommandPhase.Active, VehicleCommandPhase.Stopping, VehicleCommandPhase.Verifying, VehicleCommandPhase.Unknown, VehicleCommandPhase.RestartRequired)
    val canStart get() = kind != VehicleCommandKind.Burnout && qualified && !gaugeStartRequired && phase in setOf(VehicleCommandPhase.Ready, VehicleCommandPhase.Restored, VehicleCommandPhase.Rejected)
    val canStop get() = qualified && if (kind == VehicleCommandKind.Burnout) {
        operation > 0 && phase in setOf(VehicleCommandPhase.Starting, VehicleCommandPhase.Active, VehicleCommandPhase.Unknown)
    } else {
        phase in setOf(VehicleCommandPhase.Starting, VehicleCommandPhase.Active)
    }

    /** Local capability context only. ABS flag 8 remains forbidden on the legacy wire. */
    fun withBrakeActionsTestVersion(version: Int): VehicleCommandStatus {
        require(version in 0..1)
        return if (version == 1 && kind != VehicleCommandKind.Idle) {
            copy(gaugeStartRequired = true, ownerSupervised = true)
        } else {
            this
        }
    }
    val message get() = when {
        ownerSupervised && kind == VehicleCommandKind.Burnout -> when (phase) {
            VehicleCommandPhase.Ready -> "Hold on gauge · release to stop"
            VehicleCommandPhase.Starting -> "Starting Burnout Mode"
            VehicleCommandPhase.Active -> "Hold on gauge · $remainingSeconds seconds remaining"
            VehicleCommandPhase.Stopping -> "Returning brake control"
            VehicleCommandPhase.Verifying -> "Check normal braking, then confirm on the gauge"
            VehicleCommandPhase.Restored -> "Normal braking confirmed on the gauge"
            VehicleCommandPhase.Unknown -> "Check braking and confirm on the gauge"
            VehicleCommandPhase.Rejected -> "Could not start · check the gauge"
            else -> "Check the gauge before starting"
        }

        kind == VehicleCommandKind.AbsTcs -> when (phase) {
            VehicleCommandPhase.Ready -> if (gaugeStartRequired) "Hold to disable on the gauge" else "Ready"
            VehicleCommandPhase.Starting -> "Disabling…"
            VehicleCommandPhase.Active -> "Disabled · Restore on the gauge"
            VehicleCommandPhase.Stopping -> "Restoring…"
            VehicleCommandPhase.RestartRequired -> "Restart Jeep · Confirm reset on the gauge"
            VehicleCommandPhase.Verifying -> "Restoration not verified · Check the gauge"
            VehicleCommandPhase.Unknown -> "Not verified · Restore on the gauge"
            VehicleCommandPhase.Restored -> if (ownerSupervised) "Reset confirmed on the gauge" else "Restoration confirmed"
            VehicleCommandPhase.Rejected -> "Could not start · Check the gauge"
            VehicleCommandPhase.Unavailable -> if (ownerSupervised) "Connecting…" else "Not available for this car yet"
        }

        kind == VehicleCommandKind.Burnout -> when (phase) {
            VehicleCommandPhase.Unknown -> "Outcome unknown · brake release not verified"
            VehicleCommandPhase.Active -> "Hold on gauge · $remainingSeconds seconds remaining"
            VehicleCommandPhase.Starting -> "Starting Burnout Mode"
            VehicleCommandPhase.Stopping, VehicleCommandPhase.Verifying -> "Checking control release"
            VehicleCommandPhase.Restored -> "Control released"
            else -> if (qualified) "Hold on gauge · release to stop" else "Hold on gauge · release not qualified"
        }

        kind == VehicleCommandKind.Idle && phase in setOf(VehicleCommandPhase.Ready, VehicleCommandPhase.Restored, VehicleCommandPhase.Rejected) && gaugeStartRequired -> if (coolantUnavailable) "Start on the gauge. Without coolant readings, sessions are limited to one minute." else "Start again on the gauge."

        kind == VehicleCommandKind.Idle && timedFallback && phase == VehicleCommandPhase.Active -> "${if (coolantUnavailable) "Coolant unavailable · " else ""}Timed idle · ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')} remaining"

        kind == VehicleCommandKind.Idle && reason == VEHICLE_COMMAND_COOLANT_TIMER && phase == VehicleCommandPhase.Stopping -> "Timed session ended. Returning to normal idle."

        kind == VehicleCommandKind.Idle && reason == VEHICLE_COMMAND_COOLANT_TIMER && phase == VehicleCommandPhase.Verifying -> "Checking normal idle after the timed session."

        kind == VehicleCommandKind.Idle && reason == VEHICLE_COMMAND_GUARD && phase == VehicleCommandPhase.Rejected -> "Idle cannot start. Check coolant and vehicle conditions."

        kind == VehicleCommandKind.Idle && reason == VEHICLE_COMMAND_GUARD && phase == VehicleCommandPhase.Stopping -> "Stopping raised idle. Check coolant and vehicle conditions."

        kind == VehicleCommandKind.Idle && reason == VEHICLE_COMMAND_GUARD && phase == VehicleCommandPhase.Verifying -> "Checking normal idle after a condition changed."

        else -> phaseMessage
    }
    private val phaseMessage get() = when (phase) {
        VehicleCommandPhase.Unavailable -> "Not available for this car yet"
        VehicleCommandPhase.Ready -> "Ready"
        VehicleCommandPhase.Starting -> if (kind == VehicleCommandKind.Idle) "Raising idle" else "Switching ABS and traction control off"
        VehicleCommandPhase.Active -> if (kind == VehicleCommandKind.Idle) "Raised idle · ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')} remaining" else "ABS and traction control off · Restore on gauge, then reset ignition"
        VehicleCommandPhase.Stopping -> if (kind == VehicleCommandKind.Idle) "Returning to normal idle" else "Ending control"
        VehicleCommandPhase.Verifying -> "Checking normal idle"
        VehicleCommandPhase.Restored -> if (kind == VehicleCommandKind.Idle) "Normal idle restored" else "ABS and traction control restored"
        VehicleCommandPhase.RestartRequired -> "After Restore, reset ignition while parked and check the dash"
        VehicleCommandPhase.Rejected -> "Command could not start"
        VehicleCommandPhase.Unknown -> "Outcome unknown · check the vehicle before another command"
    }
}

/** One typed contract for authenticated App commands and the gauge shortcut/menu.
 * The idle wire intent names the captured setting, never an invented RPM mapping. */
object VehicleCommandProtocol {
    fun request(status: VehicleCommandStatus, stop: Boolean, preset: IdlePreset? = null): ByteArray {
        require(if (stop) status.canStop else status.canStart)
        require(listOf(status.revision, status.link, status.boot).all { it in 1..0xffffffffL })
        require(if (status.kind == VehicleCommandKind.Idle && !stop) preset != null && preset.rpm == null else preset == null)
        val operation = if (stop) status.operation else (status.lastOperation + 1).also { require(it in 1..0xffffffffL) }
        require(operation in 1..0xffffffffL)
        return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(if (status.kind == VehicleCommandKind.Burnout) 2.toByte() else 1.toByte())
            put(status.kind.wire.toByte())
            put(if (stop) 2.toByte() else 1.toByte())
            put((preset?.id?.wire ?: 0).toByte())
            putInt(operation.toInt())
            putInt(status.revision.toInt())
            putInt(status.link.toInt())
            putInt(status.boot.toInt())
            putShort((preset?.seconds ?: 0).toShort())
            putShort(0)
        }.array()
    }
    fun status(bytes: ByteArray): VehicleCommandStatus {
        require(bytes.size == 32 && bytes[0] == 22.toByte())
        fun u8(i: Int) = bytes[i].toInt() and 255
        fun u32(i: Int) = ByteBuffer.wrap(bytes, i, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        val kind = VehicleCommandKind.entries.single { it.wire == u8(1) }
        val phase = VehicleCommandPhase.entries.getOrNull(u8(2)) ?: error("Unknown command state")
        require(u8(3) in 0..9 && u8(27) and 0xe0 == 0)
        val seconds = u8(24) or (u8(25) shl 8)
        val preset = IdlePresetId.entries.firstOrNull { it.wire == u8(26) }
        require(seconds <= 600 && u8(26) in 0..2 && u32(16) != 0L)
        require(
            when (kind) {
                VehicleCommandKind.Idle -> true
                VehicleCommandKind.AbsTcs -> seconds == 0 && preset == null
                VehicleCommandKind.Burnout -> seconds <= 10 && preset == null
            }
        )
        require(phase != VehicleCommandPhase.Active || u32(4) != 0L)
        require(u8(27) and 1 == 0 || (u32(8) > 0 && u32(12) > 0))
        require(kind == VehicleCommandKind.AbsTcs || u8(27) and 2 == 0)
        require(kind == VehicleCommandKind.Idle || (u8(27) and 0x14 == 0 && u8(3) != 9))
        require(kind != VehicleCommandKind.AbsTcs || u8(27) and 8 == 0)
        require(kind != VehicleCommandKind.Burnout || (u8(26) == 0 && phase != VehicleCommandPhase.RestartRequired))
        return VehicleCommandStatus(
            kind, phase, u8(3), u32(4), u32(8), u32(12), u32(16), u32(20), seconds, preset,
            u8(27) and 1 != 0, u8(27) and 2 != 0, u32(28),
            u8(27) and 4 != 0, u8(27) and 8 != 0, u8(27) and 16 != 0
        )
    }
}

interface VehicleCommandSession {
    suspend fun read(kind: VehicleCommandKind): VehicleCommandStatus
    suspend fun writeOnce(request: ByteArray)
}

interface VehicleCommandSessions {
    suspend fun <T> use(action: suspend VehicleCommandSession.() -> T): T
}

/** Read reconciliation is bounded. An uncertain write is never resubmitted. */
class VehicleCommandClient(private val sessions: VehicleCommandSessions, private val pause: suspend () -> Unit = { delay(150) }) {
    private data class Readback(val before: VehicleCommandStatus, val uncertain: VehicleCommandStatus)
    private sealed interface Outcome {
        data class Complete(val status: VehicleCommandStatus) : Outcome
        data class Reconcile(val context: Readback) : Outcome
    }

    suspend fun execute(kind: VehicleCommandKind, stop: Boolean, preset: IdlePreset? = null, expected: VehicleCommandStatus? = null): VehicleCommandStatus {
        val outcome = sessions.use {
            val before = read(kind)
            require(before.kind == kind)
            require(expected == null || (before.kind == expected.kind && before.boot == expected.boot && before.revision == expected.revision && before.link == expected.link && (!stop || before.operation == expected.operation))) { "Vehicle command context changed before confirmation" }
            val request = VehicleCommandProtocol.request(before, stop, preset)
            val operation = ByteBuffer.wrap(request, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            val context = Readback(before, before.copy(phase = VehicleCommandPhase.Unknown, operation = operation, preset = preset?.id ?: before.preset))
            try {
                writeOnce(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Leave the retired owner before reconciling. Never resubmit the mutation.
                return@use Outcome.Reconcile(context)
            }
            Outcome.Complete(readback(this, context))
        }
        return when (outcome) {
            is Outcome.Complete -> outcome.status

            is Outcome.Reconcile -> try {
                sessions.use { readback(this, outcome.context) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                outcome.context.uncertain
            }
        }
    }

    private suspend fun readback(session: VehicleCommandSession, context: Readback): VehicleCommandStatus {
        val before = context.before
        var uncertain = context.uncertain
        repeat(70) {
            pause()
            val observed = try {
                session.read(before.kind)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return uncertain
            }
            if (observed.kind != before.kind || observed.boot != before.boot || observed.revision != before.revision || observed.link != before.link) return uncertain
            if (observed.operation == context.uncertain.operation) {
                uncertain = observed.copy(phase = VehicleCommandPhase.Unknown)
                if (observed.phase !in setOf(VehicleCommandPhase.Starting, VehicleCommandPhase.Stopping, VehicleCommandPhase.Verifying)) return observed
            }
        }
        return uncertain
    }
}
