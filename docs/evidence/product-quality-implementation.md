# Product quality implementation

> Historical evidence. Approved offline work, 2026-10-29 through 2026-10-30.
> [Current state](../current-state.md) and [backlog](../backlog.md) own current facts.

The owner approved all recommendations in the [review](product-coherence-review.md).
This record distinguishes source/automated checks from installed-device, physical
radio, vehicle-control and public-deployment qualification. The installed App dev.57 /
firmware dev.80 baseline was not changed by this work.

## Implemented

- CODE-06: one immutable per-action observation owner, scoped by gauge, vehicle,
  revision/hash and retirement epoch. Batch replies retain each receipt time;
  partial command completion cannot renew siblings. Pending/uncertain completion
  cannot enable controls. Mixed/new boots retire old receipts and delayed replies.
  App presentation and command admission consume the same freshness set.
- CODE-07: production queue-rejection accounting through existing worker health;
  native replay separates staged/committed NVS, injects open/set/commit faults and
  reboot boundaries, preserves prior committed uncertainty and prohibits replay.
  Saturation/drain covers Start, Stop and reset confirmation. Read-only pressure
  covers 100 cached UI/reservation queries, maintenance exclusion and link retirement.
- CODE-08: current state reduced from 530 to 132 lines; backlog release diaries and
  contradictory Burnout availability consolidated into next acceptance outcomes.
  [Exact earlier snapshots](status-through-dev80.md) and section anchors are retained.
  The validator limits current context to 200 lines and one Pixel/Gauge baseline.
- CODE-09: material claims carry scope/audience/evidence and reviewed source hashes.
  Image records carry actual capture bytes and relevant rendering-input hashes.
  Missing provenance/corrupted captures fail; changed claim/image inputs produce
  review items and block deployable builds until reviewed or recaptured. Metadata
  lint remains fast. CI retains its JSON review report without taking screenshots.
  Current production App/gauge assets and benefit-led Luke build story are prepared
  for the next authorized daily website batch.

The framework uses existing Python tools, Compose/LVGL captures, runbooks and CI.
No new dependencies, public capability flags, ECU command bytes or qualification
masks were introduced. It does not establish OOM immunity or broaden vehicle support.

## Scenario trace

| Scenario | Automated owner / result | Physical gate |
| --- | --- | --- |
| Partial action reply, stale sibling, pending/uncertain completion | VehicleCommandObservationsTest; VehicleCommandsUiTest confirmation expiry | Actual BLE/context transition remains device acceptance |
| Gauge/profile/hash change, reconnect, mixed boot | Fake-clock observation owner; old epoch cannot repopulate new scope | Reboot/radio behavior remains separate |
| Failed persistence commit or reboot before/after commit | Production action runtime linked to transactional NVS fake in parked_idle_runtime_test | Real flash/rail-power interruption stays QUAL-02 |
| Saturated Start/Stop/confirmation queue, then drain | Runtime admission counter and native ASan/UBSan replay | Stop and UI latency under physical load stays QUAL-08 |
| UI reads during retained recovery, maintenance and link retirement | 100-query deterministic pressure scenario, no blind control replay | Real scheduler/radio/vehicle timing remains unmeasured |
| Changed source or image/claim provenance | Website rejection/review/deployment tests | Desktop/mobile visual review and publication receipt remain distinct |

## Verification and issues found during execution

- Host suite: 197 tests plus 14 review-server tests, all passed; native policy and
  transport fixtures ran with ASan/UBSan. The first host attempt overlapped a tooling
  edit and saw two outdated download-test mocks; the settled source run passed.
- Android: 354 debug and 328 release JVM tests passed; focused action-page/command UI run passed
  32/32 with no skips. Separate current marketing capture passed. Android lint passed.
- Firmware: ordinary and parked-idle/brake/orientation candidate builds passed with
  pinned ESP-IDF 5.4.1; production normal/fallback/parked rendering/config fixtures passed.
- Independent review found that a mixed-boot poll initially retained old freshness;
  the implementation now retires those receipts and tests delayed-epoch rejection.
  A follow-up partial-batch case retains positive reboot evidence immediately,
  even if a later kind fails. The production read coordinator has direct JVM tests.
- A later JVM run stalled before the subject code in SocketIoTest's unbounded
  ServerSocket.accept. Thread evidence was retained privately. The test fixture now
  binds IPv4 loopback explicitly and bounds connect/accept/entry waits. Its production
  socket code is unchanged; the final debug/release rerun passed.
- Modal screenshot capture initially selected the activity root and produced an
  empty image despite visible semantics. The capture now uses the native dialog
  screenshot for that route, with actual image inspection before asset publication.

Final source/CI, visual checks and publication state are recorded below as completed.


## Public preview and handoff

Current production App screenshots include the action configuration modal; Gauge,
Car and Settings captures were reproduced byte-for-byte. Ten native gauge exports
now have transparent circular corners, preserving visible pixels on both README
and website. A native-pixel/alpha regression guards that conversion.

Browser review covered desktop1280×720 and mobile390×844 action cards, circular
screens and App-to-gauge workflow. DOM checks found no horizontal overflow or failed
loaded images; lazy images were distinguished from failures. No contact form was
submitted. Current claim/image review report has no unresolved items. The site was
also built with all six immutable existing public download files verified against
their manifest hashes/sizes. This changes neither the download versions nor the
signed release catalog.

Public deployment is blocked by SSH public-key rejection for the configured login
to one.majjix.com. No server files/services or live website were changed. The verified
bundle is under ignored artifacts/website-publication; it must be rebuilt from the
final pushed commit before deployment. Main merges, signed firmware publication,
installation and parked acceptance remain separately authorized delivery steps.

CI status is available on PR #128. Local results above describe the actual offline
checks, not an assertion that all remote jobs or physical scenarios passed.
