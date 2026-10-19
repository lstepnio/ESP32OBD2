package com.lstepnio.egauge

import java.util.Locale

/** Authored explanations, not repair diagnoses. Source/review rules live in alerts-and-diagnostics.md. */
data class FaultMeaning(val title: String, val explanation: String, val nextStep: String, val reference: String? = null, val manufacturer: String? = null)

object FaultCodeCatalog {
    const val VERSION = "2026-10-19.2"
    private const val ENGINE = "Ford OBD-II PCED, diagnostic code descriptions"
    private const val NETWORK = "Ford 2017 diesel OBD summary, communication monitor"
    private const val REVIEW = "Have the related system checked using the vehicle's service information. A code alone does not identify a failed part."
    private fun meaning(title: String, explanation: String, reference: String = ENGINE) = FaultMeaning(title, explanation, REVIEW, reference)
    private val codePattern = Regex("[PCBU][0-3][0-9A-F]{3}")
    private val definitions = buildMap {
        put("P0300", meaning("Engine misfire", "The car detected uneven combustion in multiple or unidentified cylinders."))
        for (cylinder in 1..10) put("P%04d".format(Locale.ROOT, 300 + cylinder), meaning("Cylinder $cylinder misfire", "The car detected uneven combustion in cylinder $cylinder."))
        put("P0171", meaning("Fuel mixture too lean · bank 1", "The car detected too much air relative to fuel on one side of the engine."))
        put("P0174", meaning("Fuel mixture too lean · bank 2", "The car detected too much air relative to fuel on the other side of the engine."))
        put("P0172", meaning("Fuel mixture too rich · bank 1", "The car detected too much fuel relative to air on one side of the engine."))
        put("P0175", meaning("Fuel mixture too rich · bank 2", "The car detected too much fuel relative to air on the other side of the engine."))
        put("P0420", meaning("Catalyst performance · bank 1", "The car detected lower than expected emissions-conversion performance on one side of the engine."))
        put("P0430", meaning("Catalyst performance · bank 2", "The car detected lower than expected emissions-conversion performance on the other side of the engine."))
        put("P0128", meaning("Engine warming too slowly", "Coolant did not reach the expected temperature under the conditions checked by the car.", "Ford 2018 gasoline OBD summary, thermostat monitor"))
        put("U0121", meaning("ABS communication lost", "The reporting controller did not receive expected messages from the anti-lock brake controller.", NETWORK))
        put("U0140", meaning("Body communication lost", "The reporting controller did not receive expected messages from the body controller.", NETWORK))
        putAll(FcaTransmissionFaultCodes.entries)
    }
    fun lookup(raw: String): FaultMeaning {
        val code = raw.trim().uppercase(Locale.ROOT)
        definitions[code]?.let { return it }
        val valid = codePattern.matches(code)
        return FaultMeaning(
            if (valid) "Code explanation unavailable" else "Unrecognized fault code",
            if (valid) "A verified explanation for $code is not included. Its meaning may depend on the vehicle and controller." else "The reported code does not match the supported five-character format.",
            "Use the matching vehicle or controller service information to confirm this code's meaning before choosing a repair."
        )
    }
}
