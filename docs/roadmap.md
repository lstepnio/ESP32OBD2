# Product roadmap

Updated 2026-10-19. This document orders outcomes, not individual task status.
[Current state](current-state.md) records support and devices. The
[backlog](backlog.md) is the sole prioritized task/status list.

## Delivery sequence

| Stage | Outcome | Backlog scope | Exit evidence |
| --- | --- | --- | --- |
| Now: indoor reliability | Qualify recovery and memory stability on the installed baseline | QUAL-12, QUAL-02, QUAL-08 | Repeated authenticated Wi-Fi lifecycles, App/radio loss and return, retained pairing/setup, explicit uncertain outcomes and measured memory/task margins; interrupted signed updates remain a separate candidate-dependent gate |
| Next parked vehicle session | Qualify the existing logical vehicle, diagnostics and shared alerts under real load | QUAL-03, QUAL-04, QUAL-08, QUAL-05, FEATURE-05, FEATURE-09, QUAL-11, QUAL-07 | Simultaneous ECM/child-TCM plus phone, startup absence and independent recovery, sustained freshness/resource limits, real CEL/filter/threshold transitions and daylight/night readability |
| Next capability increment | Add usable Auto orientation and qualify remaining diagnostic semantics | FEATURE-07, FEATURE-03, QUAL-06, FEATURE-08 | Physically qualified sensor/touch mapping; authoritative JSS meanings; separately qualified explicit clearing; [shared alert implementation sequence](architecture/alerts-and-diagnostics.md) and custom-PID display rules |
| Parallel command preparation | Reuse profile shortcuts for temporary high idle during air-up and winching | FEATURE-04 | [Controller procedure and bounded offline slice](architecture/vehicle-actions.md#first-vehicle-command-temporary-high-idle), then matching-PCM parked release/expiry qualification before enabling vehicle writes; existing reliability gates continue |
| Beta preparation | Broader phones/adapters, accessibility and reliable distribution | QUAL-09, QUAL-10, RELEASE-01 | [Quality gates](development/quality.md), compatibility evidence and explicit release review |
| Future capability | Broader definitions, live phone telemetry, optional sensors and additional reviewed controls | FEATURE-01, FEATURE-02, FEATURE-06 | Bounded contracts, authoritative definitions and complete protected path before advertising support; ABS/ESC remains outside the first high-idle increment |

Dependencies and precise acceptance criteria belong to the backlog. There are no
implied dates or automatic release authorizations. Unavailable hardware blocks its
qualification task, not useful independent offline work.

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
