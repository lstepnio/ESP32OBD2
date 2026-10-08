# Product UX, architecture and resilience review

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

Updated 2026-10-07. Android source dev.45; firmware dev.45 is unchanged by this work.
This review implements scoped improvements directly. It does not certify universal
vehicle support, dual-radio coexistence, accessibility conformance or deadlock immunity.

## Executive assessment

The existing architecture already separates immutable screen contracts, shared
Material components, one operation lease, foreground reads and independently scoped
adapter state. Atomic configuration and firmware journals require running proof
before success. Preserve those boundaries rather than introduce a replacement state
framework or a new dependency.

The highest-impact verified weaknesses were duplicated connection messaging,
engine-only fault summary selection in a combined vehicle, stale pairing-completion
copy, small-window title compression, non-scrollable choice dialogs, and HTTP IO
with per-read timeouts but no total deadline or cancellation-driven retirement.
Fast adapter retries also repeated protected gauge reads unnecessarily.

Risks: message classification still depends partly on transport error strings;
`AppViewModel` remains a large coordinator; signed catalog discovery checks many
bounded candidates; and hardware qualification covers fewer failures/devices than
the source implements. None of these alone proves observed jank or a deadlock.

The strongest argument against simplification is loss of scope or recovery truth.
Accordingly, missing checks, stale settings, unknown commits, pairing authorization,
preview labels and destructive-action confirmations remain explicit. A healthy ECM
must never certify the child or erase its previously checked fault evidence.

## Assessment scope

| Journey/boundary | Code reviewed | Conclusion |
| --- | --- | --- |
| Startup, permissions, file picker, lifecycle | MainActivity, AppModel, stores | Foreground owns automatic work; cancellation must not become a package-invalid message |
| Gauge and previews | HomeScreen, RoundPreview, PresentationMapper | Example labels retained; routine connection cards duplicate the universal pill; Home lacked explicit next/previous accessibility actions |
| Discovery and pairing | SetupScreen, BLE discovery/owner flow | System code confirmation retained; bonded-owner checks are automatic; completion incorrectly claimed adapter setup unavailable |
| Page/layout/alert/review | DashboardEditorScreen, pickers, AlertForm, ConfigurationProjection | Local edits remain separate from exact reviewed send; shared spacing and per-vehicle editor identity improved |
| Car/profile/adapter/actions/faults | CarScreen, VehicleDashboard, DiagnosticPresentation | User selects ambiguous physical adapter; source/group scope must survive combined faults; coverage details belong in Expert |
| Gauge selection/settings | SettingsScreen, GaugeAssociationStore, display reads | Automatic protected settings remain required; dialogs need scope reset and scrolling; failed reads must not permit stale writes |
| Expert and optional child | ExpertScreen, PresentationDetails, per-source status | Technical coverage preserved here; child affects routing, not vehicle pages |
| Hosted updates and recovery | HostedFirmwareRelease, DevUpdateBundle, SocketIo, operation/update journal | Signature/generation/size checks retained; HTTP cancellation/deadlines and automatic/manual check handover needed tightening |
| Connection/polling state | ForegroundConnectionController, VehiclePollSchedule | Existing leases await cleanup; one resource failure must not terminate the loop; healthy gauge polling needs its own schedule |
| Firmware integration | adapter workers/scheduler, BLE manager, Wi-Fi IO/state locks | Existing independent workers and monotonic budgets retained; short state-lock scopes are distinct from external waits; no firmware change justified by this UI increment |

## Prioritized implementation and remaining plan

H/M/L are relative to this project. Risk is regression risk, not a claim of a
measured incident. Entries 1..10 are implemented in this increment.

| Rank | Change | User impact | Reliability | Performance | Complexity/risk | State |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Cancellable, bounded HTTP and full catalog deadline | H | H | M | M/M | Implemented; stalled/fragmented/oversized/redirect fixtures |
| 2 | Separate healthy gauge cadence from adapter retry ticks | M | H | H | M/M | Implemented; scope/resume and read-count tests |
| 3 | Preserve both fault sources and partial/unavailable checks | H | H | L | M/M | Implemented; source/category and rendered fixtures |
| 4 | Jitter and recovery after unexpected service failure | M | H | M | L/M | Implemented; bounded floors/ceilings and recovery fixture |
| 5 | Remove routine global/Home connection duplication | H | M | L | L/L | Implemented; shared pill retains link status and action paths |
| 6 | Wait for bonded-owner read; pairing leads to Car | H | M | L | L/L | Implemented; rendered callback/owner-wait fixtures |
| 7 | Responsive titles, screen-reader link summary and page actions | H | M | L | L/L | Implemented; compact/large text and semantic fixtures |
| 8 | Scrollable, gauge-scoped settings/profile dialogs | H | H | L | L/M | Implemented; last cycle option/save and gauge-switch fixture |
| 9 | One transfer-progress surface on Updates | M | M | L | L/L | Implemented; global activity remains on other destinations |
| 10 | Native token/spacing and technical-copy consolidation | M | M | L | L/L | Implemented; existing token values preserved |
| 11 | Typed failure reasons replacing remaining string matching | M | H | L | M/M | Follow-up; migrate boundaries incrementally with protocol compatibility |
| 12 | Profile/persistence timing, frame/memory and energy soak | M | M | H | M/L | Requires longer device profiling; avoid speculative cache rewrites |
| 13 | Firmware/phone interruption and two-adapter matrix | H | H | M | H/M | Physical gates in recovery/dual-adapter plans; two adapters required |
| 14 | TalkBack, switch/keyboard, fold and OEM qualification | H | M | L | M/L | Semantic/large-text fixtures help; human/device checks remain |

## Shared design system

[Design-system specification](../design/design-system.md) owns the visual language;
[generated tokens](../../android/core/designsystem/src/main/java/com/lstepnio/egauge/core/designsystem/GeneratedTokens.kt)
remain the source for shared colors, type, spacing and shape. No dependency or theme
framework was introduced. Material components retain native focus, scaling and
motion behavior. Dynamic chrome does not change fixed gauge warning colors.

- `ScreenTitle`: full-width compact/large-text title, with the universal status
  underneath; expanded layouts share the row. Heading semantics and back target stay.
- `ConnectionStatusWidget`: one aggregate pill per screen; accessible description
  names phone/gauge and every required adapter. Tap exposes independent link status.
- `Panel`, `StatusCard`, `SettingsRow`, `PrimaryAction`: shared surfaces, semantic
  colors, labels and at least 48 dp targets. Choice surfaces use the control-radius token.
- `DialogContent`: scrollable text/choices inside native dialogs; actions stay in
  the native footer. Gauge identity resets settings choices rather than reusing a
  previous gauge's unsaved value.
- Home/Customize previews remain examples. Home supplies edit/next/previous screen
  reader actions; ordinary page browsing never edits or sends configuration.
- One transfer-progress surface is visible at a time. Updates owns its detailed
  progress; other destinations retain the global operation entry point. Unknown
  outcomes remain actionable and never fade like a proven success.

## Existing versus simplified workflows

| Objective | Previous friction | Implemented workflow |
| --- | --- | --- |
| Reconnect to known gauge/car | Pill, global notice and sometimes Home card repeat the same event | One pill; scoped details on tap; automatic foreground checks continue |
| Complete pairing | Bond completion exposes an extra check tap; final screen claims no adapter setup | Ordinary owner read runs automatically; Choose adapter goes directly to Car; Customize remains optional |
| Read faults from a swap | Engine summary above mixed codes; coverage toggle exposes implementation detail | One Vehicle faults card, independently labeled source sections and honest partial status; exact snapshots in Expert |
| Adjust settings after transient loss | Separate refresh message; known values may look current | Disabled edits plus Last checked settings; fresh protected read restores controls automatically |
| Change gauge while a dialog exists | Remembered brightness/rotation choice can survive identity change | Choice dialog closes when selected gauge changes |
| Follow an update | Global progress entry plus detailed Updates progress | Detailed surface on Updates; global entry elsewhere; same recovery journal/proof |
| Use large text | Long title competes with pill; choice list may exceed dialog text area | Stacked title/pill and scrollable choices with native actions |

Adapter discovery/selection, reviewed configuration send, firmware installation,
physical pairing code and destructive deletion remain deliberate user actions.
They cannot be inferred safely from continuous polling.

## Resilience architecture

The [standing recovery policy](../architecture/interaction-recovery.md) remains
mandatory. Automatic reads stop on background; explicit mutations retain their
existing transaction/journal semantics. No commit, activation, code clear or vehicle
control is automatically replayed.

| Operation | Policy | User signaling |
| --- | --- | --- |
| Gauge protected health | Independent 20 s healthy cadence; immediate scope/resume check; remembered target only | Shared pill; stale proof expires at 30 s |
| Settings and each adapter | Independent scopes/cadences; nominal retries 2/5/10/20/30 s, 80..100% jitter, cap 30 s; success resets failures | Stale settings disabled/labeled; failed child cannot hide healthy parent |
| Unexpected platform/service exception | Retire current read, record connection failure, retry with bounded jitter | Pill state; loop survives rather than silently stopping |
| Each HTTPS response | Existing byte bound plus 45 s total deadline; 15 s connect and 30 s read limits; cancellation disconnects and awaits IO-child cleanup | Online error belongs to Updates; trust failures stay explicit; an empty compatible feed never implies gauge failure |
| Catalog search | 120 s whole search budget; parent cancellation propagates through candidate iteration | Quiet automatic failure; explicit check shows one relevant failure |
| Automatic release discovery | Existing 15 min failure/6 h success schedule; foreground resume permits fresh check; signed packages only | Verified availability in pill; no unsolicited install |
| Manual update check/download | Mark busy before launch; cancel and join automatic HTTP work before handover | Deduplicated action; separate gauge/install outcome |
| Configuration/firmware mutation | Existing single lease, expected revision/hash/image and durable unknown-outcome recovery | Persistent result until authoritative proof; no blind retry |

Bounds and structured cleanup are evidence-backed mechanisms. They do not promise
recovery from every OEM stack failure, corrupt flash, controller fault or unavailable
physical link. Platform permission/security transitions still require user action.

## Implementation file map

| Changed area | Files | Purpose |
| --- | --- | --- |
| Shared UI | `core/designsystem/Components.kt`, `ui/ConnectionStatusWidget.kt` | Responsive headings, scrollable dialog content, shared shape tokens and independent-link accessibility description |
| App shell and screens | `ui/CompanionApp.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `SetupScreen.kt`, `CarScreen.kt`, `DashboardEditorScreen.kt` | One status/progress surface, scoped unsaved state, fewer routine controls, source-labelled faults and consistent gutters |
| Presentation | `ui/state/ScreenStates.kt`, `PresentationMapper.kt`, `DiagnosticPresentation.kt`, `OperationCopy.kt`, `PresentationDetails.kt` | Honest partial/unknown state and concise user-facing copy; technical snapshots stay in Expert |
| Recovery and IO | `AppModel.kt`, `HostedFirmwareRelease.kt`, `HttpDownload.kt`, `SocketIo.kt`, `connection/ForegroundConnectionController.kt`, `VehiclePollSchedule.kt`, `MainActivity.kt` | Independent health cadence, bounded jitter, request cancellation/deadlines and manual/automatic handover |
| Verification | `HttpDownloadTest.kt`, `OptimizationPolicyTest.kt`, `OptimizationUiTest.kt`, existing journey/status tests and 40 golden images | Negative paths, state invariants, rendered behavior and reviewed visual baselines |
| Standing context | `AGENTS.md`, Android README, design-system/recovery docs, AI context, current state and roadmap | Reusable standards, current evidence and ranked remaining qualification |

Android version is dev.45. Firmware remains the already installed signed dev.45.
There is no protocol/schema/capability change or new firmware publication in this increment.

## Verification and measurements

### Completed checks

- **136 JVM tests passed**, including six HTTP deadline/cancellation/size/redirect
  fixtures and seven retry/fault/error-projection policy tests. Existing socket cleanup,
  operation lease, catalog verification, projection and persistence tests remained green.
- **28 native Pixel UI tests passed**: shared connection semantics, mixed-source
  faults, compact title, 200% text dialog, gauge-scope reset, pairing completion,
  transaction banner, unified dashboard and deletion flows. These use examples or
  stored state; they do not query a vehicle. **12 navigation/editing journey tests**
  also passed after bringing the old deletion/unsupported-reading fixtures up to date.
- **76 native screenshot comparisons passed** after visually reviewing all 40
  changed images. Compact/expanded and light/dark variants retain the existing
  palette, preview labels, deliberate confirmation and native controls.
  The [native gallery](../../design/prototype/native.html) uses these checked-in images.
- Debug/test builds, Android lint, token/schema/example/document-link validation
  and whitespace checks passed. No dependency, wire format or firmware change.
- Installed **App 0.2.0-dev.45** on the existing Pixel with replacement installation.
  A physical read-only journey confirmed signed gauge dev.45 and healthy OTA state 2;
  saved/running configuration revision 46 and its exact digest remained unchanged.
  Opening, resume, Bluetooth off/on and Add-gauge cancellation needed no Connect tap.
  Brightness 100%, rotation 270°, Imperial units and cycling Off were retained.
- Phone profile, gauge-association and presentation preference files were byte-identical
  before/after replacement and the completed recovery check. No configuration,
  vehicle-control or firmware write was invoked.
- The final APK from implementation commit `2f5b95e` was installed after the phone
  returned. Its error copy keeps trust failures explicit and distinguishes an empty
  compatible feed from gauge loss. A second physical read-only opening/resume check
  passed after the owner unlocked Android: exact firmware identity, healthy OTA state 2,
  dashboard revision 46/digest and all display settings were retained. Profile,
  gauge-association and presentation preferences were again byte-identical. Resume
  confirmed fresh settings in 2.729 s; opening included time awaiting unlock and is
  excluded from performance comparisons. Original phone wake settings were restored.
- All three GitHub Foundation quality checks passed for implementation commit
  `2f5b95e`: contracts/documentation, Android build/lint/tests and firmware build.

### Measurements

| Measure | Before | After | Interpretation |
| --- | --- | --- | --- |
| Cold Activity launch, `am start -W`, three samples | 703 / 1036 / 603 ms; median 703 ms | 489 / 468 / 446 ms; median 468 ms | Small exploratory sample; cache, build and device variation prevent a causal speed claim; does not measure BLE or first telemetry |
| Protected gauge reads while adapter retry ticks every 2 s for one minute | 30 reads in the previous shared cadence | 3 with the independent 20 s schedule | Deterministic scheduler fixture, 90% fewer checks in this failure case; not measured radio airtime or battery savings |
| Opening to protected identity/settings confirmation | No comparable baseline for this increment | 13.217 s | One real Pixel/gauge run including discovery; no user connection tap |
| Resume to fresh protected settings | No comparable baseline for this increment | 3.233 s | One real Pixel/gauge run |
| Bluetooth restoration to protected confirmation/settings | No comparable baseline for this increment | 12.380 s | One off/on test; same remembered gauge and configuration |

### Verification limits and remaining gates

HTTP failures are injected with fake connection/stream resources, not a qualification
of every Android HTTPS implementation or real captive network. The first Bluetooth
UI assertion expected a removed banner; it was updated to assert the pill and
Bluetooth action, then the full physical recovery journey passed. Older Customize
journey fixtures also referenced the superseded removal menu and incorrectly treated
the now-supported transmission reading as unavailable; those tests now exercise the
visible Delete control and a genuinely unknown reading.

Physical gauge display/touch confirmation for the preceding dev.45 firmware release
remains pending from the owner. No new Jeep or simultaneous-adapter test occurred in
this increment. Longer frame/memory/energy soak, real network interruption and
TalkBack/keyboard/fold/OEM checks remain ranked qualification work. Current/target gear
semantics and TCM temperature scale still require independent vehicle references.
The coordinator and remaining string-based failure reasons should be migrated in
small steps with explicit state-contract tests, not replaced speculatively.

Private artifacts under ignored `artifacts/product-optimization/` hold phone timing,
protected readback, preference comparisons and rendered captures. No personal device
IDs, adapter captures, credentials or signing material are committed.
