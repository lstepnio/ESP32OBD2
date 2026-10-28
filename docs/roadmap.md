# Product roadmap

Updated 2026-10-27. This document orders outcomes, not individual task status.
[Current state](current-state.md) records support and devices. The
[backlog](backlog.md) is the sole prioritized task/status list.

## Delivery sequence

| Stage | Outcome | Backlog scope | Exit evidence |
| --- | --- | --- | --- |
| Now: indoor reliability | Qualify recovery and memory stability on the installed baseline | PERF-01, QUAL-12, QUAL-02, QUAL-08 | Repeated authenticated Wi-Fi lifecycles, App/radio loss and return, retained pairing/setup, explicit uncertain outcomes and measured memory/task margins; interrupted signed updates remain a separate candidate-dependent gate |
| Next parked vehicle session | Qualify the existing logical vehicle, diagnostics and shared alerts under real load | PERF-01, QUAL-03, QUAL-04, QUAL-08, QUAL-05, FEATURE-05, FEATURE-09, QUAL-11, QUAL-07 | Simultaneous ECM/child-TCM plus phone, startup absence and independent recovery, sustained freshness/resource limits, real CEL/filter/threshold transitions and daylight/night readability |
| Next capability increment | Add usable Auto orientation and qualify remaining diagnostic semantics | FEATURE-07, FEATURE-03, QUAL-06, FEATURE-08 | Physically qualified sensor/touch mapping; authoritative JSS meanings; separately qualified explicit clearing; [shared alert implementation sequence](architecture/alerts-and-diagnostics.md) and custom-PID display rules |
| Parallel command preparation | Reuse profile shortcuts for idle first, then combined ABS/TCS | FEATURE-04, FEATURE-10 | [Shared command path](architecture/vehicle-actions.md#shared-command-owner), then matched identity/guard/restoration proofs and parked release/expiry qualification before enabling vehicle writes; existing reliability gates continue |
| Beta preparation | Broader phones/adapters, accessibility and reliable distribution | QUAL-09, QUAL-10, RELEASE-01 | [Quality gates](development/quality.md), compatibility evidence and explicit release review |
| Future capability | Broader definitions, live phone telemetry, optional sensors and additional reviewed controls | FEATURE-01, FEATURE-02, FEATURE-06 | Bounded contracts, authoritative definitions and complete protected path before advertising support; fourth-button Line Lock remains deferred |

Dependencies and precise acceptance criteria belong to the backlog. There are no
implied dates or automatic release authorizations. Unavailable hardware blocks its
qualification task, not useful independent offline work.

## Distribution and custom work

The original 1.28-inch reference board retains free public development firmware.
Additional hardware integrations and firmware default to private distribution, with
custom software, interfaces and integrations as the primary service. The
[platform/RC contract](architecture/platforms-and-releases.md) defines shared-core
development, capability limits, square/virtual display extensions and target-specific
release-candidate qualification before general availability.
There is no committed standard-gauge retail launch, preorder/deposit program or
paid roadmap tier. A finished build or custom hardware can be discussed by request
and scoped separately. Customer ideas can influence priorities without payment.
Custom work can extend beyond vehicles to desktop software/displays, control panels
and standalone connected devices. These are project possibilities, not claims of
implemented eGauge support. Direct CAN integration and guided remote protocol
analysis can be scoped for higher-rate or otherwise unavailable signals. Follow
[hardware boundaries](architecture/hardware-and-transports.md): the current board
has no CAN transceiver, and signal availability/rates require validation. Remote
capture is an investigation method, not a promise that every protocol is accessible.

The DIY offer is a hands-on development project using capable, low-cost hardware
bought independently. Once configured for a working use case, core gauge readings,
saved pages and local alerts operate directly with the vehicle adapters, without
a connected phone, open App or internet service. Routine operation does not require
firmware updates. Configuration changes, updates and phone-sourced features still
need their respective tools or connections; do not imply every future integration
is autonomous.

The website presents this direction alongside evidenced product progress. Its
[maintenance policy](../website/README.md#maintenance-cadence) owns the daily,
resource-aware publication and screenshot process; it does not block product work.

## Vehicle profile expansion

Finish the Hemi-swapped Jeep's current qualification work first. The owner reports
direct access to a Volvo C30, Lexus GX470, Toyota FJ Cruiser, newer Subaru Crosstrek
and Porsche 718 S. These are likely subsequent profile candidates, not tested or
supported vehicles; exact years, variants, controllers and adapters remain to be
recorded before vehicle-specific work. Customer interest may change the order.

QUAL-10 owns capture and compatibility acceptance. Unique customer setups can use
agreed capture software and suitable hardware for guided remote data collection,
with scope and equipment arranged per project. Use the existing capture/replay
approach, but scope the procedure to the actual vehicle rather than assuming the
Wrangler's diagnostic endpoints or meanings apply. Access to a vehicle or receipt
of a capture is not profile qualification or remote-control support.

## Baseline carried into this sequence

The Android UI cleanup, shared alerts/CEL filters, revised gauge compositor and
optional child-adapter implementation are already delivered development features.
Signed App/Wi-Fi installation, live simultaneous ECM/TCM reads with independent
loss/return, and bounded indoor memory/lifecycle/interruption checks are observed
foundations for the remaining qualification, not future implementation milestones.
The internal-RAM hardening implementation/bench work is complete; its
remaining loaded/thermal acceptance is consolidated under QUAL-08. Exact installed
identities and evidence limits remain in [current state](current-state.md).

Targeted typed-failure/coordinator cleanup (CODE-01, CODE-02) accompanies a concrete
failure or feature boundary; it does not displace the reliability gates with a broad
rewrite. Production release readiness follows qualification rather than prerelease
publication alone.

## Product boundaries

Android and the autonomous gauge are the implemented platform. A basic iOS
companion and browser-over-USB core configuration are planned under PLATFORM-01
and PLATFORM-02. iOS targets essential setup/use, not Android feature parity;
some phone integrations will remain Android-only, and police/road-alert availability
may differ by platform/provider. USB configuration targets a supported desktop
browser so core gauge setup does not require a smartphone App. The existing
browser recovery tool installs firmware; it does not yet configure running gauge
settings. This scope update does not claim an iOS build, USB configuration path,
release date or live alert provider. Everyday vehicles use
one primary adapter for their available engine and transmission data. A swap's
optional second adapter changes routing within the same vehicle, never its editor,
page list or alert choices. Multiple vehicles and gauges retain independent context.

Prefer one workspace, the existing architecture and shared consumer UI. Automatically
poll known state with bounded recovery; reserve prompts for identity choices,
actionable uncertainty or required authorization. Imported definitions and schema
coverage never prove a vehicle value or authorize a control command.

## Changing the roadmap

Update this document only when outcome order or scope changes. Update task readiness,
blockers and acceptance evidence in the backlog; update implemented/installed facts
in current state. Completion removes the task from the active backlog or links its
replacement gate. Keep exact dated measurements in evidence. Follow `AGENTS.md` for
commits, pushes, reviews, releases and hardware-session authorization.
