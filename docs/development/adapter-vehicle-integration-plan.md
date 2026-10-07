# Adapter and vehicle integration plan

Status: proposed, 2026-10-06. This plan is based on repository code, documents, and the owner's hardware description, not a live OBD-II session. The described adapter variant and vehicle need physical identification and measurement.

## First test configuration

| Part | Owner-reported or researched starting point | Still to observe |
| --- | --- | --- |
| Vehicle | 2010 Jeep Wrangler chassis with a custom 5.7 L Hemi swap and ZFHP70 transmission | Market, installed engine and transmission controllers, software IDs, diagnostic routes, and actual responder behavior. A stock 2010 Wrangler profile is not sufficient. |
| Current adapter | One [Vgate iCar Pro BLE 4.0, ASIN B06XGB4873](https://www.amazon.com/dp/B06XGB4873), to be moved between the engine and transmission diagnostic ports | Physical label/revision, advertisement, GATT map, ELM behavior, and successful requests on each port. The linked listing selects the BLE 4.0 variant, but the actual unit still needs to be checked. |
| Future second adapter | To be purchased for simultaneous engine and transmission monitoring | Make/model, transport, independent identity, and three-link coexistence with the phone. No simultaneous dual-adapter claim is possible yet. |

The [Vgate product page](https://vgatemall.com/products-detail/i-9/) describes the BLE model and automatic sleep/wake behavior. Treat those as vendor claims until observed on this unit. Record separate engine-port and transmission-port sessions even though the same adapter is used, and do not assume that the transmission controller exposes standard engine Mode 01 readings.

## Goal and boundary

Use short, repeatable vehicle sessions to capture real adapter and ECU behavior. Develop and regress most firmware and Android work on a bench from sanitized recordings. Support multiple adapter models and multiple vehicle profiles without assuming that one adapter equals one ECU or that a vehicle always uses the same adapter. The first vehicle has separate engine and transmission diagnostic ports. Initially, one adapter is moved between them; continuous concurrent monitoring requires a second adapter later.

The gauge owns adapter connections and live polling. Android owns setup, local editing, evidence review, and updates. One adapter is the normal setup. A second adapter is an advanced, per-vehicle option after three-link operation is measured. No public capability flag may imply a complete path before physical verification.

## Current baseline

- Firmware has two independent BLE central contexts, but the ECM connection uses a build-time MAC or first-compatible discovery. The TCM connection requires a build-time MAC and currently does not poll readings. One adapter and two adapters have not been qualified against an actual vehicle in the repository evidence.
- Android vehicle profiles are local drafts. They do not persist adapter identity or observed vehicle capability. The second-adapter preference is not a live binding.
- The existing discovery design distinguishes advertised support from a valid response and stores evidence per ECU. The code still needs actual adapter/vehicle traces and end-to-end qualification.
- The current transport expects a specific BLE UART GATT profile. Adapter names and ELM version banners are insufficient to claim compatibility. The first capture must record the actual GATT map and command behavior.

## Architecture to implement

```text
Android vehicle profile
  -> source bindings: primary, optional second
  -> gauge adapter registry and connection manager
  -> adapter transport driver (observed GATT profile)
  -> serialized ELM session per source
  -> ECU responder and PID definition
  -> fresh reading, page, and alert

Recorded transport events -> deterministic replay -> same parser and state logic
```

Keep four identities separate:

| Identity | Purpose |
| --- | --- |
| Vehicle profile ID | Selects pages, alerts, definitions, and source bindings. Does not itself prove a physical vehicle identity. |
| Adapter ID | Names a physical adapter and its transport profile. Binding credentials remain on the gauge. A BLE address alone may be insufficient as a durable product identity. |
| Source ID | Stable role within a vehicle profile, such as `primary` or `transmission`. A source selects one adapter connection. |
| ECU identity and route | Distinguishes responders behind an adapter. Every reading also carries service, identifier, and definition revision. |

Switching vehicles must invalidate current live readings and evidence until the newly selected profile's adapters and responders are confirmed. A user may move one adapter between vehicles, but old vehicle evidence remains historical and cannot become a live claim for the new vehicle.

For the first Jeep sessions, the same adapter may be used to collect evidence for each port in turn. Only the physically connected port can be active. Changing ports starts a new source session and immediately invalidates the other source's live values and alerts. Do not configure the same adapter identity as two simultaneous live sources. After a second physical adapter is available, assign distinct adapter identities to the two source bindings.

### Contracts and persistence

Add a versioned, owner-authenticated app-to-gauge contract for adapter discovery, binding, unbinding, connection status, observed adapter profile, ECU support evidence, and bounded diagnostic capture. Stage and commit bindings with readback and rollback, like the existing configuration path. Store the gauge's adapter bindings durably, including vehicle profile and source association. Keep the app's profile record and gauge's active profile revision in sync; surface a mismatch rather than silently applying the wrong pages or alerts.

Represent observations with source ID, ECU/route, request, raw response, adapter/session generation, monotonic timestamp, response latency, decoder revision, and evidence state (`unknown`, `advertised`, `responding`, `no_response`, `unsupported`, `decode_error`, `unavailable`). Mark recordings as captured, simulated, or source-derived. Never promote a simulated response to vehicle evidence.

Keep transport drivers separate from the ELM parser. Qualify the existing GATT service and characteristics on the actual adapter first. Add another driver only after its radio and GATT behavior is recorded. BLE, Bluetooth Classic, and Wi-Fi adapters are distinct transport work; their shared ELM text protocol does not make them interchangeable.

## Short vehicle sessions

### Session A: sequential capture from both diagnostic ports, proposed 45 to 60 minutes

Prepare a build with bounded, locally stored diagnostic tracing before connecting to the vehicle. Bring the gauge, Pixel, Vgate adapter, USB power/cable, and an independent scan tool if available. Record exact app, firmware, board, phone, adapter, and swapped vehicle variants in a private session manifest. Keep VIN and other identifiers out of Git fixtures. Label each capture by physical port before moving the adapter.

1. With ignition off and then on, inspect the Vgate on the engine port. Record advertisement, connection/disconnection, GATT service and characteristic UUIDs/properties, subscription result, adapter identity/firmware response, and timing. Note the physical diagnostic connector and responders.
2. On the engine path, record initialization commands and complete ELM transactions through each prompt, including the individual BLE notification chunks and monotonic timestamps. Capture headers and all ECU responders.
3. With engine running, read a bounded standard engine set: supported Mode 01 PID ranges and common values such as RPM, speed, coolant temperature, and load where advertised. Compare RPM and coolant with an independent scan tool or vehicle display where possible. Record protocol, sample interval, latency, and errors.
4. Disconnect cleanly, move the same Vgate to the transmission port, and start a new capture session with a new source/port label. Record its adapter response and transmission controller identity. Attempt a read-only, documented query only after its route and response format are known. Capture the raw result and timing. Do not use stock Wrangler transmission-temperature definitions as if they applied to the ZFHP70 swap.
5. On each port, observe engine off, adapter unplug/replug, gauge reconnect, and value aging. Keep the Pixel connected during at least one single-adapter poll. Check whether the Vgate unit actually sleeps and wakes as claimed. No DTC clearing, security access, coding, or unbounded PID scans.
6. Export separate sanitized captures for engine and transmission plus a manifest before leaving. Check that each contains the physical port, adapter identity, and connection trace. The engine capture must contain at least one complete successful transaction; the transmission capture must state whether a safe read was possible. If a fragmentation or failure case does not occur naturally, synthesize it later and label it simulated.

Session A exit: both physical ports are captured separately with the same adapter, and at least the engine path has an actual GATT map, successful read-only transaction, ECU/source attribution, timing baseline, and replayable trace. The transmission path has a clear result: observed read, unavailable controller, or unresolved route. Preserve negative results instead of adapting the parser to imagined behavior. This session cannot establish concurrent operation.

### Offline development, no vehicle required

- Build a transport recorder/replayer that feeds the production assembler and parser with captured notification chunks and a controlled clock. Store private raw captures separately from small, sanitized Git fixtures. Preserve timing and request/response boundaries in both.
- Build a deterministic BLE adapter simulator or bench bridge for complete gauge integration. Cover echo on/off, prompt splitting, multiple responders, delayed replies, `NO DATA`, malformed frames, queue overflow, disconnect during a transaction, and late bytes from an old connection generation.
- Use a separately powered adapter on the bench, if its hardware permits it, to test radio discovery, GATT, reconnection, and ELM initialization. A bench adapter without a vehicle does not prove vehicle PID behavior.
- Implement the durable source registry, profile migration, owner-controlled binding workflow, per-source status, and reading provenance. Make Android show live, stale, historical, and example states plainly. Keep technical capture tools in Expert.
- Add golden decoder vectors from captured standard responses, parser and state tests under the existing host sanitizers, Android repository/UI state tests, and configuration interruption tests.

Offline exit: captured data replays deterministically through production logic; a simulated source can exercise setup and stale/reconnect states without being displayed as a live vehicle.

### Session B: fixed vertical slice, proposed 20 to 30 minutes

Install the integrated build. In Android, select the Jeep profile, find and bind the Vgate to the engine source, run a bounded discovery, choose one responding reading, send a page to the gauge, and confirm the physical gauge displays that live value. Reboot the gauge and app, then verify binding and page persistence. Unplug the adapter, verify stale/unavailable state, and reconnect. Compare the live value to a second instrument again. Repeat the source-selection and stale-state checks after moving the adapter to the transmission port if a documented transmission read was established in Session A; the engine value must no longer appear live.

Session B exit: one adapter-backed reading moves from vehicle to gauge display through a saved profile without a compile-time MAC. Record physical gauge observations and Pixel observations separately from logs and replay tests.

### Session C: qualify the two-adapter Jeep after the second adapter arrives, then broaden the matrix

1. Identify and qualify the second adapter separately, then bind the Jeep's engine and transmission ports as independent sources. If its transport differs, add and qualify its driver with its own fixtures. Do not label an untested model compatible because it advertises a familiar name.
2. Measure both adapter links plus the phone during display and alert activity, independent disconnects, response attribution, freshness, memory, and reconnect. Use the existing `MULTI-001` two-hour combined-load gate before advertising simultaneous support. If it fails, choose and describe an explicit degraded mode rather than silently dropping a source.
3. Repeat with a second vehicle using one of the same adapters where possible. Verify profile isolation and that selecting the new vehicle cannot show readings, support evidence, pages, or alerts as if they came from the Jeep.
4. Qualify another adapter model and vehicle variant beyond these first combinations. Each needs its own recorded compatibility evidence; shared ELM commands alone are insufficient.

## Acceptance matrix

| Area | Minimum evidence before claiming support |
| --- | --- |
| Adapter model | Exact hardware/firmware and GATT profile; connect, ELM initialization, read-only response, disconnect/reconnect; recorded error behavior. |
| Vehicle profile | Exact year, market, engine, transmission, protocol, and ECU responders; observed standard readings with raw fixture and decoder revision. |
| Android to firmware | Bind, readback, reboot persistence, vehicle switching, interrupted write recovery, and honest state when unavailable. |
| Reading safety | Source and ECU attribution, bounded parser, no old value shown as live, no cross-profile carryover, alerts only on fresh valid data. |
| Two adapters | Two adapter links plus phone measured on the physical gauge, independent failure handling, achieved sampling and alert freshness, combined-load gate. |

Maintain a compatibility table keyed by tested adapter revision and vehicle variant. `Supported`, `partially supported`, and `untested` must reflect actual recorded sessions. The offline simulator qualifies code paths, not vehicle compatibility.

## Decisions to make after the first capture

- The physical revision of the current Vgate, the future second-adapter model, and whether either can be powered safely on a bench.
- Whether the first adapter matches the current BLE GATT driver or needs another driver.
- The transmission controller's protocol and diagnostic route, and whether the second adapter can be sampled concurrently with the engine adapter on this build.
- Actual latency and radio coexistence budgets, which determine polling and freshness settings. Do not select a source-switching interval before measuring connection and initialization time.
- The private capture storage location and redaction policy for VIN, adapter addresses, and any owner data.

Related designs: [multi-adapter architecture](../architecture/multi-adapter.md), [PID discovery](../protocol/pid-discovery.md), [capture runbook](vehicle-capture-runbook.md), and [quality gates](quality.md).

## Implementation progress after Session A

The sequential engine and transmission recordings are complete; see [first capture results](first-jeep-capture-results.md). Development now includes a primary adapter selection per Android vehicle profile, owner-only gauge discovery/status, schema 2 binding persistence in the existing configuration transaction, explicit ECU response parsing, and a trace-only Mac BLE simulator using sanitized captured vectors. See [binding contract](../protocol/adapter-bindings-v1.md).

The first vertical slice executes one ECM source. The transmission's captured support maps remain evidence of a different responder, not an enhanced transmission-temperature definition. Multiple vehicles can keep independent primary adapter selections; simultaneous dual-adapter polling and additional physical transport models remain qualification work. Offline parser and simulator results do not close the live Vgate setup failure. Session B still requires physical adapter setup, one real reading, persistence, unplug/reconnect, and independent comparison.

The global security floor was identified as a cause of discarded unencrypted adapter replies, and the corrected image passes physical gauge transport against the simulated adapter, including disconnect/reconnect. See [offline integration evidence](offline-integration-results.md). Real Vgate qualification still requires Session B.
