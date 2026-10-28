# Unique PR reconciliation

> Historical evidence. Source reconciliation recorded 2026-10-28.
> [Current state](../current-state.md) owns installed facts; [backlog](../backlog.md)
> owns remaining work. This record does not establish deployment or physical behavior.

Baseline: main `690439b6fa6372e462f1d332538abd0cfb4fe58c`, after merged PR #93.
Work branch: `codex/pr-unique-reconciliation`. Original branches remain preserved.

| Original PR/head | Retained work | Later-main behavior preserved or intentionally superseding older work |
| --- | --- | --- |
| [#41](https://github.com/lstepnio/ESP32OBD2/pull/41), `70feea10d82e7f8fc8e52637d4142b4d18d6c40f` | One-session identity/AP bootstrap, error/cancellation cleanup; original dev.24 evidence and three images in [dated bootstrap record](wifi-update-bootstrap.md) | Current transport connection/first-response/flash stages already precede byte progress, so no redundant prepare call; current post-update identity, lease, journal and settings invalidation remain |
| [#59](https://github.com/lstepnio/ESP32OBD2/pull/59), `26faae2441f9208e8e6fde5608db0d81445e7106` | Deliberate short strokes, motion/lost-input arbitration, same-state pairing continuity, cooldown feedback; actual pointer matrix and invalid/full-cache regressions; original [browsing report/images](gauge-browsing-reliability.md) | Main's stronger cache/source/revision/transport fences, cycle recheck, action arrows and attention precedence remain; no old progress lane restored |
| [#74](https://github.com/lstepnio/ESP32OBD2/pull/74), `4fe45e87d07f1b9f0ad094b3c3c3bdaa9c5b665e` | Configured Customize rules plus Add alert, metric degC label, lazy retained history, eligible direct Current actions, shared eligibility/revalidation and UX completion standard | #115's unified Car attention/full-screen workspace, focused Car alert settings with existing guarded saves, disabled drafts, filtered evidence and detail navigation remain; original Car-card layout and phone-only save assumptions are superseded |
| [#77](https://github.com/lstepnio/ESP32OBD2/pull/77), `93cae125a5ab1c088dcef12d7f3e845dc8c555c7` | Adapted 100-redraw free-memory/object-count regression for the current four-object neutral-grey waiting mark | Current scoped-observation wake/no-data return and waking-stroke consumption supersede the original permanent adapter-ready dismissal. Original startup PNG remains historical on the retained branch; it is not exported as today's product preview |

## Integration findings and regressions

An uncertain AP request closes its protected BLE session before best-effort AP cleanup.
Identity failure/already-running rejection never requests an AP. Original startup
failure/cancellation survives a cleanup failure or a cleanup session's own timeout.
Production orchestration fixtures cover these paths without GATT/network/device access.

Real LVGL pointer dispatch exposed a deferred-entry/attention mailbox ordering race.
Deferred action entry now follows queued attention/pairing state and checks admission
again. Moved strokes cannot confirm pairing/reset, including disabled shortcut and
horizontal-motion cases; stationary deliberate confirmation remains tested. Fast,
slow, diagonal, lost-input and cooldown paths preserve ordinary horizontal browsing.
A 100-redraw fixture checks the retained waiting-logo object tree and allocator
stability after warm-up in the fixed host arena.

Attention eligibility is shared by Current/detail UI and the model command guard.
The guard resolves the latest visible episode inside the operation lease and rechecks
current owner/scope, lifecycle, simulation, availability and snooze severity. Old
callbacks cannot use a removed/hidden-only or ineligible historical episode. Busy
controls remain visible but disabled. Lazy history renders all supplied retained rows;
repository retention/storage bounds are unchanged. Disabled configured rules retain
their limits and remain excluded from Add alert rather than being lost or re-enabled.

## Verification and boundaries

Focused native pointer/configuration/render fixtures, sanitizer checks and Android
JVM/instrumentation compilation are supplemented by the complete affected local
lanes and fresh four-lane PR CI. Exact head and final results are recorded on the
replacement PR; no old-head CI is promoted to current acceptance.

Website impact: alert rule/history states and cooldown feedback can enter a future
normal screenshot refresh after rendered review. No gallery/marketing publication,
website/backend deployment or new roadmap/capability claim is made here.

No APK installation, gauge flash, signed release, live form/customer-data change,
vehicle operation or physical qualification occurred. Phone GATT/network timing,
loaded transport/cancellation, touch/vibration/legibility and physical action/Stop/
restoration remain independent acceptance gates. Existing capability/control masks
are unchanged. Closing the four original PRs preserves their branches and history;
the replacement source PR remains the software review/merge boundary.
