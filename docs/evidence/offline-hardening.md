# Offline hardening and maintainability

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

2026-10-07. Source candidate App/FW `0.2.0-dev.47`. The owner authorized all seven
priorities without routine prompts. The initial offline increment kept owner devices on dev.46. The subsequent
owner-authorized dev.47 App/Wi-Fi rollout is recorded below. No vehicle or OBD
adapter session was performed.

## Scope and completed boundaries

| Priority | Implementation | Acceptance boundary |
| --- | --- | --- |
| 1. Recoverable local saves | `LocalSetupStore` commits profiles and gauge assignments together; `LocalSetupTransactions` returns acknowledged state after commit | Creation, switching and legacy TCM attachment retain coupled state; failure does not publish a candidate |
| 2. Typed failures/cancellation | Pairing reason enum and protected owner read error replace recovery message matching; `suspendResult` preserves cancellation/fatal errors; cleanup has an explicit deadline | No cancellation-to-success conversion; corrupt update evidence requires healthy protected readback |
| 3. Off-main durable work | One `DurableWrites` IO mutex serializes setup, presentation and update evidence writes | Queued cancellation skips mutation; admitted commit finishes before lease release; page browsing does not write |
| 4. Negative tests | Seeded save/switch failures, rejected commits after platform map mutation, queued/admitted cancellation, poll cleanup before local saves, uncertain image health | Pure JVM, isolated Android preference and disposable emulator journeys; no owner preference changes |
| 5. Firmware health | Eight fixed atomic worker progress records, transfer queue drops and largest/current internal/PSRAM blocks extend the minute health log | Sanitized production-C arithmetic/bounds and firmware build; no reboot policy or hardware soak claim |
| 6. Coordinator extraction | Domain models move to `GaugeModels`; setup transactions and update persistence have dedicated owners | Lifecycle and transport leases remain in AppViewModel; incremental extraction preserves existing architecture |
| 7. Documentation/dead code | Historical runtime evidence moves out of current state; stale source-subset/alert/schema claims corrected; duplicate state resets, superseded helper and redundant progress writes removed | Standing AGENTS/runtime rules, current state/roadmap/index links; migrations and vehicle evidence preserved |

## Compatibility and failure semantics

Phone profile schema becomes 12 and continues reading schemas 1 through 11.
Assignments now live alongside profiles in `profiles-v1`; the legacy assignment
file remains a read-only migration source. Older Apps reject schema 12 rather than
using stale assignments. Downgrading to dev.46 requires an explicit compatible
backup/migration, not silently editing schema numbers.

One preference commit includes both documents. If commit fails, Android's changed
memory map is restored best-effort and the prior visible model remains. Setup edits
stop until reopening/review. Failed rollback or storage hardware failure is not
proven recoverable by a host test. Invalid existing documents are never replaced
with defaults by an association save.

Confirmed gauge units are mirrored on IO within the existing settings operation
lease, avoiding nested operation admission. Local page-action saves use the same
admitted draft transaction rather than launching a second local save.

An update candidate is recorded before upload. A confirmed running gauge image
remains authoritative if local journal cleanup fails; pending evidence remains for
another protected recovery check. Corrupt journals represent uncertainty. An
unconfirmed or unknown OTA state cannot clear recovery evidence. Optional reads
propagate real coroutine cancellation, while cleanup uses its own bounded budget.

Firmware/wire schemas and public capability flags do not change. Development cfg:5
and qualified public link capacity one remain in place. The health log is local
observability, not a new BLE protocol or consumer UI. Adapter absence is valid retry
work; only owner progress is measured. Counter snapshots are approximate and
unsigned millisecond wrap is intentional.

## Verification

Final candidate checks passed:

- 156 JVM tests, debug and instrumentation APK builds, Android lint.
- 18 native emulator tests: four isolated preference/recovery cases, one actual
  AppViewModel vehicle/page/action save journey, five hierarchy UI cases, four
  reading/alert controls, one update-hold fixture and three action UI cases.
- 75 host tests, including production worker-health C with ASan/UBSan.
- Production core firmware fixtures with ASan/UBSan.
- ESP-IDF 5.4.1 firmware build: image `0x173310` bytes, 52% of the OTA slot free.
- Repository validator: four schemas, nine examples, 24 rejection cases, signed
  decode vector, 96 document link sets and design token parity; `git diff --check`.

The 18 native fixtures ran before the final settings-unit mirror correction;
that correction passed the repeated 156 JVM tests, APK compilation and lint.
Physical unit synchronization remains a hardware gate.

The native test APK was installed only on the disposable emulator. The final save
journey also checks that a local page action persists through the serialized write
boundary. No UI styling changed, so an additional screenshot baseline was unnecessary.
The Android emulator uses a disposable Android 36 arm64 image and phone-sized
viewport. It is simulated evidence, not Pixel, vehicle, radio or physical display
qualification. Preference fixtures use private namespaces; the local save journey
is guarded to emulator hardware only. The owner phone is not used.

## Authorized Pixel/App-Wi-Fi rollout

After the phone returned, all three GitHub quality jobs passed for `ec97641`.
A private preference backup preceded a data-preserving App dev.47 replacement.
The initial migration retained all profiles, legacy assignments and appearance;
new coupled assignments matched the legacy document, with schema 11 becoming 12.
No broad connected-test task or App-data reset was used.

Two selected read-only physical journeys passed against the exact signed dev.46
image and OTA health 2. Opening/resume took 7.287/5.201 seconds, then 10.574/2.773
seconds. Bluetooth off/on recovered automatically in 16.018 seconds. Hardware
snapshot followed by fresh protected firmware read also passed. These are individual
bench samples, not performance distributions. Normal vehicle-adapter unavailability
was distinct from the successful phone/gauge connection.

Actual Settings unit saves confirmed Metric on the gauge and in the phone's durable
presentation preferences, then restored Imperial. This checks the final settings
lease correction. Other display settings and appearance remained unchanged.

The owner explicitly authorized publishing and installing dev.47. Protected workflow
run `37713217386` published immutable prerelease `dev-v0.2.0-dev.47` from `ec97641`,
catalog generation 29. Independently downloaded catalog and bundle signatures,
board/layout, descriptor, sizes and hashes passed verification. The normal Settings
GitHub download/App-Wi-Fi path completed and showed **Update installed**. First
firmware response took 12.902 seconds from preflight; flash preparation 2.370 seconds.
Protected readback asserted exact hosted ELF
`004f96dc780ef333495a7307f03bf6bcb0fee8d83deee2781f86983fd1e0ecd6`
and OTA health 2. Post-install opening/resume took 9.215/2.783 seconds; hardware
read followed by fresh firmware read passed. Running dashboard revision 49 and its
hash, display settings, gauge identity, associations and appearance were retained.
The update recovery journal cleared.

A later backup comparison found the optional TCM child removed from one phone
profile, with all three dashboard pages retained. The initial migration had preserved
it; the cause of the later change is unconfirmed. The owner explicitly chose the
clean single-adapter setup and will re-add the child when a second adapter arrives.
Audit confirmed zero optional children across three saved vehicles, zero dual-adapter
selections or TCM-only gauge transport assignments, and one ECM source in the running
gauge document. Three mixed-reading pages and one alert remain. No restoration or
new configuration send was needed. Do not claim byte-for-byte full-session profile
preservation; associations, appearance and the firmware dashboard remained intact.

Private USB recording observed dev.46 boot after opening the serial port, followed
by dev.47 boot after OTA. One dev.47 minute health set showed application maximum
gap 100 ms, UI 70 ms, configured adapter retry-loop maximum gap 16.100 seconds,
zero recorded transfer queue drops, internal free/largest 77,867/31,744 bytes and
PSRAM free/largest 2,059,656/2,031,616 bytes. The optional child worker was not active.
This is bounded bench observation only. The trace-oriented recorder reported no
OBD trace events, as expected without a vehicle session; its raw health log remains
private. Physical display/touch owner confirmation remains pending.

Original phone wake settings were restored, with eGauge reopened. Personal identifiers,
preferences, captures and screenshots remain under ignored artifacts.

## Next physical gates

1. Dev.47 signed App/Wi-Fi installation and exact healthy identity passed. Finish
   owner display/touch confirmation; retain the accepted single-adapter setup.
2. Exercise backgrounding, disconnect and power interruption across config/update,
   preserving uncertain outcomes and checking protected recovery before retry.
3. Measure worker gaps, admission drops, heap minima/largest blocks and freshness
   through missing adapters, maintenance transitions and sustained polling.
4. Qualify two distinct adapters plus phone, sibling continuity on source loss and
   per-source recovery. The owner still has only one adapter.
5. Finish new reading/TCM alert rendering and live transitions against independent
   vehicle/controller references. This increment does not validate new PID meaning.

## Implementation references

- [Android runtime](../architecture/android-runtime.md)
- [Firmware runtime](../architecture/firmware-runtime.md)
- [Standing interaction/recovery rules](../architecture/interaction-recovery.md)
- [Historical runtime records](runtime-history.md)
- [Remaining work](../roadmap.md)

Platform rationale: [Android synchronous preference commit](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit()),
[Kotlin cancellation](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/coroutines-cancellation.md),
[ESP-IDF heap diagnostics](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-reference/system/heap_debug.html).
