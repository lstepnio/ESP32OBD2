# Shared alerts and diagnostics implementation

> Historical evidence. This records the implementation session, not standing instructions.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md).

2026-10-07, development candidate `0.2.0-dev.48`, PR #51 branch
`codex/dual-adapter-runtime`. The initial implementation was verified offline. The authorized Pixel/App-Wi-Fi
rollout below followed; no vehicle writes or live adapter session occurred.
The owner phone was not used by instrumentation.
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

Android debug build, lint, all **162 JVM tests** and instrumentation APK compilation
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

## Authorized App/Wi-Fi rollout

Owner explicitly authorized deployment when ready. All three Foundation quality jobs
passed for release commit `f572974`. Protected release workflow `37729394793`
published immutable signed `dev-v0.2.0-dev.48`, generation 30. Independent downloaded
catalog/bundle checks verified both signatures, hashes, board, layout, protocol and
embedded version. Hosted image: 1,534,576 bytes; ELF SHA-256
`f83e85c7411ce6bea724f6d6f36baf6038a1d20bc287c6ddf36bb4f6192248a4`.

Pixel App replacement retained all seven original preference documents byte-for-byte.
Actual Settings hosted-update flow installed through private gauge Wi-Fi; the App
confirmed running image. A subsequent protected Expert read independently showed
version dev.48, OTA state 2 and the exact hosted ELF, running partition `0x360000`.
Profile, gauge-association and presentation documents remained byte-identical after
firmware installation; trusted generation became 30 and update journal was empty.
Owner separately confirmed a normal physical gauge page and working swipes.

The upgrade exposed a cached unsupported result: Car still requested a firmware
update for history after dev.48 was installed. The App follow-up invalidates alert
transport/support and clear preparation on a new connection, changed protected image
or confirmed update, preserving durable history and requiring fresh supported reads.
This corrects automatic feature discovery without replaying writes or replacing setup.

Standing extension rules were added to AGENTS.md, the AI context guide and ADR-015.
The orientation policy and FEATURE-07 record capability-gated Auto rotation as upcoming
work. Current firmware has no implemented IMU identity/health/axis path; Auto is not
advertised or claimed by this release. Manual rotation and page cycling remain separate.

The relay check then exposed an owner-handshake compatibility gap: new v16..19
protected responses were absent from the resumed-session format allowlist. The App
follow-up recognizes bounded valid frame lengths/counts and rejects malformed/unknown
frames. A regression fixture covers a subsequent owner session beginning with the
previous alert/context/clear/readiness response. These App corrections do not change
the already signed firmware bytes or enable code clearing.

Final App implementation `fa36164` passed 162 JVM tests, debug/test APK builds and
lint, then was installed with data-preserving replacement. The eight disposable
emulator tests preceded these two App follow-ups. Actual Pixel/gauge checks after
the corrections established a durable protected history cursor and successful
Expert synthetic relay: key 36 entered as Advisory/simulated, then expired; two
unique transitions (sequence 1 Entry/Active, sequence 2 Resolution/Expired) were
stored. The active App banner disappeared, the history/context remained explicitly
Simulated, and disconnected diagnostics remained NotChecked. This is protected
runtime state and phone presentation evidence, not an owner observation of the
physical synthetic badge or a qualified external feed.

Final automatic Settings read showed brightness 100%, rotation 270°, Imperial and
page cycling Off. Profile, gauge association and presentation documents remained
byte-identical to the pre-install backup after all checks. Original Pixel wake
settings were restored. No App-data reset, phone instrumentation, USB flashing,
live adapter session, ECU command, public-capability promotion or main merge occurred.
Physical vehicle transitions/context, phone monitoring/OEM behavior, sensor/storage
fault injection and simultaneous dual adapters remain qualification work.
