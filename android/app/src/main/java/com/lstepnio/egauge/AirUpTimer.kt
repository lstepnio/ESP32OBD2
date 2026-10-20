package com.lstepnio.egauge

import org.json.JSONObject

/** Four tires together, calibrated against the owner's compressor, tires and hose setup.
 * Pressure is input, never telemetry. No universal flow rate or target PSI is assumed. */
data class AirUpTimer(
    val currentPsi: Int,
    val targetPsi: Int,
    val referenceFromPsi: Int,
    val referenceToPsi: Int,
    val referenceSeconds: Int,
    val overrideSeconds: Int? = null
) {
    init {
        require(currentPsi in 0..149 && targetPsi in 1..150 && targetPsi > currentPsi)
        require(referenceFromPsi in 0..149 && referenceToPsi in 1..150 && referenceToPsi > referenceFromPsi)
        require(referenceSeconds in 1..7200)
        require(overrideSeconds == null || overrideSeconds in 10..600)
    }
    val estimatedSeconds: Int get() {
        val delta = referenceToPsi - referenceFromPsi
        return ((targetPsi - currentPsi) * referenceSeconds + delta - 1) / delta
    }
    val sessionSeconds: Int get() = (overrideSeconds ?: estimatedSeconds).also { require(it in 10..600) }
    val outsideReferenceRange get() = currentPsi < referenceFromPsi || targetPsi > referenceToPsi
    fun json(): JSONObject = JSONObject()
        .put("currentPsi", currentPsi).put("targetPsi", targetPsi)
        .put("referenceFromPsi", referenceFromPsi).put("referenceToPsi", referenceToPsi)
        .put("referenceSeconds", referenceSeconds).put("overrideSeconds", overrideSeconds ?: JSONObject.NULL)

    companion object {
        fun decode(value: JSONObject): AirUpTimer {
            require(value.keys().asSequence().toSet() == setOf("currentPsi", "targetPsi", "referenceFromPsi", "referenceToPsi", "referenceSeconds", "overrideSeconds"))
            fun integer(key: String): Int {
                val n = value.get(key)
                require(n is Number && n.toDouble() == n.toInt().toDouble())
                return n.toInt()
            }
            return AirUpTimer(integer("currentPsi"), integer("targetPsi"), integer("referenceFromPsi"), integer("referenceToPsi"), integer("referenceSeconds"), if (value.isNull("overrideSeconds")) null else integer("overrideSeconds"))
        }
    }
}
