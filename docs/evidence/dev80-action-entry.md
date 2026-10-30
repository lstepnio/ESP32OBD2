# Dev.80 action entry and connection presentation

> Historical evidence. [Current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-29. This continues the owner's action UI implementation/device
delivery request with the follow-up to stop entry at the third action, omit the
generic unknown verdict and normalize Connecting. No vehicle controls or owner
reset confirmations were operated, and no vehicle uncertainty record was cleared.

## Cause and implementation

Entry previously selected the first active/unresolved record after synchronizing
configured pages. The retained ABS/TCS Unknown record selected its third page.
Entry now selects index zero after synchronization, respecting configured reorder.
Recovery remains reachable through the existing explicit Review/Restore controls.

All offline faces render **Connecting** at the same position with the same neutral
font/color and no subtitle or button. Generic “Not verified” copy is omitted; the
underlying command phase, admission, durable marker and recovery proofs remain.
App dev.57 uses matching connection/recovery language. No protocol, storage, ECU
command, background retry or qualification flag changed. Native fixtures exercise
reopen/reorder with a retained third-page record and compare all three rendered
connection faces, font/color, hidden controls and absence of command side effects.

## Accepted source and delivery

Source `0ef89b7828e7089fc5639660b98c84713a71ad02` passed all four lanes in
[quality run 40997689482](https://github.com/lstepnio/ESP32OBD2/actions/runs/40997689482).
Local JVM/build/lint, focused managed UI and production normal/fallback/parked LVGL
plus configuration fixtures passed. The signed candidate retains the existing
parked-idle, owner-brake and orientation options without promoting qualification.

[Dev.80](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.80)
was published by [run 40998985098](https://github.com/lstepnio/ESP32OBD2/actions/runs/40998985098),
generation61. Catalog, image and build-evidence signatures, freshness, source,
board/layout, candidate configuration and bundle/evidence image parity passed.

- Image bytes: 1699280.
- Image SHA-256: `c8d872b5e4113616d0ce02001f807a9e8bdb20307059d6edf849286750f92fb4`.
- ELF: `889ae28fe30ccb7be491c50503773d2488114c77cc26c509eeca2b3e280e0a7a`.
- Replacement App dev.57/code57 SHA-256: `9e1307ae225420c62c2bd8130dd57dd6467427bd4f95b40d4b2722f9ae7f2e58`.

The full hosted App/Wi-Fi installation fixture passed on this candidate, including
exact healthy identity, alternate slot, cleared update journal and saved-state
preservation. Two maintenance opens/closes, automatic connection/recovery and cached
diagnostic readback also passed. No USB flashing or image resend was used.

Raw configuration revision 66, SHA-256
`807623e49e2995cb50bad065979ed974b627503f4314bdff3b16db971697b153`, display preferences, profiles, associations and retained
history were preserved. All command phases and operation IDs matched the fresh
baseline, including retained ABS/TCS Unknown operation1. Phone notification and wake
policies were restored. These software/transport checks do not establish actual
brake restoration or physical panel behavior.

## Remaining scope

The owner confirmed that repeated action entry starts on Air up, the generic
unknown verdict is absent and Connecting styling matches all three action faces.
Only navigation was requested; this is separate from control qualification. Parked Jeep recovery, restoration and loaded role/reading qualification
remain separate. Burnout stays paused; public capability flags remain disabled.
[PR #127](https://github.com/lstepnio/ESP32OBD2/pull/127) remains stacked on unmerged
PR #126; no main merge was requested. Website visuals remain queued for the normal
daily window; this correction introduces no new vehicle-support claim.
