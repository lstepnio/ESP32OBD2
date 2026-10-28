# Remaining PR consolidation

> Historical evidence. Source reconciliation recorded 2026-10-28.
> [Current state](../current-state.md) owns installed facts; [backlog](../backlog.md)
> owns remaining work. This is not a release, deployment or physical certificate.

This follows the #93 assessment merge and #116 preservation of #41/#59/#74/#77.
No old feature branch is blindly merged over newer product/runtime behavior.

| Original PR | Preserved implementation/evidence and reconciliation |
| --- | --- |
| [#57](https://github.com/lstepnio/ESP32OBD2/pull/57), `9177618666955f7f6e056fdc31d0d09fd835f384` | Typed companion proposal retained, renumbered ADR-018 to preserve newer platform ADR-016 and Smart ADR-017; original acquisition/provider findings are dated inputs, with live feasibility/permissions/terms gates still open |
| [#58](https://github.com/lstepnio/ESP32OBD2/pull/58), `be868ef11acae525e0657bfd0b806eb4023225b2` | Current GATT discovery/generation/overflow/cleanup policies move into the lexical factory; transaction serialization, failure retirement, escaped-handle fencing and read-only resource-pressure retry preserved with the original regressions. The synthetic probe has no caller in App UI/startup/service, no provider or permission wiring and no capability promotion |
| [#64](https://github.com/lstepnio/ESP32OBD2/pull/64), `de8a4d31005ad919d41a19193a3b210fd3857d66` | Bounded fractional trend interpolation reconciled across C/Kotlin/generated review art; one-time PSRAM mask has deterministic rounded-line fallback. Preserve current units/scaling, timestamps/sample meaning, source resets, display profiles and new gesture/waiting behavior |
| [#72](https://github.com/lstepnio/ESP32OBD2/pull/72), `7b0ee228e4a38e0ac726ebb654a707fe3c3f7840` | Research-only coverage audit, translated catalog search, sanitized 42-bit fixture/report and conflicting TIPM variants retained. Explicit transfer-case mode uses current bounded parser/session helpers and verifies primary ECM identity before the research route; never automatic polling or a public vehicle capability |
| [#112](https://github.com/lstepnio/ESP32OBD2/pull/112), `faecd338073fee667c71a5a9566f8049edb900c2` | Dev.71 installation/owner appearance-and-wake evidence appended without rewriting later status or promoting all-angle/touch/hold-exit qualification. This is preservation of the original record, not a new device observation |

## Verification and compatibility

The normal App retains operation-scoped connections through the same operation
coordinator and deadlines. The #116 one-session Wi-Fi bootstrap remains intact.
GATT service discovery generation, late-callback overflow, connection-priority and
bounded disconnect cleanup are preserved. Failed/escaped transactions cannot be
reused; writes are not replayed. The synthetic experiment is separately removable,
while the production owner factory is shared infrastructure and must not be reverted
as an incidental experiment removal.

Core owner/probe regressions cover authentication/reset, serialization, cancellation,
retired/escaped handles, ambiguous writes, scoped boot/runtime/slot ownership,
sequence reservations, expiry and small-MTU rejection. They do not emulate radio or
establish live firmware behavior. C/Kotlin trend regressions cover bounded interpolation,
flat/extreme/missing samples and fallback allocation failure; production LVGL fixtures
retain the gesture/cache/logo regressions. Jeep tests cover exact reproducible fixture
coverage, bounded pagination, CLI isolation, primary identity and rejection before sends.

The replacement PR records exact-head affected local checks, remote four-lane CI and
rendered review. Original source branches/discussions remain retained after closure.
Website impact: only current trend art changes; other review artwork retains typography
and unrelated visuals. Marketing publication is a separate normal batch.

No dependency, wire/storage schema, permission or service activation is introduced.
No deployment, signed release, APK installation, gauge flash, physical capture,
customer-data access or vehicle operation occurred. Normal capability/qualification
masks remain unchanged. Provider approval, phone/radio/loaded-render timing, actual
TIPM reachability/variant mapping and physical display/gesture/orientation qualification
remain explicit gates. Software preservation is not qualification of future features.

## Concurrent changes retained

Main advanced with #117's diagnostic-session prerequisite while this cleanup was
running. Its code, negative/cancellation replays, dev.73 source version and dated
owner dev.72 report are retained. The historical dev.71 snapshot does not replace
that newer installed-version report. PR #118's scoped container Git ownership fix
and real dirty-source/non-persistent-trust regression are also retained. No global
Git trust setting is changed. The final consolidation includes original #116/#118
heads, so one protected, up-to-date integration can preserve their work without
forcing an outdated merge or dropping concurrent fixes.

## Legacy diagnostics compatibility review

Required automated review identified that the old same-transaction unsupported
snapshot fallback conflicts with the new fail-closed IO retirement. The fallback
now opens a fresh authenticated transaction after cleanup, only for explicit GATT
6/13 rejection of unscoped `39`. Six production-orchestration regressions use the
actual owner-session policy: supported read, both legacy statuses/cleanup order,
scoped/other failures, authentication rejection, malformed/read failure, cancellation
and invalid source. Retirement/no uncertain-write replay remains unchanged.

## Transfer staging cleanup review

A second required review found the same retirement boundary in configuration abort.
The audit also covered Bluetooth OTA's analogous abort. Both now close the failed
operation before a bounded fresh-owner status/abort attempt. Status must prove the
same transfer in a pre-commit phase. Commit/activation uncertainty is never replayed
or cleared. Six regressions cover actual owner retirement after exhausted ATT read
pressure for config/OTA, ownership/phase exclusion, preflight/commit boundaries,
cleanup timeout/error preservation, cancellation retaining the operation lease and
successful operations without abort. These are fake transport/production policy
checks, not physical transfer interruption or rollback qualification.
