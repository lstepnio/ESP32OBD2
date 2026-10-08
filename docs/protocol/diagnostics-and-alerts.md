# Diagnostics and shared alert development wire

Implemented source candidate: `0.2.0-dev.48`. Physical qualification is separate.
[Current state](../current-state.md) owns support/installation;
[ADR-015](../architecture/alerts-and-diagnostics.md) owns behavior/ownership;
[provider guide](../architecture/phone-alert-providers.md) owns future integrations.
All commands below use the protected paired-owner control/state characteristics.
ATT admission of a queued write does not confirm application. Read back state.
Public `configWrite:false`, `ota:false` and link capacity one are unchanged.

## Threshold integration

Atomic schema-2 rules retain PID/ECU identity, canonical units, above/below/named-gear
comparators, optional Warning/Critical boundaries, hysteresis, trigger/clear dwell and
priority. The existing engine remains the sole threshold evaluator. It emits into
shared keys 0..31; Android does not re-evaluate values. Gauge Warning/Critical map to
shared severity 3/4. Invalid/stale data breaks pending dwell and preserves an active
condition with availability Unknown; it never establishes resolution. Display units
convert editor/context presentation only. Acknowledgment and five-minute snooze
change attention, not the rule, condition or ECU codes. Escalation resets attention.

## Implemented read-only snapshot, dev.38-faults

Legacy physical-source snapshots retain their layouts. Development dev.48 also
keeps Engine/Transmission endpoint snapshots independent of physical adapter slots.
Each enabled endpoint polls Mode 01 PID 01 and Modes 03, 07 and 0A through its
configured transport. Page selection does not select a separate vehicle. The protected companion
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

Physical qualification still requires the 248-byte long read on Pixel, category
coverage on the gauge and disconnect/reconnect checks with the real adapter.

## Independent development sources, dev.41

Each configured adapter has its own connected state, session, MIL and three fault
categories. Disconnect clears freshness only for that source; reconnect increments
its session and clears old categories. Source-specific `3A` reads keep the v15 layout;
`39` remains source zero. See [source selection](adapter-bindings-v1.md).
Snapshot lock contention yields a retryable resource error, not empty healthy data.
The app reads disconnected snapshots as last checked and keeps separate poll backoff.
These legacy source commands do not clear faults.

## Controller endpoint reads, dev.48

`3B <sequence:u32le> <endpoint:u8>` (6 bytes) selects the same 248-byte version-15
layout for endpoint 0 Engine or 1 Transmission. The sequence is a request marker,
not an event cursor. Endpoint role, revision, session and responder remain explicit.
Endpoints use 7E8/7E9 for the existing CAN profile; these are not universal module IDs.
A shared primary transport can route both endpoints; an optional TCM child replaces
only Transmission routing. Unsupported endpoint admission returns GATT 6. Only GATT
6/13 permits Android legacy fallback, never authentication or malformed data.
MIL, Stored, Pending and Permanent use fair deadlines, minimum two-second background
spacing and bounded per-category backoff. A due reading gets progress between
background jobs. A changed fresh MIL coalesces a refresh of three code categories.
Source loss invalidates routed endpoints only; snapshot reads never scan the car.

`63 <sequence:u32le> <endpoint:u8>` reads raw readiness (version 19, 24 bytes):

| Offset | Field |
| --- | --- |
| 0 / 1 | Version 19 / endpoint |
| 2 / 3 | Flags: known=1, fresh=2, connected=4, simulated=8 / reserved zero |
| 4 / 8 | Configuration revision / connection session, u32le |
| 12 / 16 | PID 01 observation / snapshot monotonic milliseconds, u32le |
| 20..23 | Raw Mode 01 PID 01 A/B/C/D, zero until known |

This preserves readiness evidence for reports without inventing a monitor interpretation
for a custom controller. Known bytes may be last checked; fresh follows MIL rules.

## Shared events and cursor synchronization

`3C <active:u8> <boot:u32le> <cursor:u32le>` (10 bytes) reads version 16, at most
360 bytes. Active=0 selects transition history; Active=1 selects current episodes.

| Header offset | Field |
| --- | --- |
| 0 / 1 | Version 16 / selected active mode |
| 2 / 3 | Flags: coverage gap=1, page complete=2, storage gap=4 / record count 0..4 |
| 4 / 8 / 12 | Current boot ID / oldest retained sequence / newest sequence, u32le |
| 16 / 20 | Evicted transition count / next cursor, u32le |
| 24 | Start of up to four 84-byte records |

History cursor is the last committed transition sequence. Boot mismatch, lost ring
coverage or cursor ahead of producer advertises a gap and restarts retained history.
Active cursor is the next key index, not a sequence; paginate until complete and
resume transition history. Commit events and history cursor in one SQLite transaction
before advancing. Imports/reconnect pages are silent. An inactive historical record
is not proof that missing current data resolved a condition. Sequence exhaustion
creates a new boot identity/gap rather than ambiguous wrapped ordering.

| Record offset | Field |
| --- | --- |
| 0 / 4 / 8 / 12 | Sequence / originating boot / episode ID / transition milliseconds, u32le |
| 16 | Key, u16le: thresholds 0..31, MIL 32/33, fault aggregates 34/35, phone slots 36..43 |
| 18 / 19 | Severity 0 Inactive, 1 Information, 2 Advisory, 3 Warning, 4 Critical / unavailable 0 or 1 |
| 20 / 21 | Lifecycle 0 Resolved, 1 Active, 2 Expired / kind 1 Entry, 2 Severity, 3 Resolve-or-expire, 4 Availability, 5 Attention |
| 22 / 23 | Acknowledged 0 or 1 / flags simulated=1, snoozed=2 |
| 24 / 28 | Canonical value / active boundary, IEEE-754 float32 little-endian |
| 32 / 64 | NUL-terminated UTF-8 label (32 bytes) / unit or phone context (12 bytes), zero padding |
| 76 / 80 | Value observation milliseconds / running configuration revision, u32le |

Boot/episode/key identifies an occurrence; gauge/boot/sequence identifies a transition.
Same-severity samples update current state without flooding transition history. First
entry evidence is retained on the phone. Missing data preserves the prior value and
severity with unavailable set. Steady MIL On is Warning; stored/pending codes are
Advisory; permanent-only codes Information. Codes do not imply MIL On.

`3D <key:u8> <reserved:u16=0> <episode:u32le> <boot:u32le> <snoozeMs:u32le>`
(16 bytes) queues acknowledgment when snooze=0, otherwise snooze up to 300000 ms.
Retired identity is ignored; duplicate acknowledgement is idempotent. Snooze expiry
emits Attention, not a new hazard. Gauge touch acknowledgment uses the displayed
identity, so a race cannot acknowledge a different newly selected alert.

## Paired-phone notices

`3E` is 64 bytes: opcode/key slot 0..7/severity 1..3/simulated 0..1 at offsets 0..3;
boot/positive sequence/remaining TTL (u32le) at 4/8/12; label[32] at 16;
compact context[12] at 48; reserved zero u32 at 60. TTL is at most 600000 ms;
zero withdraws a notice. Firmware rejects malformed UTF-8, controls, nonzero padding,
retired boots, non-increasing per-slot sequence and Critical priority. Expiry is
autonomous. The provider never chooses vehicle commands. The debug synthetic
adapter uses slot zero and a 30-second Advisory, persisted sequence before send.
See the provider guide for live slot allocation, review and authorization gates.

## Bounded sensor context

`3F <key:u8> <boot:u32le> <episode:u32le> <sampleOffset:u32le>` (14 bytes)
selects version 17. Header is 24 bytes: version at 0; flags available=1,
complete=2, partial=4 at 1; key at 2; reserved zero at 3; boot/episode/configuration
revision/total samples/returned offset at 4/8/12/16/20, all u32le.
Up to 16 samples follow, each 12 bytes: frame milliseconds u32, canonical float32,
PID definition index u8, valid 0/1 u8, reserved zero u16. Maximum total is 480.
Missing capture returns available=false with no samples. Validate identity/revision,
count, offset and finite values when valid; invalid samples are not zeros.

The gauge retains two volatile windows, up to 16 configured readings, sampled at
1 Hz with ten seconds before and twenty after a local Entry/Severity transition.
Original trigger value/boundary remains in the event. A short prebuffer or more
than sixteen configured readings marks partial. Allocation failure, reboot or
replacement can make a window unavailable; the phone records that limit. These
windows are context, not a continuously saved high-rate vehicle log. Successful
phone import is durable independently of subsequent gauge loss.

## Explicit clearing, qualification gate closed

`CONFIG_EGAUGE_DTC_CLEAR_ENABLED` defaults off. This candidate does not offer live
ECU clearing. When deliberately qualified/enabled, only Engine endpoint 0 is
eligible; custom JSS Transmission clearing remains unsupported. Preparing requires
scope/connectivity, a saved pre-clear report and an exclusive transfer gate.

- `60 <endpoint:u8=0> <operation:u32le> <revision:u32le>` (10 bytes) prepares a
  one-use token tied to revision/session, expiring after 30 seconds.
- `61 <parked-confirmed:u8=1> <operation:u32le> <token:u32le> <revision:u32le>`
  (14 bytes) queues exactly one Engine clear. Firmware independently checks scope,
  expiry and any fresh engine-running/moving evidence. Phone confirmation remains
  necessary when those readings are not available; never infer stopped from dashes.
- `62 <sequence:u32le>` (5 bytes) selects cached status; repeated reads never send.

Version 18 is 32 bytes: version/phase/supported/endpoint at 0..3; operation/token/
revision/session/preparedAt (u32le) at 4/8/12/16/20; reserved zero at 24..31.
Phases: 0 Disabled, 1 Idle, 2 Prepared, 3 Sent, 4 Verifying, 5 Verified,
6 CodesRemain, 7 PermanentRemain, 8 Unknown, 9 Failed. Transport worker commits
intent before sending addressed Mode 04 to 7E0; functional broadcast clearing is
forbidden. It verifies MIL plus three categories. Permanent codes can remain.
Prepare/ack/snooze/history deletion never invokes Mode 04. No individual-code clear.

Phone and gauge preserve uncertain intent across restart, reconcile with reads and
never automatically resend. Disconnect/timeout/power loss after sending is Unknown,
not success. App reports retain pre/post scope, diagnostic lists and raw readiness;
freeze-frame collection is not implemented and is explicitly labelled not captured.
Hardware clearing, NVS fault injection, monitor lifetime and poll/memory latency
remain separate qualification gates in QUAL-11.
