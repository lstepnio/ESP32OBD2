package com.lstepnio.egauge

import org.json.JSONObject

/** Selected on the gauge. An advertisement does not establish adapter compatibility. */
data class AdapterBinding(val id: String, val address: String, val addressType: String,
                          val driver: String = "elm-18f0-v1") {
    init {
        require(id.matches(Regex("[a-z][a-z0-9._-]{0,63}"))) { "Adapter identity is invalid" }
        require(address.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))) { "Adapter address is invalid" }
        require(addressType in setOf("public", "random") && driver in setOf("elm-18f0-v1", "elm-bench-v1")) { "Adapter transport is not supported" }
    }
    fun json() = JSONObject().put("id", id).put("address", address)
        .put("addressType", addressType).put("driver", driver)
    companion object {
        fun decode(value: JSONObject) = AdapterBinding(value.getString("id"), value.getString("address"),
            value.getString("addressType"), value.getString("driver"))
    }
}

data class AdapterCandidate(val name: String, val binding: AdapterBinding)

data class AdapterSourceStatus(val phase: Int, val result: Int, val bound: Boolean,
    val generation: Long, val evidenceAgeMs: Long, val ecu: Long, val vehicleId: String,
    val sourceId: String, val simulated: Boolean, val supportMaps: Map<Int, Long>, val uptimeMs: Long) {
    val message: String get() = if (simulated) when (phase) {
        4 -> "Bench simulator connected. These are example readings, with no vehicle connected."
        2, 3 -> "Connecting to the bench simulator. No vehicle is connected."
        6 -> "Bench simulator paused while the gauge update connection is open."
        else -> "Bench simulator unavailable. Example readings are not live."
    } else when (phase) {
        0 -> "No adapter selected on the gauge."
        1 -> "Waiting for the vehicle adapter."
        2 -> "Connecting to the vehicle adapter."
        3 -> "Preparing the vehicle adapter."
        4 -> "Adapter ready. Vehicle readings still need to be checked."
        5 -> "Adapter setup did not finish. Keep it powered and try again."
        6 -> "Adapter paused while the gauge update connection is open."
        else -> "Adapter status is unavailable."
    }
}
