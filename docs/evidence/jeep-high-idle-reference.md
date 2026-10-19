# Jeep high-idle JScan reference, 2026-10-19

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

## Setup and scope

The owner confirmed the Jeep parked in Park, parking brake set, engine idling,
ECM adapter connected and gauge powered off. The initial JScan connection selected
the TCM; the owner temporarily unplugged that adapter and JScan was reconnected
through its BLE adapter chooser. No idle-control request was issued on the TCM
connection. JScan subsequently displayed live engine RPM and zero vehicle speed.

Pixel JScan package `net.clever.obd4u` reported version `v21.09.2026.12:45`, code
1503. The working control was **Trail View, second bottom button**, identified by
the owner. That button immediately toggles control; it does not open an RPM picker.
No compressor or winch was operated. These are unloaded reference tests, not
qualification of eGauge commands, arbitrary RPM choices or either equipment load.

## Observed start and ordinary Stop

- First cycle: JScan showed 697 RPM beforehand and 1708 RPM after activating the
  second Trail View button. After switching it off, screenshots showed 903 RPM at
  approximately 3.3 seconds and 706 RPM at approximately 8.9 seconds after the
  recorded Stop request. The owner separately confirmed that the physical engine
  raised idle and returned to normal.
- Second cycle: baseline screenshot showed 686 RPM. Start input was requested at
  21:02:42.033 UTC; an active screenshot at 21:02:46.945 showed 1714 RPM. Stop input
  was requested at 21:02:52.951. Following screenshots showed 1704, 491, 760, 723 and
  approximately 700 RPM as control returned. In particular, the transient 491 RPM
  observation means one low sample must not be treated as stable restoration.
- Screenshot completion timestamps include input, display refresh, transport and
  image-capture latency. They are sampled phone observations, not exact ECU
  execution or physical release times. No independent tachometer timing/video was
  collected. No fault-code clearing or deliberate adapter-loss test was performed.

## Trace collection

Android Bluetooth HCI snoop logging was initially Disabled and was temporarily set
to Enabled, followed by a Bluetooth off/on cycle before the ECM test. The first
export verified a valid HCI log, covering 20:57:15.105 through 21:00:38.601 UTC;
that snapshot ends before idle activation and is **not** the idle-control trace.
A second export was requested after both Start/Stop cycles. It finished on the
phone, but USB disconnected before copying it to the Mac. Retrieval/decoding of
that saved export remains pending. No packet-level result is claimed from the
screenshots or the earlier preflight file.

Raw Android exports, HCI traffic, VIN/device identity, screenshots and event times
are private ignored artifacts under `artifacts/jeep-high-idle-live/`. They must not
be committed or attached to a public PR. Only scoped, sanitized request/reply
vectors and a summary belong in source control.

## Matching static path and remaining work

The original archived assembly has a distinct Trail View JK path in
`TrailViewPageDashboardViewModel.<PrepareLiveDataForJK>d__118`. Its Engine Rpm
Switch uses `InputOutputControlSwitchDataInfo` with these adapter-facing strings:

| Role | Static Trail View JK string |
| --- | --- |
| Short-term adjustment | `31 05 06 80 1` |
| Report current state | `33 05 1` |
| Return control to ECU | `32 05 1` |
| Request / response route | `7E0` / `7E8` |

This is **different** from the earlier adaptation-screen handler's Stop that ends
its recurring work loop. The static trailing `1` remains an adapter argument until
confirmed by actual framing; do not append it to an ECU payload by assumption.
The byte word `06 80` and measured RPM are not enough to qualify a general target
RPM conversion or supported range. Recover the actual traffic before implementing
the driver.

Next gates are recorded by [FEATURE-04](../backlog.md): retrieve the saved trace,
verify the ECM identity, accepted Start/Report/Return replies and actual framing,
then add source-worker driver fixtures and protected integration. Automatic ECU
release on lost refresh remains unmeasured. eGauge itself was not updated and
cannot execute high idle in this session.
