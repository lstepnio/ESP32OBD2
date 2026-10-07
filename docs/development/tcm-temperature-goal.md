# Physical TCM temperature goal

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

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

## Challenger candidates and a positive temperature payload

[OBDb's Challenger signal definitions](https://github.com/OBDb/Dodge-Challenger/blob/d2d9fda9119e1c61a806e32c657415e8e3ffc0bb/signalsets/v3/default.json)
and its 2019/2021 response fixtures provide two further bounded TCM reads.
Definitions and fixture examples are attributed to OBDb under
[CC-BY-SA-4.0](https://github.com/OBDb/Dodge-Challenger/blob/main/LICENSE).
Our interpretation and JSS observations are additions; the source does not
establish compatibility with this 2016 calibration or the JSS bridge.

- `7E1 / 22 50 43`: three positive `62 50 43 00` responses with engine off,
  and the same three with owner-confirmed engine idling. Published math gives
  -40 C; this is not accepted as credible live fluid temperature.
- `7E1 / 22 04 FE`: three positive `7E9 06 62 04 FE 55 54 55` responses in
  that idling session. The documented first-byte conversion gives **45 C /
  113 F**. Other bytes, 84 and 85, are retained without assigning sensor names.
  This is a plausible experimental interpretation, not independently validated
  sensor meaning. Only the same TCM responder/calibration was observed.

Private capture suffixes: `20261007T032535.989351Z` (5043 engine off),
`20261007T032621.148874Z` (5043 idling), and `20261007T032708.792942Z` (04FE
idling), each followed by `-transmission-mac_ble`. All adapter restorations
were confirmed. These observations are Mac BLE evidence, not LCD verification.

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
  and timeout evidence retention. All 44 host OBD tests passed, including the
  separately opted-in `--tcm-temperature-v2` (5043) and `--tcm-temperature-v3`
  (04FE) routes.

The App now has a debug-only Expert action creating a separate saved **JSS TCM
temperature test** profile. It copies the selected adapter binding, preserves
the engine profile, and sends one experimental TCM definition via the existing
configuration transaction. Source, route, prefix, payload length and scaling
are explicit. Normal engine alert choices remain unchanged; this test profile
does not support alerts or mixing engine and transmission readings.

The firmware adds a bounded Mode 22 single-frame parser with full identifier and
ECU matching. The existing adapter worker can initialize one active TCM source,
verify its support reply and select physical header 7E1. The runtime accepts
only the captured 04FE definition for the experimental TCM source, with exact
three-byte payload and first-byte scaling. UI/sample identifiers now retain all
16 bits. Existing numeric display, units, scheduling, persistence and stale
handling are reused. The lab range 0..180 C deliberately fails closed outside
the warm-vehicle test range; it is not a production sensor validity definition.
ECM fault requests are suppressed for this TCM-only profile.

Parser sanitizer tests cover the actual reply, fragmentation, wrong ECU/DID,
malformed/duplicate frames, negative replies, overflow and adapter errors. Runtime
compiler tests check the actual decoder and reject wrong routes/identifiers and
zero placeholders. All 56 Android tests passed, including source separation and
saved TCM profile readback. Firmware **0.2.0-dev.36-tcm** built successfully and
was signed for the authorized App/Wi-Fi installation. Exact image/APK identities
and a phone-preference backup are private in `artifacts/tcm-temperature-install/`.

The image was transferred through App/Wi-Fi, and passive USB boot output identifies
`0.2.0-dev.36-tcm`. The initial post-update App confirmation was delayed by BLE
reconnection. After applying the corrected TCM profile, passive USB boot output
confirms running configuration revision 27 with one PID, one page and no alerts,
and the adapter link becomes ready. The owner separately reported **45** on the
physical gauge with the Vgate in the TCM connector and ignition ON/RUN.
The owner confirmed **45 C**, then dashes after unplugging the Vgate with the gauge
powered. The App independently read installed version `0.2.0-dev.36-tcm`, OTA
state 2 (valid), and ELF SHA-256
`b30d0de7d56922eafdc1f0882d47a3516e259a7d0ffaee98d789ccd40de9c090`.
The owner confirmed 45 C returned after reconnecting the Vgate. App configuration
readback confirms revision 27 is running, with no trial or previous-generation
fallback. The prior engine profile remains saved on the phone. Physical
Celsius display, loss clearing and reconnect recovery passed. Independent sensor
meaning and Fahrenheit display remain unqualified.
The first TCM configuration attempt was rejected by document validation because
the App's source label exceeded 32 characters. The label was shortened, a
regression assertion added, and Android tests/build and C runtime tests passed.
Public capabilities remain disabled. Existing coolant PID 05 is not a substitute
for transmission-fluid temperature.

## Next route and acceptance criteria

Install the experimental 04FE path through App/Wi-Fi and verify its visible value,
stale/recovery behavior, and units. Obtain a matching reference from JSS/PCS or a
compatible diagnostic tool to validate sensor meaning. The PCS controller is hidden and the matching PCS laptop,
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

No supplier message has been sent. The experimental definition is now integrated
into the existing firmware scheduler/display and App's explicit TCM test profile.
Physical acceptance requires a plausible live reading, correct units, stale
data becoming unavailable after disconnect, recovery after reconnect, and a
trusted reference where available. Experimental meaning must be visible until
validated; merely compiling or replaying a synthetic fixture does not finish
the goal.

## Combined workspace follow-up

The subsequent parked gear comparison identified a useful current-gear read.
The companion now upgrades the same TCM profile into a Transmission setup with
Gear + Temperature together, using the existing page editor and adapter binding.
Firmware dev.37-tcm was installed through App/Wi-Fi, and the owner confirmed the
combined physical page worked. Normal labels omit test qualifiers; evidence and
remaining qualification live in Expert/provenance. See [gear capture and combined
setup](tcm-gear-capture.md) for implementation and acceptance details.
