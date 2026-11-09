package com.lstepnio.egauge

import java.util.Locale

/** Authored explanations, not repair diagnoses. Source/review rules live in alerts-and-diagnostics.md. */
data class FaultMeaning(val title: String, val explanation: String, val nextStep: String, val reference: String? = null, val manufacturer: String? = null) {
    val referenceTitle: String get() = manufacturer?.let { "$title ($it reference)" } ?: title
}

object FaultCodeCatalog {
    const val VERSION = "2026-11-09.1"
    private const val ENGINE = "Ford OBD-II PCED, diagnostic code descriptions"
    private const val GASOLINE_2012 = "Ford 2012 gasoline OBD operation summary"
    private const val GASOLINE_2024 = "Ford 2024–2025 gasoline OBD operation summary"
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
        put("P0101", meaning("Airflow reading out of range", "The measured intake airflow did not agree with the engine's expected airflow.", GASOLINE_2012))
        put("P0130", meaning("Oxygen sensor circuit · bank 1 sensor 1", "The upstream oxygen sensor circuit on bank 1 failed a signal check.", GASOLINE_2012))
        put("P0131", meaning("Oxygen sensor voltage low · bank 1 sensor 1", "The upstream oxygen sensor signal on bank 1 stayed below the monitored limit.", GASOLINE_2012))
        put("P0132", meaning("Oxygen sensor voltage high · bank 1 sensor 1", "The upstream oxygen sensor signal on bank 1 stayed above the monitored limit.", GASOLINE_2012))
        put("P0442", meaning("Small fuel vapor leak detected", "The evaporative emissions system did not hold pressure or vacuum as expected during a small-leak check.", GASOLINE_2024))
        put("P0443", meaning("Fuel vapor purge valve circuit", "The purge valve electrical circuit failed a monitored check.", GASOLINE_2024))
        put("P0455", meaning("Large fuel vapor leak detected", "The evaporative emissions monitor detected a condition consistent with a large leak.", GASOLINE_2024))
        put("P0456", meaning("Very small fuel vapor leak detected", "The evaporative emissions monitor detected a leak in its smallest monitored range.", GASOLINE_2024))
        put("P0457", meaning("Possible loose fuel cap", "The evaporative emissions monitor reported a leak pattern associated with an open or loose filler closure.", GASOLINE_2024))
        put("P0506", meaning("Idle speed too low", "The engine ran below the monitored idle speed.", GASOLINE_2012))
        put("P0507", meaning("Idle speed too high", "The engine ran above the monitored idle speed.", GASOLINE_2012))
        put("P0700", meaning("Transmission requested warning lamp", "The transmission controller requested the warning lamp. Read its own fault codes for the underlying condition.", "Stellantis STAR publication MC-10241004; transmission MIL request"))
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
