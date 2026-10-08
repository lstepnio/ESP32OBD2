# Product roadmap

Updated 2026-10-07. This document orders outcomes, not individual task status.
[Current state](current-state.md) records support and devices. The
[backlog](backlog.md) is the sole prioritized task/status list.

## Delivery sequence

| Stage | Outcome | Backlog scope | Exit evidence |
| --- | --- | --- | --- |
| Now | Preserve the working single-adapter product and qualify dev.48 recovery | QUAL-02, QUAL-03, CODE-01, CODE-02 | Confirmed physical behavior, explicit uncertain outcomes, bounded recovery without routine user taps |
| Next offline and parked sessions | Complete vehicle diagnostics and shared alerts | QUAL-05, FEATURE-05, FEATURE-03, QUAL-11, QUAL-06, QUAL-07, FEATURE-07 | Capability-gated Auto orientation; ECU-scoped MIL/faults, consistent severity/context/history/notifications, qualified explicit clearing and real alert transitions; [implementation sequence](architecture/alerts-and-diagnostics.md) |
| Next hardware expansion | Add the niche second adapter as a child of the primary vehicle | QUAL-04, QUAL-08 | Measured two adapters plus phone, independent loss/recovery and sustained resource limits |
| Beta preparation | Broader phones/adapters, accessibility and reliable distribution | QUAL-09, QUAL-10, RELEASE-01 | [Quality gates](development/quality.md), compatibility evidence and explicit release review |
| Future capability | Broader definitions, live phone telemetry, optional sensors and reviewed controls | FEATURE-01, FEATURE-02, FEATURE-04, FEATURE-06 | Bounded contracts, authoritative definitions and complete protected path before advertising support |

Dependencies and precise acceptance criteria belong to the backlog. There are no
implied dates or automatic release authorizations. Unavailable hardware blocks its
qualification task, not useful independent offline work.

## Product boundaries

Android and the autonomous gauge are active; iOS is deferred. Everyday vehicles use
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
