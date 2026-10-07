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

    /** Shape gate for a successful encrypted owner read; payload decoding stays with each reader. */
    fun isProtectedStatusFrame(bytes: ByteArray): Boolean = when (bytes.firstOrNull()?.toInt()?.and(255)) {
        2 -> bytes.size == 8
        3 -> bytes.size == 64
        4 -> bytes.size == 56
        5 -> bytes.size == 32
        6 -> bytes.size == 60
        7 -> bytes.size in 52..180
        8 -> bytes.size == 44
        9 -> bytes.size == 112
        10 -> bytes.size == 8 || bytes.size == 56
        11 -> bytes.size == 8
        12 -> bytes.size == 10
        13 -> bytes.size == 140
        14 -> bytes.size == 160
        15 -> bytes.size == 248
        else -> false
    }

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
        require(configurationVersion in 0..4) { "Gauge returned an invalid configuration version" }
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
            quickSelect = json.optBoolean("quickSelect", json.optBoolean("qs", false)),
            displayRotationWrite = json.optBoolean("displayRotationWrite", false),
            displaySettingsVersion = json.optInt("ds", 0).also {
                require(it in 0..3) { "Unsupported display settings version" }
            },
            ota = ota,
            wifiBulk = json.optString("wifiBulk").takeIf { it.isNotBlank() },
            hardwareCapacityVersion = hardwareCapacity.takeIf { it > 0 },
            configurationVersion = configurationVersion,
            vehicleDashboardVersion = json.optInt("va", 0).also { require(it in 0..1) },
            dualAdapterVersion = json.optInt("da", 0).also { require(it in 0..1) },
            pageActionsVersion = if (configurationVersion >= 4) 1 else 0,
            adapterRegistryVersion = json.optInt("ad", 0).also { require(it in 0..1) },
            maxPages = if (configurationVersion >= 2) 8 else if (configurationVersion == 1) 3 else 0,
            supportedRenderers = when (configurationVersion) {
                2, 3, 4 -> GaugeLayout.entries.toSet()
                1 -> setOf(GaugeLayout.Numeric)
                else -> emptySet()
            },
        )
    }

    fun displaySettings(bytes: ByteArray): GaugeConfigTransferClient.DisplaySettings {
        val version = if (bytes.isNotEmpty()) bytes[0].toInt() and 255 else -1
        require(((bytes.size == 8 && version in 10..11) || (bytes.size == 10 && version == 12)) &&
            (version >= 11 || bytes[3].toInt() == 0)) {
            "Gauge returned unsupported display settings"
        }
        val rotation = bytes[1].toInt() and 255
        val brightness = bytes[2].toInt() and 255
        val units = bytes[3].toInt() and 255
        require(rotation in 0..3 && brightness in 5..100 && units in 0..1) {
            "Gauge returned invalid display settings"
        }
        val cycleSeconds = if (version == 12) (bytes[8].toInt() and 255) or ((bytes[9].toInt() and 255) shl 8) else 0
        require(cycleSeconds in setOf(0, 5, 10, 15, 30, 60)) { "Gauge returned invalid page cycle interval" }
        return GaugeConfigTransferClient.DisplaySettings(rotation, brightness, u32(bytes, 4),
            MeasurementSystem.entries[units], when (version) { 12 -> 3; 11 -> 2; else -> 1 }, cycleSeconds)
    }

    fun diagnostics(bytes: ByteArray): GaugeConfigTransferClient.Diagnostics {
        if (bytes.size == 248 && bytes[0].toInt() == 15) return fullDiagnostics(bytes)
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
            milKnown = flags and 3 != 0 || u32(bytes, 24) != 0L,
            milAgeMs = (u32(bytes, 28) - u32(bytes, 24)) and 0xffffffffL,
        )
    }

    private fun fullDiagnostics(bytes: ByteArray): GaugeConfigTransferClient.Diagnostics {
        fun byte(offset: Int) = bytes[offset].toInt() and 255
        val source = byte(1)
        val flags = byte(2)
        val connected = flags and 4 != 0
        val simulated = flags and 8 != 0
        val milKnown = byte(23) == 1
        val milState = byte(3)
        val ecu = byte(20) or (byte(21) shl 8)
        require(source in 0..1 && flags and 0xf0 == 0 && milState in 0..3 &&
            byte(23) in 0..1 && byte(22) <= 127 && bytes.sliceArray(24..31).all { it == 0.toByte() } &&
            ecu == if (source == 1) 0x7e9 else 0x7e8) { "Malformed diagnostic source/header" }
        val now = u32(bytes, 12)
        val milAge = if (milKnown) (now - u32(bytes, 16)) and 0xffffffffL else null
        require((milState != 1 || milKnown) && (flags and 2 == 0 || milKnown) &&
            (milKnown || (u32(bytes, 16) == 0L && byte(22) == 0))) { "Malformed MIL evidence" }
        require(flags and 1 == 0 || (milKnown && connected && !simulated && milState == 1 && milAge!! <= 60_000)) {
            "Malformed diagnostic MIL freshness"
        }
        val categories = (0..2).map { index ->
            val offset = 32 + 72 * index
            val state = byte(offset)
            val categoryFlags = byte(offset + 1)
            val count = byte(offset + 2)
            val known = categoryFlags and 1 != 0
            val fresh = categoryFlags and 2 != 0
            val age = if (known) (now - u32(bytes, offset + 4)) and 0xffffffffL else null
            require(state in 0..3 && categoryFlags and 0xf8 == 0 && count <= 32 && byte(offset + 3) == 0 &&
                (state != 1 || known) && (known || (count == 0 && u32(bytes, offset + 4) == 0L)) &&
                (!fresh || (known && state == 1 && connected && !simulated && age!! <= 120_000))) {
                "Malformed diagnostic category"
            }
            val codes = (0 until count).map { codeIndex ->
                diagnosticCode(bytes, offset + 8 + codeIndex * 2).also {
                    require(it != null) { "Zero entry in diagnostic code list" }
                }!!
            }
            require(codes.distinct().size == codes.size &&
                bytes.sliceArray(offset + 8 + count * 2 until offset + 72).all { it == 0.toByte() }) {
                "Duplicate diagnostic code or nonzero list padding"
            }
            DiagnosticCategory(listOf("Stored", "Pending", "Permanent")[index],
                DiagnosticAvailability.entries[state], known, fresh, codes, age, categoryFlags and 4 != 0)
        }
        return GaugeConfigTransferClient.Diagnostics(flags and 1 != 0, flags and 2 != 0, byte(22),
            categories[0].fresh, categories[0].codes.size, categories[0].codes.firstOrNull(),
            categories[1].fresh, categories[1].codes.size, categories[1].codes.firstOrNull(),
            categories[2].fresh, categories[2].codes.size, categories[2].codes.firstOrNull(),
            if (source == 1) "TCM" else "ECM", ecu, u32(bytes, 4), u32(bytes, 8), connected, simulated,
            milKnown, DiagnosticAvailability.entries[milState], milAge, categories)
    }

    private fun diagnosticCode(bytes: ByteArray, offset: Int): String? {
        val a = bytes[offset].toInt() and 255
        val b = bytes[offset + 1].toInt() and 255
        if (a == 0 && b == 0) return null
        return "${"PCBU"[a ushr 6]}${(a ushr 4) and 3}${(a and 15).toString(16).uppercase()}${b.toString(16).uppercase().padStart(2, '0')}"
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
