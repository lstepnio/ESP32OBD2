# Removable companion session experiment

Updated 2026-10-28; original experiment prepared 2026-10-19. Initial FEATURE-02/FEATURE-06 slice under approved
[ADR-018](../architecture/phone-companion-content.md). This is a developer harness,
not a user feature, background stream or new firmware capability.

## Implemented boundary

The shared `AndroidGaugeOwnerSessions` factory contains the existing protected
GATT discovery, authentication and cleanup. `GaugeOwnerSession` serializes several
transactions on that connection, fences transaction handles and retires the session
on IO failure/cancellation. Recursive transactions fail instead of waiting on their
own mutex. The normal App still uses one session per existing operation through
`GaugeConfigTransferClient.withGauge`. No AppModel/service/permission/UI wiring is
added, and no connection is retained after the factory returns.

The explicitly invoked `SyntheticRoadProbe` exercises the existing simulated `3E` and `3C`
path through that session. It checks the selected vehicle/gauge, current boot and
exact healthy runtime revision/digest, checks slot zero before use, requires a
fresh positive sequence reservation and reads back the simulated notice before
reporting application. Withdrawal also needs readback. It never overwrites an
unowned slot or replays an unconfirmed write. A three-second TTL limits a stranded
synthetic notice; time spent in preflight/reservation consumes this lifetime.

Only an Advisory fixture can be sent. The harness cannot discover live providers,
choose arbitrary commands, clear ECU faults, edit pages/settings or install firmware.
It is not an alternate alert reducer/history store. Existing production codecs
and the production-C parity fixture are reused by the scripted transport tests.

**Not implemented:** persistent idle-session cache, background content scheduler,
user-operation preemption of a continuous content stream, typed navigation/media
wire/state/display, clock negotiation, MTU-23 content fragmentation or live adapters.
Current one-shot operation preemption/cleanup remains covered by its existing tests.
These remaining boundaries are in ADR-018 and the backlog, not implicitly completed
by a reusable lexical connection.

## Isolation and builds

The owner explicitly permits experimental code in release APKs. Both ordinary
debug and release builds compile the clearly marked package
`com.lstepnio.egauge.experimental.companion`; neither activates it automatically.
There is no special build flag, alternate application ID or additional build variant.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug \
  :app:compileDebugAndroidTestKotlin :app:assembleRelease --no-daemon
```

To run only the focused JVM checks:

```sh
./gradlew :app:testDebugUnitTest --tests '*GaugeOwnerSessionTest' \
  --tests '*SyntheticRoadProbeTest' --no-daemon
```

The harness lives only under `src/main/java/com/lstepnio/egauge/experimental/companion/`,
with tests under the matching `src/test/java/` package. Files carry the searchable
marker `EXPERIMENT: companion-content`. No launcher action, UI, service or saved
preference starts it; there is no connected-device entry point in this slice.
Tests do not install or contact anything. A future runtime opt-in requires an
explicit entry point and remains separate from merely packaging the code.

CI runs these tests as part of the normal Android suite and assembles release as
well as debug. Release assembly is unsigned build verification, not publication
or installation. Packaging the harness does not advertise a public capability.

## Test boundaries

Core JVM tests cover authentication/reset, serialized reuse, cancelled waiters,
in-flight cancellation, caught write failures, read-only resource-pressure retry,
failed status propagation, escaped handles, clean factory retirement, recursive
entry rejection and cleanup retaining the existing operation lease.

Experimental JVM tests use scripted raw transport replies. They cover simulated-only
encoding, show/withdraw readback, occupied/unowned slots, scope/config/boot mismatch,
reservation delay/duplicate sequence, rejected small MTU, malformed readback and
cancellation/unconfirmed-write handling. They do not emulate or prove firmware
application, expiry, pixels, radio behavior, hardware memory or real navigation.
Existing alert C/Kotlin parity tests continue to own the unchanged wire format.

Physical session reuse is still unqualified. Before any future connected probe:

- Integrate with the existing App operation lease; never create a second
  `OperationCoordinator` or GATT owner beside the running App.
- Supply durable slot-sequence reservation and an immutable selected-target scope.
  The tests use in-memory reservations; no production reservation store is added.
- Use a bench gauge with no occupied external slot and negotiate MTU at least 67.
  The probe refuses to guess about 64-byte long writes at MTU 23.
- Keep the whole factory session deadline short and await cleanup before admitting
  another operation. If a write outcome is unknown, stop and let its TTL expire.
- Legacy `3E` lacks a revision/session token. Sender checks reduce accidental scope
  errors; they do not provide the proposed apply-time wire fencing. Do not promote
  this harness to a live provider or advertise the new content capability.

Device testing is a separate explicit task. Follow the existing awake-helper and
single-device-owner rules when it is scheduled. No firmware release is needed for
these offline tests, and no devices were changed by this source slice.

## Removal without disrupting other development

1. Search `EXPERIMENT: companion-content` and remove only
   `android/app/src/main/java/com/lstepnio/egauge/experimental/companion/` and the
   matching `android/app/src/test/java/com/lstepnio/egauge/experimental/companion/`.
2. There are no Gradle hooks, extra variants, manifest entries, permissions, services,
   UI hooks, schemas or saved data to migrate or clean up. The ordinary CI task
   automatically stops finding the removed tests; no experiment-specific CI gate
   must be unwound.
3. Retain the shared session extraction and its core regression tests as an
   independent refactor, or reverse that focused extraction separately. Do not
   restore the whole transfer client or reset later unrelated development.
4. Update the owning architecture/backlog references and this runbook/map entry.
   Run normal Android checks and the documentation validator.

The production factory/session owner is shared infrastructure after reconciliation.
Remove only the marked experiment package/tests and its documentation entries; do
not revert current production transport, newer operation leases or Wi-Fi bootstrap.
No firmware change or device reset is part of disabling/removing the experiment.
