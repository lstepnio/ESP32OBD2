# Offline hardening and maintainability

2026-10-07. Source candidate App/FW `0.2.0-dev.47`. The owner authorized all seven
priorities without routine prompts. Installed owner Pixel/gauge remain dev.46.
No release publication, owner-device install, vehicle access or BLE adapter session
is part of this offline increment.

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

## Next physical gates

1. Publish a signed candidate only with separate release authorization, then install
   through App/Wi-Fi and confirm exact healthy running identity, display and touch.
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
