# First Jeep capture results

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

Date: 2026-10-06, America/Denver. Vehicle and swap description are owner-reported: 2010 Wrangler, 5.7 L Hemi, ZFHP70 transmission. One Vgate was moved sequentially between ports. No vehicle configuration or diagnostic-clear commands were sent.

## Adapter identification

Two devices were visible to the Mac. IOS-Vlink disappeared when the owner unplugged the physical Vgate, then returned when reconnected. The other device was not selected. Actual IOS-Vlink GATT discovery exposes service 18F0, TX 2AF1 with both write properties, and RX 2AF0 with notify/indicate plus CCCD. The adapter reports ELM327 v2.3; this string does not verify its implementation version or authenticity.

## Engine port, Mac BLE

154 complete transactions, zero timeouts, zero incomplete queries. Protocol response: AUTO, ISO 15765-4 (CAN 11/500), A6. Recognized support maps came from responder 7E8 for ranges 00, 20, and 40. Most samples deliberately retained CAN headers, which the current firmware parser does not accept. Final headerless samples passed the production parser: RPM payload 0B3C gives 719 RPM; coolant payload 58 gives 48 degrees Celsius (118.4 degrees Fahrenheit). These are adapter observations at that time, not physical LCD readings or comparison-instrument verification.

## Transmission port, Mac BLE

11 complete transactions, zero timeouts. Same adapter/protocol, distinct responder 7E9. Support responses: 410098180001, 412080018001, 414040800000. This establishes communication and advertised standard support on the owner-identified transmission port. It does not establish controller model, transmission-fluid temperature, manufacturer requests, or the meaning of any future enhanced reading. No enhanced probes were sent.

## Gauge and Pixel

Capture firmware 0.2.0-dev.33cap connected to the adapter service and discovered matching characteristic properties and CCCD. Eight first-command ATE0 attempts timed out; no adapter RX trace records arrived and no OBD transaction completed. The Pixel UI said Gauge ready, establishing companion connectivity only. Actual live LCD readings were not established. A separate Mac comparison received OK prompts for ATE0 within one second with both acknowledged and unacknowledged writes. That comparison does not resolve the firmware failure or prove a subscription defect.

## Evidence and next work

Private recordings, GATT maps, support maps, raw gauge serial output, parser replay summaries, and observation notes are stored under ignored artifacts/vehicle-captures. Recordings remain local because they contain adapter identifiers. The source labels are engine and transmission; captures are sequential and do not qualify simultaneous support. Public capability flags remain unchanged.

1. Reproduce the firmware initialization state machine offline from saved timing and connection generations. Investigate subscription activation/readback, actual ATT write completion, event routing, and initialization timing. Preserve fail-closed transaction boundaries.
2. Add coverage for the identified defect before deploying a correction. Use one bounded physical retest for successful setup, RPM/coolant freshness, adapter loss/recovery, and phone coexistence.
3. Identify the installed transmission controller and obtain documented enhanced requests before implementing its readings. Retain the observed 7E9 support maps as evidence, not a guessed controller definition.
4. Repeat simultaneous adapter qualification after the second physical adapter is purchased.
