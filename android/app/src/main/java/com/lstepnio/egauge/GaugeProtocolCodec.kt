package com.lstepnio.egauge

import org.json.JSONObject

object GaugeProtocolCodec {
    const val HARDWARE_WIFI = 1L shl 0
    const val HARDWARE_BLE = 1L shl 1
    const val HARDWARE_PSRAM = 1L shl 2
    const val HARDWARE_DISPLAY = 1L shl 3
    const val HARDWARE_TOUCH = 1L shl 4
    const val HARDWARE_BACKLIGHT = 1L shl 5
    const val HARDWARE_IMU = 1L shl 6
    const val HARDWARE_BATTERY_ADC = 1L shl 7
    const val HARDWARE_EXPANSION = 1L shl 8
    const val HARDWARE_USB_UART = 1L shl 9
    private const val HARDWARE_KNOWN_MASK = (1L shl 10) - 1

    fun capabilities(bytes: ByteArray): CapabilitySnapshot {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val protocol = json.getInt("protocolMajor")
        require(protocol == 0) { "Unsupported gauge protocol $protocol" }
        val links = json.getInt("maxAdapterLinks")
        require(links in 0..2) { "Gauge returned an invalid adapter-link limit" }
        val configWrite = json.getBoolean("configWrite")
        val ota = json.getBoolean("ota")
        val hardwareCapacity = json.optInt("hardwareCapacity", json.optInt("hw", 0))
        val configurationVersion = json.optInt("cfg",
            if (json.optBoolean("experimentalNumericConfig", false)) 1 else 0)
        require(configurationVersion in 0..2) { "Gauge returned an invalid configuration version" }
        require(hardwareCapacity in 0..1) { "Gauge returned an invalid hardware-capacity version" }
        require(!configWrite && !ota) { "Unexpected experimental capability flags" }
        return CapabilitySnapshot(
            board = json.getString("board"),
            protocolMajor = protocol,
            maxAdapterLinks = links,
            simultaneousVerified = json.optBoolean("simultaneousAdapterLinksVerified", false),
            configWrite = configWrite,
            experimentalNumericConfig = configurationVersion > 0,
            savedStateRead = json.optBoolean("savedStateRead", false),
            quickSelect = json.optBoolean("quickSelect", false),
            displayRotationWrite = json.optBoolean("displayRotationWrite", false),
            displaySettingsVersion = json.optInt("ds", 0).also {
                require(it in 0..2) { "Unsupported display settings version" }
            },
            ota = ota,
            wifiBulk = json.optString("wifiBulk").takeIf { it.isNotBlank() },
            hardwareCapacityVersion = hardwareCapacity.takeIf { it > 0 },
            configurationVersion = configurationVersion,
            maxPages = if (configurationVersion >= 2) 8 else if (configurationVersion == 1) 3 else 0,
            supportedRenderers = when (configurationVersion) {
                2 -> GaugeLayout.entries.toSet()
                1 -> setOf(GaugeLayout.Numeric)
                else -> emptySet()
            },
        )
    }

    fun displaySettings(bytes: ByteArray): GaugeConfigTransferClient.DisplaySettings {
        val version = if (bytes.isNotEmpty()) bytes[0].toInt() and 255 else -1
        require(bytes.size == 8 && version in 10..11 &&
            (version == 11 || bytes[3].toInt() == 0)) {
            "Gauge returned unsupported display settings"
        }
        val rotation = bytes[1].toInt() and 255
        val brightness = bytes[2].toInt() and 255
        val units = bytes[3].toInt() and 255
        require(rotation in 0..3 && brightness in 5..100 && units in 0..1) {
            "Gauge returned invalid display settings"
        }
        return GaugeConfigTransferClient.DisplaySettings(rotation, brightness, u32(bytes, 4),
            MeasurementSystem.entries[units], if (version == 11) 2 else 1)
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

    fun hardwareSnapshot(bytes: ByteArray): GaugeConfigTransferClient.HardwareSnapshot {
        require(bytes.size == 56 && bytes[0].toInt() == 10 && bytes[1].toInt() == 1) {
            "Gauge returned an unsupported hardware snapshot"
        }
        val cores = bytes[3].toInt() and 255
        val cpuMhz = u16(bytes, 6)
        val declared = u32(bytes, 12)
        val initialized = u32(bytes, 16)
        val flash = u32(bytes, 20)
        val internalTotal = u32(bytes, 24)
        val internalFree = u32(bytes, 28)
        val internalMinimum = u32(bytes, 32)
        val internalLargest = u32(bytes, 36)
        val psramTotal = u32(bytes, 40)
        val psramFree = u32(bytes, 44)
        val psramMinimum = u32(bytes, 48)
        require(cores in 1..2 && cpuMhz in 80..240 &&
            declared and HARDWARE_KNOWN_MASK.inv() == 0L &&
            initialized and declared.inv() == 0L && flash in 4_000_000..64_000_000 &&
            internalFree <= internalTotal && internalMinimum <= internalFree &&
            internalLargest <= internalFree && psramFree <= psramTotal &&
            psramMinimum <= psramFree) { "Gauge returned inconsistent hardware capacity data" }
        return GaugeConfigTransferClient.HardwareSnapshot(
            chipModel = bytes[2].toInt() and 255,
            cores = cores,
            chipRevision = u16(bytes, 4),
            cpuMhz = cpuMhz,
            resetReason = bytes[8].toInt() and 255,
            wifiMode = bytes[9].toInt() and 255,
            declaredFeatures = declared,
            initializedFeatures = initialized,
            flashBytes = flash,
            internalTotalBytes = internalTotal,
            internalFreeBytes = internalFree,
            internalMinimumFreeBytes = internalMinimum,
            internalLargestBlockBytes = internalLargest,
            psramTotalBytes = psramTotal,
            psramFreeBytes = psramFree,
            psramMinimumFreeBytes = psramMinimum,
            uptimeSeconds = u32(bytes, 52),
        )
    }

    private fun u16(value: ByteArray, offset: Int) =
        (value[offset].toInt() and 255) or ((value[offset + 1].toInt() and 255) shl 8)

    private fun u32(value: ByteArray, offset: Int) = (0..3).fold(0L) { result, index ->
        result or ((value[offset + index].toLong() and 255) shl (index * 8))
    }
}
