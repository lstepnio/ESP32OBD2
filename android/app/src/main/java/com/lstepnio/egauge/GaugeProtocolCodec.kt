package com.lstepnio.egauge

import org.json.JSONObject

object GaugeProtocolCodec {
    fun capabilities(bytes: ByteArray): CapabilitySnapshot {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val protocol = json.getInt("protocolMajor")
        require(protocol == 0) { "Unsupported gauge protocol $protocol" }
        val links = json.getInt("maxAdapterLinks")
        require(links in 0..2) { "Gauge returned an invalid adapter-link limit" }
        val configWrite = json.getBoolean("configWrite")
        val ota = json.getBoolean("ota")
        require(!configWrite && !ota) { "Unexpected experimental capability flags" }
        return CapabilitySnapshot(
            board = json.getString("board"),
            protocolMajor = protocol,
            maxAdapterLinks = links,
            simultaneousVerified = json.getBoolean("simultaneousAdapterLinksVerified"),
            configWrite = configWrite,
            experimentalNumericConfig = json.optBoolean("experimentalNumericConfig", false),
            savedStateRead = json.optBoolean("savedStateRead", false),
            quickSelect = json.optBoolean("quickSelect", false),
            displayRotationWrite = json.optBoolean("displayRotationWrite", false),
            ota = ota,
        )
    }

    fun diagnostics(bytes: ByteArray): GaugeConfigTransferClient.Diagnostics {
        require(bytes.size == 32 && bytes[0].toInt() == 5) {
            "Gauge returned an unsupported diagnostic snapshot"
        }
        val flags = bytes[1].toInt() and 255
        require(flags and 0xe0 == 0) { "Gauge returned malformed diagnostic flags" }
        fun code(offset: Int): String? {
            val first = bytes[offset].toInt() and 255
            val second = bytes[offset + 1].toInt() and 255
            if (first == 0 && second == 0) return null
            val system = "PCBU"[first ushr 6]
            return "$system${(first ushr 4) and 3}${(first and 15).toString(16).uppercase()}" +
                "${(second ushr 4).toString(16).uppercase()}${(second and 15).toString(16).uppercase()}"
        }
        return GaugeConfigTransferClient.Diagnostics(
            flags and 1 != 0,
            flags and 2 != 0,
            bytes[2].toInt() and 255,
            flags and 4 != 0,
            bytes[3].toInt() and 255,
            code(6),
            flags and 8 != 0,
            bytes[4].toInt() and 255,
            code(8),
            flags and 16 != 0,
            bytes[5].toInt() and 255,
            code(10),
        )
    }

    fun runtimeIdentity(bytes: ByteArray): GaugeConfigTransferClient.RuntimeIdentity {
        require(bytes.size == 44 && bytes[0].toInt() == 8 &&
            bytes.sliceArray(2..3).all { it == 0.toByte() }) {
            "Gauge returned an unsupported runtime identity"
        }
        val flags = bytes[1].toInt() and 255
        require(flags and 0xf8 == 0) { "Gauge returned malformed runtime identity flags" }
        val running = flags and 1 != 0
        val revision = u32(bytes, 4)
        val storedRevision = u32(bytes, 8)
        val digest = bytes.copyOfRange(12, 44)
        require((running && revision > 0 && storedRevision >= revision) ||
            (!running && revision == 0L && digest.all { it == 0.toByte() })) {
            "Gauge returned inconsistent runtime identity"
        }
        return GaugeConfigTransferClient.RuntimeIdentity(
            running,
            flags and 2 != 0,
            flags and 4 != 0,
            revision,
            storedRevision,
            digest.joinToString("") { "%02x".format(it) },
        )
    }

    private fun u32(value: ByteArray, offset: Int) = (0..3).fold(0L) { result, index ->
        result or ((value[offset + index].toLong() and 255) shl (index * 8))
    }
}

