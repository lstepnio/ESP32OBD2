# Jeep action reference follow-up, 2026-10-19

> Historical evidence. These are parked JScan reference observations, not enabled
> eGauge commands. [Current state](../current-state.md) owns implementation status;
> [FEATURE-04](../backlog.md) owns remaining integration and qualification.

## Scope and identity

The owner confirmed Park, parking brake set, engine idling, ECM adapter connected,
TCM adapter unplugged and gauge off. JScan on the Pixel reported version
`v21.09.2026.12:45`, code 1503. No eGauge firmware or App was installed or changed.
No driving, compressor, winch load or braking-performance test was performed.

JScan's Powertrain Control information reported part/software `68059434AD`,
software version `140400`, variant `08`, diagnostic version `75`, hardware `154F`.
Subsequent Mac Mode 09 reads from `7E8` confirmed calibration `68059434AD` and CVN
`CA36378C`. This matches the earlier target; it is not a universal JK definition.
Raw identifiers, exports, screenshots and Mac recordings remain private under
ignored `artifacts/jeep-idle-qualification/`.

## Raised idle

The second Trail View > More button toggled immediately with no RPM picker.
The inspected Trail View settings offered no RPM target. The owner also reported
that the target is not selectable. This establishes the observed control surface,
not the absence of another target-setting path elsewhere in JScan.

- Baseline phone screenshots showed 672..698 RPM. Start showed 1,746 RPM; ordinary
  Stop showed 709 RPM approximately six seconds after input. The owner confirmed
  the physical engine raised idle and returned to normal.
- HCI captured `10 92 1` / `7E8 02 50 92`, fixed Start `31 05 06 80 1` /
  `7E8 02 71 05`, and ordinary Return `32 05 1` / `7E8 02 72 05`.
- On a later Mac connection after Stop, `33 05 1` returned `7E8 03 7F 33 80`.
  After the captured diagnostic session entry `10 92 1`, the same read returned
  `7E8 03 73 05 01` twice, with RPM 688 then 693. Session setup therefore matters.
  This is a delayed read after reopening the session, not qualification of the
  candidate driver's immediate same-session post-Return sequence.
- A second known JScan Start showed 1,758 RPM. JScan was force-stopped and phone
  Bluetooth disabled, with no deliberate Return sent. The owner reported normal
  idle returned within the ten-second observation window. A subsequent Mac read
  showed 695 RPM before session entry, then 682 RPM and two inactive reports 01.
  Physical timing was not instrumented; this is one combined process/link-loss
  observation, not a measured ECU lease deadline or every interruption scenario.

The fixed word `06 80` still has no qualified arbitrary-RPM mapping/range. An
Air up or Winch preference of 1,200 RPM must not silently execute this ~1,700 RPM
reference. eGauge's driver remains unregistered and no gesture sends controls.

## Combined ABS and traction-control button

The owner requested a separate reference capture while still parked. The first
Trail View > More button turned orange; the owner observed both ABS and
traction/stability dash warnings. Its exact complete transactions were:

| Operation | Adapter request | Complete reply |
| --- | --- | --- |
| Route | `AT SH 620`, `AT FC SH 620`, `AT CRA 504` | Each `OK` |
| Session | `10 92 1` | `504 02 50 92` |
| Off | `31 A4` | `504 02 71 A4` |
| Return | `32 A4` | `504 02 72 A4` |

After Return the JScan button was green, but the owner reported one or both dash
warnings remained. After ignition fully off for ten seconds and a parked restart,
the owner confirmed both cleared. A positive Return reply and green App icon do
not establish physical restoration. No direct brake/traction performance or
controller-state readback was tested.

The [JScan manual](https://jscan-docs.readthedocs.io/en/latest/general/user_manual.html#bottom-row-icons)
describes the first button as combined ABS+TC Kill until the next ignition cycle.
Do not present these reference bytes as separate ABS-only and traction-only APIs.
The documented ignition-cycle behavior matches this session; persistent warning
lights after the green Return button were not, by themselves, evidence of failure.

## Fourth button

The owner identified the fourth button as ABS-only off with traction retained and
also authorized capturing it as Line Lock. The manual calls that icon Burnout /
Line Lock; the installed Trail View setting showed `Lock front`. Static extracted
handler `AddABSBurnoutMode` describes rear outlet valves and a distinct ABS route.
These sources do not resolve the actual behavior on this swapped Jeep.

The owner confirmed operating the fourth button on and off after the first
ignition cycle. HCI records the ABS route `AT SH 784`, `AT FC SH 784`,
`AT CRA 785`, followed by session `10 92 1` / `785 02 50 92`:

| Operation | Adapter request | Complete reply |
| --- | --- | --- |
| Owner Start | `30 11 07 A0 00` | `785 03 70 11 07` |
| Owner Return | `30 11 07 00 00` | `785 03 70 11 07` |
| Later agent Start attempt | `30 11 07 A0 00` | `785 03 7F 30 80` |

For the later attempt the owner confirmed Park, parking brake, idling and the
brake pedal firmly held. JScan showed **Operation fail, try one more time** and
remained green. No retry was made. The trace contains no fresh session entry
between that attempt's ABS route setup and control request. The earlier positive
sequence did enter session 1092; this is a candidate explanation for the negative
reply, not proof that session entry is the only precondition. After another
ignition cycle the owner confirmed both warning lights off.

The owner later confirmed the dash behaved as expected with the buttons. The
manual does not specify a separate ignition-cycle restoration rule for the fourth
button, so do not extend the combined-shutdown rule to it without further evidence.

The positive replies establish accepted reference requests, not measured hydraulic
effects, independent traction retention or a qualified ABS-only API. No wheels
were moved or brake-release/holding performance tested. Keep this operation
separate from the `620`/`504` combined shutdown and the `7E0`/`7E8` idle driver.

## Export integrity and cleanup

The first HCI export contains 11,780 complete records with no recorded drops,
SHA-256 `0dafbe8f9881d1f38351d6d62644e203952a619fccdfac632dcb8e2ae077d8c3`.
The final current HCI log contains 18,863 complete records, 823,056 bytes, with no
recorded drops, SHA-256
`71acddf17da8519415b3488d3d5ded60fc5c3a0b47e9cdc4c7b9b750adfa0c90`.
Its previous-log companion preserves the earlier idle window. Record boundaries
and complete file consumption were checked. ATT fragments were reassembled per
connection/direction, and only complete prompt-delimited replies support the
transaction tables. This is phone/adapter traffic, not an independent CAN capture.

Android export requests take several minutes and are not instantaneous snapshots;
record timestamps, rather than request times, define included windows. Exports
and per-file hash/count/timestamp manifests are retained privately. Bluetooth HCI
logging was restored to Disabled, Bluetooth cycled off/on, JScan stopped, and the
phone's original timeout/charging-wake settings restored. The logging socket was
never enabled. No DTC clear, bond erase or persistent vehicle coding was performed.

## Next implementation boundary

Use the matched idle driver behind the existing primary-worker transaction owner,
protected command dispatch and shared operation history. Qualify immediate
post-Return status, runtime stationary/gear guards, cancellation and independent
TCM continuity before exposing a physical shortcut. Preserve the fixed captured
setting separately from unqualified numeric targets. The owner subsequently
selected idle and combined ABS/TCS as the active delivery scope and deferred the
fourth-button Line Lock function. Preserve that capture for later; no further
fourth-button implementation or vehicle testing is in the current scope.
