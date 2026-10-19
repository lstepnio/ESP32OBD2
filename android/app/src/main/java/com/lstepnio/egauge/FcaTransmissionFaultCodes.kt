package com.lstepnio.egauge

/** FCA eight-speed reference subset, not a universal ZF or calibration-complete database.
 * Source identities and coverage limits are maintained in alerts-and-diagnostics.md.
 */
internal object FcaTransmissionFaultCodes {
    private const val PARK = "FCA PE15-030 Enclosure 6, 2015 (8HP70/845RE)"
    private const val CONTROL = "FCA TSB 21-009-20, 2020 (8HP75)"
    private const val CONVERTER = "Stellantis TSB 21-022-25, 2025 (850RE)"
    private const val NEXT = "Use the matching vehicle or controller service information to investigate this condition. This reference does not identify a failed part or establish that it is expected after a swap."
    private fun entry(title: String, explanation: String, source: String) = FaultMeaning(title, explanation, NEXT, source, "Chrysler/Jeep")
    val entries: Map<String, FaultMeaning> = mapOf(
        "P1D77" to entry("Park solenoid response", "The park solenoid response was outside expectations.", PARK),
        "P1D78" to entry("Park solenoid signal low", "The park solenoid circuit signal was low.", PARK),
        "P1D79" to entry("Park solenoid signal high", "The park solenoid circuit signal was high.", PARK),
        "P1D8A" to entry("Park pawl signal low", "The park pawl solenoid signal was low.", PARK),
        "P1D8B" to entry("Park pawl signal high", "The park pawl solenoid signal was high.", PARK),
        "P1D8C" to entry("Park pawl circuit interrupted", "The park pawl solenoid circuit was interrupted.", PARK),
        "P1D8D" to entry("Park sensing signal low", "The park sensing circuit signal was low.", PARK),
        "P1D8E" to entry("Park sensing signal high", "The park sensing circuit signal was high.", PARK),
        "P1DCA" to entry("Park sense circuit open", "The park-position sensing circuit was reported interrupted.", PARK),
        "P1DD0" to entry("Unexpected park engagement", "Park engagement did not match the expected command.", PARK),
        "P0850" to entry("Park/Neutral input fault", "The Park/Neutral input did not match expectations.", PARK),
        "P081C" to entry("Park input circuit fault", "The park input circuit reported a fault.", PARK),
        "P1B14" to entry("Unexpected park position", "The park-by-wire position did not match expectations.", PARK),
        "P1B13" to entry("Unexpected exit from park", "The park-by-wire state unexpectedly left park.", PARK),
        "P1DDE" to entry("Ignition inputs disagree", "Two ignition input signals did not agree.", CONTROL),
        "P1DF3" to entry("Controller not initialized", "The expected controller initialization state was not established.", CONTROL),
        "P0733" to entry("Gear 3 ratio mismatch", "The observed third-gear ratio did not match expectations.", CONTROL),
        "P0716" to entry("Input speed signal unreliable", "The input speed signal was outside expectations.", CONTROL),
        "P0721" to entry("Output speed signal unreliable", "The output speed signal was outside expectations.", CONTROL),
        "P1DB6" to entry("Vehicle speed signal unreliable", "The vehicle speed signal was outside expectations.", CONTROL),
        "P1DAB" to entry("Vehicle speed unknown", "The controller could not establish vehicle speed.", CONTROL),
        "P215C" to entry("Output and wheel speeds disagree", "The output shaft and wheel speeds did not agree.", CONTROL),
        "P0607" to entry("Controller internal performance", "An internal controller performance check reported a fault.", CONTROL),
        "P1DAC" to entry("Engine speed unknown", "The controller could not establish engine speed.", CONTROL),
        "P07BF" to entry("Input speed signal low", "The first input speed sensor signal was low.", CONTROL),
        "P07C0" to entry("Input speed signal high", "The first input speed sensor signal was high.", CONTROL),
        "P077C" to entry("Output speed signal low", "The output speed sensor signal was low.", CONTROL),
        "P077D" to entry("Output speed signal high", "The output speed sensor signal was high.", CONTROL),
        "P177F" to entry("Output speed signal excessive", "The output speed sensor reported an excessive signal.", CONTROL),
        "P1778" to entry("Input speed signal excessive", "An input speed sensor reported an excessive signal.", CONTROL),
        "P0731" to entry("Gear 1 ratio mismatch", "The observed first-gear ratio did not match expectations.", CONTROL),
        "P0979" to entry("Shift solenoid C signal low", "The shift solenoid C control signal was low.", CONTROL),
        "P1DCD" to entry("Conflicting clutch engagement", "The controller detected conflicting clutch engagement.", CONTROL),
        "P1DB7" to entry("Converter clutch response", "The torque converter clutch response was outside expectations.", CONVERTER),
        "P0701" to entry("Transmission control response", "The transmission control system response was outside expectations.", CONVERTER)
    )
}
