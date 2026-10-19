# Wrangler JK action-handler inventory

Status: static analysis of the managed diagnostic handlers, recorded on 2026-09-29. The complete [method inventory](../../../data/vehicle-definitions/vehicles/jeep/wrangler-jk-handler-inventory.json) preserves method names, unique called methods, string literals, and instruction counts for five relevant action classes. Its assembly SHA-256 matches the source-package input in the [archive manifest](../../../data/vehicle-definitions/manifest.json). These are code observations, not vehicle observations or complete wire procedures.

| Candidate action path | Static findings | Validation still required |
| --- | --- | --- |
| Petrol high idle | The JK adaptation selects `set_rpm_pb_kwp` at `7E0`/`7E8`. Its handler contains `31 05 {0} {1}` and `33 05` literals. It starts a recurring work task; navigation away calls `StopAdaptation`. | Determine the exact byte encoding, response checks, refresh interval, stop traffic, and physical return to normal idle for the target PCM. |
| 2.8 L diesel high idle | A separate beta handler uses the same `7E0`/`7E8` route. Its literals include `30 10 07 {0} {1}` and a recurring `30 10 01`. | Confirm market and ECU applicability, value encoding, stop behavior, and physical response. |
| ABS/ESC shutoff through the front controller | The shared settings file referenced by JK includes an `ABS + ESC Kill` entry addressed to `620`/`504`. A matching candidate handler has disable and enable options and contains `31 A4` and `32 A4` literals. | Confirm that this handler is the one selected for the target JK, its preconditions, reply interpretation, persistence, and restoration. |
| Another ABS/traction-control handler | A distinct handler uses `747`/`4C7` and offers one-key-cycle, five-key-cycle, and restore choices. Its literals include `31 01 30 46 01`, `31 01 30 46 05`, and `31 02 30 46 00`. | Establish vehicle applicability. This path must not be assumed to apply to the JK merely because it exists in the package. |
| Z test routine | The JK adaptation entry is inactive. A handler exists and describes routine steps. | No claim of JK support or safe operation follows from the inactive entry. |

The strings above identify command fragments in executable methods. Branch conditions, adapter setup, diagnostic session, security access, timing, error handling, and readback can change the actual sequence. No action is enabled in eGauge. The [JK research notes](wrangler-jk.md) define the physical validation needed before implementation.

## Primary vendor cross-check, 2026-10-07

The [JScan FAQ](https://jscan.net/faq/) describes raised idle as a temporary
adaptation that returns to normal when the app disconnects or leaves the adaptation
screen. This supports modelling high idle as an active operation with Stop and
verified refresh/loss-of-contact behavior, rather than a persistent RPM preference.
The FAQ does not provide encoding, refresh timing, a stop packet or a compatibility
identity for this Jeep's swapped PCM. It does not establish eGauge support.

The [official JK feature page](https://jscan.net/jeep-wrangler-jk/) lists module
identification (VIN, part number and version) and vehicle-specific actuator testing.
Its general JK coverage is not evidence that the swapped Hemi PCM matches the
petrol handler above, nor a complete ABS/ESC disable/restore procedure. Next controller
qualification needs exact identities and complete command/result/restore definitions;
retain these as research candidates until that evidence is available.

## Temporary-idle follow-up, 2026-10-19

The versioned inventory places `33 05` in the constructor, initial adaptation and
recurring work method. `StopAdaptation` delegates to an unexpanded base method;
navigation-away delegates to that Stop method. The inventory alone therefore does
not identify the actual release transaction or prove that ceasing `33 05` restores
idle. The exposed RPM labels include 800 through 2000, but they are UI choices in
the reference implementation, not validated limits or a decoder for our PCM.
The adaptation's `22 0122` field is not a complete execution procedure.

The captured engine calibration `68059434AD` and CVN `CA36378C` at `7E8` are the
first [qualification target](../../evidence/jeep-direct-exploration-results.md#placement-correction-and-identity).
No new vehicle requests were made for this follow-up. The implementation and test
sequence belongs in [ADR-013's temporary-idle proposal](../../architecture/vehicle-actions.md#first-vehicle-command-temporary-high-idle);
do not duplicate a command procedure here or infer compatibility from the JK name.

### Owner reference confirmation, 2026-10-19

The owner confirmed that raised idle **works in JScan on the Hemi-swapped Jeep**.
This establishes an owner-reported working reference on the target vehicle, rather
than only a stock-JK feature claim. The JScan test date/version, selected RPM,
controller identity during that test, command trace and release/timeout observations
were not supplied. Do not infer those details or identify the static handler as the
working path from this confirmation alone.

The next comparison should reproduce the known-working JScan operation and obtain
its complete procedure for the current PCM. eGauge high idle, Stop, expiry and
loss-of-contact restoration remain unimplemented and unqualified.

## Detailed offline control-flow review, 2026-10-19

Re-extracted the original local split package with the existing research tool.
`obd4u.cleversoftware.net.Common.dll` matches manifest SHA-256
`aa3b31566a772781149a0c4b65d12e3d5180da6ebba866829739237bc30289df`.
The inspector's new optional `--instructions --include-base` output retains numeric
constants, offsets and base methods for reproducible local review. Default inventory
output is unchanged; extracted assemblies and instruction reports remain private.

| Finding | Static evidence and limit |
| --- | --- |
| RPM encoding | Selected numeric RPM is divided by 256 and the remainder formed. The formatted start is `31 05 HH LL`; 1200 produces `31 05 04 B0`. This identifies encoding, not acceptance by our PCM. |
| Setup order | The handler selects `7E0`/`7E8`, requests protocol service 9, then 4, then sends `33 05` before the RPM request. In `ProtocolKWPCANServices.GetKeyword`, 9 selects TesterPresentRequest and 4 selects EnterExtendedDiagnosticSessionRequest. The actual protocol selection must still match the vehicle. |
| Adapter-facing keywords | The KWP/CAN mapping contains `3E 01 1` and `10 92 1`. These are strings passed to the transport; do not reinterpret their trailing tokens as ECU payload bytes without checking adapter framing. |
| Refresh | The recurring method sends `33 05`, requests protocol service 9, then awaits a 2000 ms delay. That delay follows IO and is not a guaranteed two-second wire interval. Calls use a 1200 ms wait argument; that is not the ECU's idle-release timeout. |
| Stop | The derived Stop calls a base Stop that immediately returns, then clears `_keepWorking`. It does not transmit a separate release packet in the reviewed path. An in-flight iteration may finish before the flag is checked. |
| Result handling | The reviewed start/refresh callers discard returned values before showing Started. Do not copy that as evidence of positive ECU acknowledgment; eGauge must verify its own result and release behavior. |

Reproduce the detailed report after extraction, using the already declared research
Python dependencies:

```sh
python tools/research/inspect_action_handlers.py /path/to/obd4u.cleversoftware.net.Common.dll \
  --type RpmPbKWPAdaptationViewModel --type ProtocolKWPCANServices \
  --type VehicleCommunicationService --instructions --include-base \
  --output artifacts/high-idle-handler-review.json
```

Remaining vehicle evidence: confirm the current controller/protocol and matching
JScan path, record accepted replies, measure actual RPM change and normal Stop,
and bound release after the last refresh. Start with the known-working JScan
operation. A reference capture needs JScan logging or an authorized phone Bluetooth
trace; a second BLE client cannot share the adapter as an independent recorder.
Do not induce adapter/gauge loss before ordinary release behavior is established.
No actual vehicle packets were sent for this analysis.
