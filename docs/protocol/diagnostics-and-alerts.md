# CEL, trouble codes, and local alerts, draft 0.1

Read-only fault snapshots and freshness-aware catalog threshold/gear alerts are implemented development features. Code clearing, attention overlays, acknowledgment/snooze and retained alert event history below are **proposed, not implemented**. [Current state](../current-state.md) owns observed support; [the backlog](../backlog.md) owns qualification and deferred scope.

## Diagnostic data

Implemented diagnostics keep one snapshot per physical adapter with a fixed responder.
The proposed [shared alert and diagnostic design](../architecture/alerts-and-diagnostics.md)
separates controller identity from transport, aggregates one logical vehicle and defines
MIL/category/coverage semantics, context, storage and notification behavior. Stored
codes do not establish MIL On, and unavailable checks do not establish no faults.
Manufacturer-specific modules and descriptions require reviewed profiles/provenance.

## Clear codes flow

**Proposed, not implemented.** The maintained [clearing plan](../architecture/alerts-and-diagnostics.md#clearing-codes-and-preserving-evidence)
defines durable pre-clear capture, explicit ECU/network scope and confirmation,
one-use operation tokens, independent firmware eligibility, at-most-once sending,
post-clear readback and reconciliation of uncertain outcomes. Standard Mode 04 does
not clear an individually selected code; acknowledgment/snooze never invokes it.

## Threshold rules

Threshold rules are part of the atomic configuration document. Each binds a PID definition **and ECU**, canonical unit, comparator (`above`, `below`, or named-gear `equals`), warning/critical boundary, hysteresis, trigger dwell, clear dwell, and future acknowledgment/snooze policy. The current wire/runtime supports thresholds and dwell, not the full future attention policy. Both levels are optional individually but at least one is required. For `above`, critical > warning; for `below`, critical < warning. Validate range and hysteresis so release thresholds remain meaningful. Changing display units converts editor labels and limits, never the canonical stored rule.

The on-gauge alert engine evaluates fresh valid samples independently of the phone and active page. Entry requires threshold violation continuously for trigger dwell. Exit requires samples beyond the release boundary (threshold minus hysteresis for above, plus for below) for clear dwell. Evaluate using monotonic elapsed time, not a fixed sample count. Any stale/invalid interval breaks pending entry/exit dwell and freezes the last active severity with a visible data-unavailable qualifier; stale data must never resolve a warning. On fresh recovery, restart dwell timers. At boot start as unknown until sufficient fresh data exists.

The source dev.46 editor exposes alerts for every selectable catalog reading,
including the calibration-specific transmission temperature and gear. Gear rules
match chosen positions and clear after a nonmatch dwell; numeric thresholds accept
fractions. Full catalog bounds, compatibility, source routing and physical follow-up
are in [reading catalog and local alerts](../development/reading-catalog-alerts.md).

## Attention and acknowledgment

**Proposed, not implemented.** Current threshold/MIL badges are not a unified retained
event/notification framework. [ADR-015](../architecture/alerts-and-diagnostics.md) owns
the shared severity/lifecycle model, producer adapters, gauge/App attention, scoped
context actions, Android notifications, history/capture and implementation/test sequence.
Preserve current dwell/hysteresis semantics; reuse the threshold engine rather than
introducing separate alert evaluation in Android.

## Implemented read-only snapshot, dev.38-faults

Each configured physical source polls Mode 01 PID 01 and Modes 03,
07 and 0A against its diagnostic responder (7E8 or 7E9). Page selection does not
select a separate vehicle. TCM faults on a shared primary transport need the
QUAL-05 routing review; two physical-source snapshots are not proof of that path. The protected companion
command `39 <sequence:u32le>` selects a cached full diagnostic snapshot. Reading
it does not initiate a vehicle scan. The scheduler populates categories separately;
initial categories remain Not checked until their first accepted reply.

Version 15 is 248 bytes. Header (32 bytes):

| Offset | Field |
| --- | --- |
| 0 | Version 15 |
| 1 | Source: 0 Engine, 1 Transmission |
| 2 | Flags: bit 0 MIL fresh, 1 MIL on, 2 connected, 3 simulated |
| 3 | MIL result: 0 not checked, 1 available, 2 unavailable, 3 unsupported |
| 4 / 8 | Configuration revision / connection session, u32le |
| 12 / 16 | Snapshot / MIL observation monotonic milliseconds, u32le |
| 20 | Responding ECU, u16le |
| 22 / 23 | MIL reported count / known flag |
| 24..31 | Reserved zero |

Stored, Pending and Permanent blocks start at 32, 104 and 176. Each is 72 bytes:
result (same enum), flags (known, fresh, truncated), count (0..32), reserved zero,
u32le observation time, then 32 two-byte big-endian DTC slots. Unused slots must
be zero. Firmware never silently truncates: its 64-byte ISO-TP payload bound
currently permits at most 31 codes after service/count bytes. Oversized or malformed
responses are unavailable. Codes are deduplicated within each category only.

Freshness requires connection, real data and a successful most recent query.
MIL expires after 60 seconds, categories after 120 seconds. Android additionally
expires its receipt after 30 seconds and verifies source, ECU and configuration
revision. Unsigned time subtraction handles the gauge clock wrapping. Failure or
disconnect retains known codes as last checked; reconnect clears them for a new
session. Empty accepted lists are distinct from unknown or unavailable replies.
Explicit DTC negative replies 11, 12 and 31 are unsupported. Other failures,
including unsupported MIL queries, currently appear unavailable.

Version 5 / command 30 remains an Engine-only partial snapshot. New Android falls
back only on explicit unsupported command/length errors (GATT 6 or 13). Auth,
transport or packet validation failures never trigger fallback. New firmware
blanks version 5 Transmission data to prevent old apps labelling it as Engine CEL.
The owner identity gate remains required; public capability flags are unchanged.
Legacy single-source firmware samples only its configured source. Current independent-source behavior is defined below.

This implementation reads codes only. The clearing flow above remains a design.
Physical qualification still requires the 248-byte long read on Pixel, category
coverage on the gauge and disconnect/reconnect checks with the real adapter.

## Independent development sources, dev.41

Each configured adapter has its own connected state, session, MIL and three fault
categories. Disconnect clears freshness only for that source; reconnect increments
its session and clears old categories. Source-specific `3A` reads keep the v15 layout;
`39` remains source zero. See [source selection](adapter-bindings-v1.md).
Snapshot lock contention yields a retryable resource error, not empty healthy data.
The app reads disconnected snapshots as last checked and keeps separate poll backoff.
No fault-clearing command is added.
