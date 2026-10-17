# Paired-phone alert providers

Maintained integration contract for FEATURE-06. Updated 2026-10-07.
[ADR-015](alerts-and-diagnostics.md) owns the common event model;
[wire contract](../protocol/diagnostics-and-alerts.md) owns byte layouts;
[backlog](../backlog.md) owns provider activation and qualification.

## Current implementation and scope

`ExternalAlerts.kt` provides `PhoneAlertProvider`, typed notices, a reviewed-provider
registry and validation. A debug Expert action sends an explicitly simulated,
30-second advisory through the same protected owner connection, gauge reducer,
history and context UI as vehicle alerts. It cannot become a Critical alert or
produce a live vehicle notification. This is a test seam, not a live crowd feed.
No commercial provider, location collection, notification scraping or account
integration is enabled. The App receives gauge-originated threshold and fault
alerts automatically; providers never evaluate those thresholds independently.

A crowd hazard is contextual information, not proof of a vehicle fault. It may
expire, be withdrawn or become unavailable; none of these clears ECU codes. An
alert provider cannot raise idle, disable ABS, issue arbitrary BLE commands,
change vehicle settings or select a critical gauge priority.

## Owner and routing

Use the existing application-scoped AppViewModel and operation coordinator. The
optional Android connected-device monitor shares that owner with the Activity.
The provider returns data; App orchestration obtains the READ lease and relays it.
Never introduce a second BLE connection owner or hold a lease while making HTTP
requests. Respect configuration, discovery, OTA and explicit clear exclusivity.

Bind a notice to an explicit vehicle ID, selected assigned gauge, running
configuration revision and current gauge boot ID. An ECM and its optional TCM
child remain one logical vehicle. A source reconnect or vehicle switch must not
retarget a notice to another car. Check the scope again after asynchronous work.
The authenticated paired owner channel is the trust boundary. Public BLE
capability flags remain unchanged; advertisements do not authorize delivery.

## Provider adapter checklist

Before adding a live provider, record in its `ProviderReview` and implementation:

1. Official supported access method, license/terms, permitted redistribution and
   attribution. Review API ownership and user consent before implementation.
   Availability of an Android app or website does not grant API/feed access.
2. Provider/schema version, event identity, timestamps, correction/withdrawal
   semantics, reliability and documented maximum age. Preserve raw references in
   phone context when redistribution permits them; do not store access tokens there.
3. Maximum severity (normally Information or Advisory; Warning requires a reviewed
   relevance policy), TTL at most ten minutes and supported relevance criteria.
4. Region, travel direction, distance and road matching policy. Treat uncertain
   location, stale fixes or unavailable network as unknown. No highway snapping or
   distance threshold is currently implemented. Qualify these before live delivery.
5. Explicit opt-in, permission denial behavior, background limits, account removal,
   privacy/retention, attribution and export policy. Location/VIN are absent by default.
6. Bounded fetch queue, HTTP timeout, cancellation, jittered exponential backoff,
   cache limits and deduplication. Retry reads; never replay expired notices.

The first provider should be implemented behind this interface with fixtures,
then qualified independently. Do not make vehicle alert/history functionality
conditional on a provider account, phone network or location permission.

## Typed notice and normalization

| Field | Meaning and admission |
| --- | --- |
| provider | Unique reviewed adapter ID; registry authorization required |
| correlationId | Stable provider identity, 1..128 characters, scoped to vehicle |
| vehicle | Explicit saved logical vehicle ID; must match delivery scope |
| title | Human-readable, no control characters, at most 31 UTF-8 bytes |
| context | Compact gauge context, no control characters, at most 11 UTF-8 bytes |
| severity | Information, Advisory or Warning, capped by review; never Critical |
| ttlMs | Remaining lifetime, positive and bounded by review and ten minutes |
| receivedAtElapsedMs | Phone monotonic time; subtract queue/fetch delay before relay |
| simulated | True for fixtures; retained in wire, history and UI; no live notifications |

Keep the full provider context, attribution and licensed source link on the phone.
The compact label/context sent to the gauge is deliberately bounded. Validate
both UTF-8 size and content before transport. Wall-clock timestamps may accompany
history but cannot extend monotonic TTL. Reject unreviewed, future-dated, expired,
wrong-vehicle or malformed notices before obtaining the BLE lease.

## Delivery, deduplication and expiry

The implemented private `3E` command supports eight external slots, keyed in the
shared reducer as 36..43. Each slot receives the current gauge boot ID and an
increasing positive sequence; persist the next sequence on the phone before
sending. The gauge rejects retired boots and non-increasing sequences. A queue
admission acknowledgment does not prove presentation: read `3C` for applied state.
The synthetic adapter currently uses slot zero. A live adapter must add a bounded
persistent `(provider, correlationId, vehicle, gauge, boot) -> slot` allocator:

- Updates to the same notice retain the slot and episode; sequence increases and
  remaining TTL is recomputed. Do not treat each fetch as a new alert.
- Reuse only free/expired slots. If all eight are occupied, coalesce duplicates
  and drop lower relevance notices with a recorded reason; never evict a local alert.
- Before assigning an occupied slot to a different identity, withdraw it with
  TTL zero and confirm resolution through `3C`. Otherwise the reducer would join
  two different hazards into the same episode. Do not retry an uncertain withdrawal
  as a new identity until state readback establishes the slot.
- A reboot invalidates the old slot map and replay counters. Re-fetch relevance;
  do not replay cached notices into the new boot blindly.
- A disconnect does not extend lifetime. The gauge expires a received notice using
  its own monotonic clock even if the phone disappears. Provider withdrawal uses
  TTL zero; this is unrelated to vehicle clearing.

Polling and history synchronization are coalesced through the existing controller.
Reconnect imports are silent. Only fresh, non-simulated Warning/Critical transitions
qualify for OS notifications; a provider's severity cap still applies.

## Integration acceptance cases

Run provider unit tests without a vehicle: authorized/unknown registry, severity
caps, Unicode boundaries, incorrect vehicle/gauge/revision, stable correlation,
future/expired timestamps, delayed delivery, duplicate/out-of-order messages,
withdrawal, slot exhaustion, restart and retention. Extend the production C/Android
parity fixtures when bytes change, not independently generated mirror fixtures.

Use a disposable emulator for permission denial, foreground/background behavior,
process restart, context links and provider data redaction. Then test the paired
phone/gauge: boot mismatch, disconnect expiry, readback, autonomous gauge alert
priority, incoming hazard while a local Critical is active, OTA contention and
vehicle switching. Verify provider access, real relevance and attribution before
promoting live support. Keep observed hardware results separate from simulations.
