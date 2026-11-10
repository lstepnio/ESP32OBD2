package com.lstepnio.egauge

internal const val DEFAULT_DAY_BRIGHTNESS = 90

data class SunsetLocation(val latitude: Int, val longitude: Int) {
    init {
        require(latitude in -9000..9000 && longitude in -18000..18000)
    }
}

data class SunsetBrightness(
    val automatic: Boolean,
    val night: Boolean,
    val clockKnown: Boolean,
    val nightBrightness: Int,
    val location: SunsetLocation,
    val revision: Long
)
