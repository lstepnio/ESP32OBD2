# Jeep Wrangler JK diagnostic knowledge

Status: research as of 2026-09-28. The initial target is a 2007 Wrangler JK. The exact engine, transmission, ECU software, and installed modules for that vehicle still need to be captured. No eGauge live request, reading, or vehicle action has been verified on the Jeep.

These notes cover diagnostic data and control behavior relevant to eGauge. They do not define a ready-to-install vehicle profile. The [vehicle knowledge index](../README.md) defines the evidence terms used below.

## Vehicle and module scope

| Finding | Evidence | Implication |
| --- | --- | --- |
| The JK family is represented as 2007-2018. | Static analysis | Split definitions by year, engine, transmission, market, and ECU software. |
| The JK diagnostic profile identifies 11-bit CAN parsing with KWP2000 over CAN. | Static analysis | A generic Mode 01 ELM implementation does not cover all JK enhanced data or actions. |
| The profile has a 17-module allowlist: ABS, AMP, ASBS, CCN, ESM, HFM, HVAC, ITM, OCM, ORC, PCM, RADIO, SAS, TCM, TIPMCGW, TPM, WCM. | Static analysis | This is a list of possible modules, not proof that every JK contains or responds from each one. |
| PCM, TCM, ABS, and body/cluster modules have distinct diagnostic routes and definition families. | Static analysis | Retain the responding module and its identification with every reading and fault code. |

The vehicle profile selects a shared 11-bit CAN module index and references 21 adaptation-definition files. Those files are shared across vehicle families, so a referenced entry is not proof of JK support. The selected index holds request and response routing and bus details for the 17 candidate modules. It does not itself select every live-data or activation file. Those definitions need ECU identification and variant matching; a model name or VIN alone is insufficient.

## Readings

Start with standard Mode 01 discovery per responding ECU. RPM (`01 0C`), coolant temperature (`01 05`), vehicle speed (`01 0D`), load (`01 04`), intake-air temperature (`01 0F`), throttle position (`01 11`), and control-module voltage (`01 42`) are candidates, not a claim that this Jeep advertises all of them. Record the support bitmap, raw reply, ECU identity, decoded value, and sample age before offering a reading in the dashboard.

Enhanced definitions are not a universal list of PIDs. Static analysis found approximately 15,360 live-data resources across the broader vehicle catalog. A decoded record can specify a service or mode, identifier, byte position, conversion expression or factors, calibration fields, and unit. Some definitions require a specific ECU variant or diagnostic session. Their formulas cannot be copied directly into eGauge's bounded numeric decoder without checking supported operations and test vectors.

### Transmission temperature candidate

For a 2007-2011 JK with the 3.8 L petrol engine and an automatic transmission, `07E022B010` is a candidate physical request for transmission-fluid temperature. Treat `07E0` as part of the proposed route and `22 B0 10` as the proposed enhanced read, not as a universal JK PID. Static analysis found `B010` in 91 PCM profile records with three distinct decode signatures. Some records express degrees Fahrenheit, others Celsius, and some represent nonnumeric/string data. Therefore, do not select the first `B010` formula or enable this reading until the actual PCM identity and matching response are known. A manual transmission or different engine needs a separate profile decision.

The stock 2007 3.8 L oil-pressure signal should not be advertised as a numeric OBD pressure reading without confirming the fitted sensor and a real numeric ECU response. An external pressure transducer is the route to a measured numeric value if the vehicle only exposes a switch state.

## Fault codes and module diagnostics

Standard emissions-related MIL and confirmed, pending, and permanent DTC paths are separate from enhanced ABS, transmission, airbag, and body-module diagnostics. Static analysis found approximately 4,332 DTC definition resources in the broader catalog. Code descriptions and decoding rules vary by module and version. An ABS fault from the ABS module must not be shown as a PCM emissions fault. eGauge's current headerless, single-responder diagnostic path does not establish JK module attribution.

## Vehicle actions and adaptations

Static analysis found approximately 5,697 activation resources and 4,324 write resources across the broader catalog. These are distinct from live-data definitions and cannot be enabled through a custom PID formula. One JK-specific adaptation file contains axle ratio, tire size, petrol high idle, a beta 2.8 L diesel high-idle variant, and a test routine. The vehicle profile also references 20 shared files covering module presence, user settings, TPMS, axle and tire settings, lighting, vehicle maintenance, theft alarm, radio, and other configuration. These files can contain entries intended for other vehicles and are not an executable JK command list.

| Action | What is known | What is not established |
| --- | --- | --- |
| Petrol high idle | The JK entry uses a dedicated KWP handler targeting the engine controller on the `7E0` request and `7E8` response route. It describes a momentary increase with the engine running. The code maintains the operation and has a stop path when leaving the screen. | Applicable PCM software, permitted RPM range, exact state checks, behavior after link loss, and physical return to normal idle on this Jeep. |
| 2.8 L diesel high idle | A distinct beta entry and handler exist. | Applicability to a particular market/year/ECU and physical behavior. |
| ABS and traction-control shutoff | A shared axle-and-tire settings file referenced by the JK profile contains an ABS/ESC shutoff entry. Dedicated implementations also exist, including more than one module-specific path. | Which path, if any, applies to the target JK; exact command sequence; how the vehicle reports status; and the conditions and timing for restoring normal operation. |
| Axle ratio and tire size | JK-specific adaptation definitions exist. | Compatibility with the target modules, value encoding, post-change verification, and rollback behavior. |

High idle is an active ECU operation, not a PID read. An ABS/traction-control shutoff affects braking and stability behavior and is restricted to off-road validation. Do not present either action as supported by eGauge today. A future action implementation needs an allowlisted vehicle and ECU version, preconditions, explicit owner initiation, a bounded lifetime, cancel and disconnect handling, positive status/readback, and physical verification of restoration. Keep vehicle writes out of the general PID editor.

## Catalog and command coverage

The local [catalog research workflow](../catalog-research.md) indexes every bundled vehicle-data resource, including diagnostic routes, reads, DTCs, activations, writes, and vehicle adaptation definitions. It preserves each original record in a Git-ignored database and creates searchable fields for service/mode, PID, module, fault code, and command. A [JK definition report](../../../data/vehicle-definitions/vehicles/jeep/wrangler-jk.json) captures 17 candidate module routes and 332 top-level entries from the 21 referenced adaptation files. Some entries are shared or are group headings. The broad catalog is an inventory for investigation, not a list of commands approved for this Jeep.

For the JK, research should proceed through these joins: JK vehicle profile to its 11-bit CAN module index; module and variant identification to the referenced live-data, activation, and DTC files; then the specific action handler or adaptation entry. Each candidate requires a raw response or state observation from a matching ECU before promotion. In particular, a write or activation entry alone does not define a safe complete procedure, including diagnostic session, security access, timing, tester-present traffic, stop path, and readback.

## Static package analysis

The analyzed diagnostic application package contains 29,967 embedded vehicle-data resources. Its vehicle definitions, module indices, live-data files, activation files, write files, and DTC files are separate. Individual files are Base64 text containing raw-DEFLATE-compressed JSON; they are not encrypted. The package also includes 11-bit and 29-bit CAN handling, other diagnostic protocol handlers, BLE adapter communication, response parsing, and simulated vehicle transactions.

This establishes that many mappings can be decoded for research. It does not establish that every decoded item is valid for a 2007 JK. The simulated transactions are not physical vehicle observations. A compressed bulk definition archive is versioned with provenance and the repository owner's statement of redistribution permission. None of its records is enabled in eGauge.

## eGauge implementation gap

The [current firmware state](../../current-state.md) has a small built-in Mode 01 catalog and a restricted numeric configuration path. Live adapter requests, ECU attribution, header-bearing and multi-frame responses, Mode 22, and dual-adapter operation still need vehicle validation or implementation. The [PID definition model](../../protocol/pid-discovery.md) already provides a bounded place for curated read definitions, but it deliberately excludes arbitrary commands. Vehicle actions remain outside [v1 scope](../../requirements.md).

Before promoting a JK reading or action:

1. Record the vehicle year, market, engine, transmission, adapter model, ECU/module identity, software or part number, and diagnostic route.
2. For a read, capture the raw request and response, verify length and responder, calculate the value independently, compare it with another trustworthy observation, and test unavailable/stale behavior.
3. For an action, first establish an authorized and version-specific procedure, then verify parked-state preconditions, activation, timeout, disconnect/cancel behavior, status reporting, and restoration on the physical vehicle.
4. Add regression fixtures and an explicit compatibility entry only for the verified variant. Leave capability flags disabled until the complete path has passed.

## Open questions for the target Jeep

- What engine, transmission, market, and PCM/TCM software versions are installed?
- Which standard PIDs are advertised and which modules answer with the selected BLE adapter?
- Does the PCM answer the proposed transmission-temperature request, and which of the known decoder variants fits its actual response?
- Which enhanced modules can the adapter address reliably, including ABS and the cluster?
- How do the owner's high-idle and ABS/traction-control functions behave on this exact vehicle, including automatic restoration?
