# Physical TCM temperature goal

Updated 2026-10-06. Goal remains active: display a live transmission-fluid
temperature from the JSS/PCS 8HP70 setup on the physical eGauge, updated through
the App/Wi-Fi path and verified on the LCD. No temperature has been displayed yet.

## New bounded candidate and actual result

[ScanGauge's EcoDiesel temperature definition](https://www.scangauge.com/xgauge/3-0l-ecodiesel-transmission-temperature/)
documents physical request `7E1 / 22 08 DF`, positive response `62 08 DF`, one
temperature byte, and math `A * 9/5 - 40` Fahrenheit, equivalent to `A - 40`
Celsius. This application's compatibility with the Hemi/JSS calibration is unknown.
The separate `22 81 02` alternative on ScanGauge's aggregate page belongs to
the 2013-2024 Cummins section, not the EcoDiesel section; it was not tested.

The owner confirmed Vgate in the separate TCM connector, ignition ON/RUN,
engine off, gauge off. The Mac identified only responder `7E9`, calibration
`68274867AE`, CVN `8240DAE8`, and made three `2208DF` requests via `ATSH7E1`.
All three returned `7E9 03 7F 22 31`. No positive temperature payload occurred.
This records rejection of this request under those conditions, not the absence
of a transmission sensor or of every possible diagnostic route.

Private evidence: ignored capture directory
`artifacts/vehicle-captures/20261007T032225.686329Z-transmission-mac_ble/`,
including raw transactions, identity, candidate results and separate observations.
Header, CAN formatting and temporary protocol state were restored successfully.

## Implementation and verification

- `tools/obd_tcm_temperature_candidate.py`: exact responder/prefix/length parser;
  negative responses and NO DATA cannot produce a numeric reading. Candidate
  readings are always unqualified, including all byte values; vendor invalid
  sensor codes remain unknown.
- `tools/obd_explore.py --tcm-temperature`: explicit opt-in, transmission label,
  only `7E9` on protocol 6/A6, three fixed reads, clean prompt recovery/header
  restoration and preservation of partial raw evidence. Runs separately from
  the fault, ECM temperature and CAN-monitor probes.
- `tools/tests/test_obd_tcm_temperature.py`: synthetic scaling, malformed frames,
  wrong ECU, negative responses, policy boundaries, CLI exclusions, restoration
  and timeout evidence retention. All 40 host OBD tests passed.

No App configuration or installed gauge firmware changed on this test. Public
capabilities remain disabled. Existing coolant PID 05 is not a substitute for
transmission-fluid temperature.

## Next route and acceptance criteria

Obtain a calibration-matched read-only temperature definition or CAN map from
JSS/PCS, or a recording from a compatible diagnostic tool that actually reports
fluid temperature. The PCS controller is hidden and the matching PCS laptop,
calibration and logs are unavailable. Do not make controller access a repeated
prerequisite for testing through the accessible TCM diagnostic connector.

Provide the supplier these exact questions:

1. For PCS TCM-2800 in the JSS 8HP70 conversion with observed TCM calibration
   68274867AE/CVN 8240DAE8, can fluid temperature be read through the separate
   diagnostic connector at 7E1/7E9?
2. What service, identifier, response length, scaling, invalid-value codes and
   session requirements apply? Current default-session `22 08 DF` is rejected
   with `7F 22 31`.
3. If only CAN broadcast is supported, what bus, identifier, byte/bit positions,
   scaling, validity/checksum/counter and update period apply, and does the JSS
   bridge expose that message at the diagnostic connector?
4. Is the reading actual fluid temperature, modeled temperature or electronics
   temperature? What tool can provide an independent matching reference?

No supplier message has been sent. A definition is then tested with one bounded
read, decoded with source and validity checks, and integrated into the existing
firmware scheduler/display and App's explicit TCM source configuration.
Physical acceptance requires a plausible live reading, correct units, stale
data becoming unavailable after disconnect, recovery after reconnect, and a
trusted reference where available. Experimental meaning must be visible until
validated; merely compiling or replaying a synthetic fixture does not finish
the goal.
