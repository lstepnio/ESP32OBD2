# Shared alerts and diagnostics implementation

> Historical evidence. This records the implementation session, not standing instructions.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md).

2026-10-07, development candidate `0.2.0-dev.48`, PR #51 branch
`codex/dual-adapter-runtime`. Source/offline evidence only. No physical gauge,
Pixel, vehicle writes or firmware installation were performed for this increment.
The attached owner phone was not used by instrumentation. Devices remain dev.47.
[Current state](../current-state.md), [backlog](../backlog.md) and
[ADR-015](../architecture/alerts-and-diagnostics.md) own maintained decisions.

## Implemented boundaries

- Existing threshold dwell/hysteresis emits into shared events, with independent
  availability, severity, lifecycle and attention. Critical overlay uses its
  displayed identity and shows the triggering value/boundary in chosen units.
- Engine/Transmission endpoints are separate from physical slots. The primary
  can route both; optional child replaces Transmission routing only. Fresh MIL
  and full code categories remain independent, including MIL Off plus codes.
- Fair polling keeps both readings and diagnostics progressing, coalesces MIL
  changes and bounds failed-category backoff. Existing responder/session checks
  reject retired data. Legacy 248-byte vector remains byte-identical.
- Bounded gauge RAM/NVS transitions, two volatile sensor windows and transactional
  phone history/cursor import preserve first-entry evidence and mark coverage loss.
  Reports/deletion/pins are scoped and do not clear vehicle codes. A bounded
  RAM fallback preserves fresh presentation if phone history storage fails, without
  advancing its durable import cursor.
- Application-scoped phone owner, shared UI, scoped notification context,
  opt-in connected-device monitoring and platform permission/channel policy.
  Foreground, replayed, simulated and unavailable events cannot become new OS hazards.
  Pending OS posts are tracked for cancellation; transition identity prevents replay.
- At-most-once Engine clear/token/readback paths, durable pre/post evidence and
  interrupted-intent reconciliation exist behind a default-off build gate.
  Synthetic paired-phone seam is implemented; no live provider is activated.

## Offline verification

Production C fixtures run with AddressSanitizer and UndefinedBehaviorSanitizer:
threshold-to-event dwell/stale/resolve, idempotent acknowledgment, snooze/clock wrap,
TTL expiry, ring gaps, sequence exhaustion, fresh-critical arbitration, strict
external text/admission, prepare/confirm expiry/session/moving conditions and
unknown restart. Firmware/Android event wire parity uses a vector emitted by
production C, not a separately synthesized packet. Endpoint/readiness tests cover
Engine and Transmission on one transport, sibling loss and immutable legacy bytes.
ELM fixtures cover addressed Mode 04 ACK, wrong responder and duplicate responses.
Core scheduler fixtures cover slow/absent readings, no bursts, sibling failure and wrap.

The prior audit's ten-minute case selected one job every 30 seconds with a due
500 ms PID. It produced MIL=3, DTC=0, PID=20. The corrected production scheduler
selects PID=10, MIL=3, Stored=3, Pending=2, Permanent=2. With two endpoints it
selects PID=10 and jobs 2/2/1/1 for Engine, 1/1/1/1 for Transmission. This is a
synthetic admission stress case, not measured vehicle latency. Real reads are
bounded at 300/700/1500 ms and physical rates/heap/flash latency remain unqualified.

Android debug build, lint, all **161 JVM tests** and instrumentation APK compilation
passed. A disposable API-36 Android TV emulator with phone-size display override
passed **8 focused instrumentation tests**: real SQLite transaction/import/restart,
old revisions/unknown scope, pins/deletion, out-of-order checkpoint/report scope,
shared offline history/context/export, real OS notification suppression/cancel and
the two existing primary journeys. It is not a Pixel or phone battery/OEM result.
Documentation policy cases and repository contract/link validation accompany this
increment. CI outcomes are authoritative on the PR; local results are not CI results.

## Qualification and remaining limits

The source image builds into a 3 MiB app slot with approximately 51% remaining.
This does not establish running heap/stack headroom. Gauge context allocation may
fail gracefully, only two windows are retained, at most sixteen readings are
sampled at 1 Hz and reboot loses windows before phone import. NVS batches the last
32 transitions every five seconds; recent uncommitted transitions can be lost.
Measured flash wear/latency, PSRAM failure, full storage and power-loss injection
remain QUAL-11 work. Raw readiness is saved; freeze frames are labelled not captured.

Physical attention/touch, paired synthetic relay, locked/background Pixel lifecycle,
long-read coverage, sequential ECM/TCM and actual clearing still require owner-assisted
qualification. Clearing remains off; no live JSS clear semantics are assumed. The
owner still has one adapter, so simultaneous physical ECM/TCM plus phone is blocked
by hardware availability. The [provider integration guide](../architecture/phone-alert-providers.md)
requires reviewed access/licensing, relevance and a bounded persistent slot allocator
before any live crowd provider is enabled. iOS remains deferred. Public capability
flags and qualified link capacity are unchanged.
