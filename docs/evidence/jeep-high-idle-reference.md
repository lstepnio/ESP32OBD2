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
  approximately 700 RPM as control returned. The recovered ECU stream instead
  reports 1491 RPM at that part of the deceleration; the apparent 491 screenshot
  reading is not evidence of engine undershoot. Prefer timestamped parsed ECU
  values over a screenshot with changing digits.
- Screenshot completion timestamps include input, display refresh, transport and
  image-capture latency. They are sampled phone observations, not exact ECU
  execution or physical release times. No independent tachometer timing/video was
  collected. No fault-code clearing or deliberate adapter-loss test was performed.

## Trace collection

Android Bluetooth HCI snoop logging was initially Disabled and was temporarily set
to Enabled, followed by a Bluetooth off/on cycle before the ECM test. The first
export verified a valid HCI log, covering 20:57:15.105 through 21:00:38.601 UTC;
that snapshot ends before idle activation and is **not** the idle-control trace.
A second export was requested after both Start/Stop cycles. USB disconnected
before copying; after reconnection its saved export was successfully retrieved.
The recovered HCI file contains 12,326 complete records, 516,498 bytes, from
20:57:15.105 through 21:05:35.282 UTC. All record drop counters were zero and the
parser consumed the complete file. This is host/adapter traffic, not an independent
CAN bus recording or proof that the controller never dropped data.

HCI SHA-256: `65edcfa50feb728b4f87650e1118c58b7d6d492ef3f070f0658880333112c308`.
ACL fragments were reassembled per direction/connection, ATT write/notification
payloads decoded, and RX fragments associated only with the preceding request on
that connection. Only complete ELM prompt-delimited replies informed results.
Temporary HCI logging was restored to Disabled, Bluetooth cycled off/on, and the
original phone timeout/charging-wake values restored and read back. The logging
socket was never enabled.

Raw Android exports, HCI traffic, VIN/device identity, screenshots and event times
are private ignored artifacts under `artifacts/jeep-high-idle-live/`. They must not
be committed or attached to a public PR. Only scoped, sanitized request/reply
vectors and a summary belong in source control.

## Verified reference transactions

JScan selects the engine route with `AT SH 7E0`, `AT FC SH 7E0` and `AT CRA 7E8`,
all acknowledged `OK`. The first cycle enters session `10 92 1`; the second reuses
the existing session. These are selected observations, not a complete adapter
initialization recipe or evidence that reconnect may skip session establishment.

| Adapter request text | Complete returned frame | Observation |
| --- | --- | --- |
| `10 92 1` | `7E8 02 50 92` | Positive session reply in first cycle |
| `33 05 1` before each Start | `7E8 03 73 05 01` | Report byte 01 before activation |
| `31 05 06 80 1` | `7E8 02 71 05` | Positive Start reply in both cycles |
| `33 05 1` while active | `7E8 03 73 05 02` | Report byte 02 during activation |
| `32 05 1` | `7E8 02 72 05` | Positive return-control reply in both cycles |

The report-byte meaning is inferred from observed before/active behavior, not a
complete vendor status enumeration. There was no explicit post-Stop report query
in these cycles. Start acknowledgment, report state and actual RPM must remain
distinct. The adapter text includes trailing response-count-style arguments; HCI
does not directly capture the outgoing CAN frame, so do not treat the text's final
`1` as an extra controller payload byte.

| Event | First cycle, HCI UTC | Second cycle, HCI UTC |
| --- | --- | --- |
| Start transmitted | 21:00:51.170909 | 21:02:41.345397 |
| Start acknowledged | 21:00:51.222744 | 21:02:41.383698 |
| Return transmitted | 21:01:08.853867 | 21:02:52.980150 |
| Return acknowledged | 21:01:08.892522 | 21:02:53.024041 |

Report requests were interleaved with live readings and other Trail View work.
Across the selected pre-Start through Stop windows, first-cycle intervals ranged
152..1082 ms (median 557 ms); second-cycle intervals 497..1046 ms (median 602 ms).
JScan also sends functional tester-present text `3E 02 0` on `7DF` between engine
work. Do not copy unrelated module polling or infer a required refresh deadline
from this observed schedule. This is not the adaptation screen's two-second loop.

The multi-PID `01050D0C0F 2` replies were reassembled using the declared ISO-TP
length, then the RPM bytes following PID 0C decoded as `(256*A+B)/4`. Padding bytes
were excluded. On the same HCI clock relative to each return request:

| Reading sample | First cycle | Second cycle |
| --- | --- | --- |
| Last sample before Stop | 1700 RPM at -0.130 s | 1704 RPM at -1.049 s |
| First deceleration sample | 1265 RPM at +0.681 s | 1491 RPM at +0.443 s |
| Near normal | 737 RPM at +2.841 s | 735 RPM at +3.057 s |
| Later normal sample | 697 RPM at +8.034 s | 697 RPM at +9.592 s |

These use request timestamps plus asynchronous reply/display latency, not exact
physical actuation time. HCI/phone and Mac input-event clocks are separate and
were not synchronized. Do not subtract across them to claim subsecond response.
A positive return reply alone is not proof that RPM has settled.

Selected complete, sanitized replies are retained as
[offline fixture cases](../../firmware/gauge/tests/fixtures/jeep-high-idle.json).
They include both cycles' positive Start/Report/Return replies and the observed
route/session setup, without VIN, adapter identity, raw Android export or unrelated
traffic. They are data for offline regression work, not an executable allowlist.

## Static match and remaining qualification

The archived assembly's
`TrailViewPageDashboardViewModel.<PrepareLiveDataForJK>d__118` selects
`InputOutputControlSwitchDataInfo` with the same Start, Report and Return strings.
This is **different** from the earlier adaptation-screen handler's Stop that ends
its recurring work loop. The live reference resolves explicit ordinary Stop;
it does not establish automatic restoration by simply ceasing refresh traffic.

The word `06 80` has numeric value 1664, while observed active RPM is approximately
1700. This single setting does not validate a general RPM conversion, a supported
range or a safe default. The earlier adaptation handler's numeric encoding remains
static evidence for that separate path. Do not silently equate those APIs.

The ECM route and live engine values are verified in this trace. Calibration ID
`68059434AD` is the earlier recorded target, not a fresh identity measurement from
this session. Current identity, supported targets, out-of-session/negative replies,
post-Stop state report, lost-refresh/connection behavior and equipment-load
qualification remain open under [FEATURE-04](../backlog.md). No deliberate
communication-loss test ran, and no eGauge driver or firmware was installed.
