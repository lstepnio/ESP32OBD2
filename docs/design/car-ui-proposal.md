# Car experience and hidden fault codes

**Proposed, not implemented. Review 1, 2026-10-28.** This is the approval-stage plan for the whole Car section, with emphasis on alerts, vehicle faults and hidden-code consistency. It changes no installed App, firmware, vehicle commands or capability gates.

The review gallery is docs/design/gauge-ui-proposal/car-pages/index.html. Its interactive phone uses example data. The PNGs and walkthrough come from the same prototype.html and screens.js. The local render_plan.py produces the gallery's HTML view of this document; edit this source, not the generated plan.

Run the local review with `python3 docs/design/gauge-ui-proposal/review_server.py --review car-pages --port 8771`, then open http://127.0.0.1:8771/docs/design/gauge-ui-proposal/car-pages/index.html. Feedback stays in ignored artifacts/gauge-ui-review/car-pages-review-v1.json; never copy test feedback into this file. Regenerate the plan view with `python3 docs/design/gauge-ui-proposal/car-pages/render_plan.py` after editing this document.

## Decision

Replace the overlapping Vehicle health and recent Alerts panels with one useful attention list. Each row opens its explanation directly. History, Alert settings and Vehicle actions remain one tap from Car. The universal connection pill owns routine discovery and recovery.

Hide matching codes throughout eGauge presentation, including old history, recorded context, notifications, badges, accessibility text and ordinary reports. Keep the hidden-code management list in Advanced. Hiding is reversible and does not clear a vehicle code or change its dashboard lamp.

The installed firmware may still differ from this proposal. Mainline owns the separate CEL startup and Air up recovery release. This design must be integrated after that work, not used to overwrite it.

## What is wrong today

- ui/car/CarScreen.kt and ui/AlertPresentation.kt present diagnostic summaries and alert episodes as separate blocks, making one underlying fault look like several things to investigate.
- AlertPresentation.kt hides matching recent episodes only while they are current. Full history uses raw entries, and details render recorded codes directly. Filtering therefore appears to stop working after an episode ages or the vehicle disconnects.
- DiagnosticCodeFilters.filtersAlert requires a live diagnostic snapshot and matching revision. AppModel.effectiveDiagnosticFilters depends on the active confirmed document. That is useful for gauge truth, but insufficient as the visibility policy for historical App content.
- ui/state/DiagnosticPresentation.kt emits “Only filtered codes”; whole-car mapping can fall through to a waiting message. Neither should become normal user-facing work when there is no visible issue.
- AlertReportExporter.kt exports raw event context. Current normal sharing can reveal codes hidden elsewhere.
- The old architecture intentionally retained codes in visible diagnostics/history. This proposal changes that product contract, rather than fixing only one list.

Android paths above are relative to android/app/src/main/java/com/lstepnio/egauge/. The gauge already has src/diagnostic_filter.c and a separate filtered presentation in firmware/gauge/main/main.c. A physical gauge leak has not been established by this audit; firmware needs parity verification, not an assumed rewrite.

## Everyday structure

**Car header:** selected vehicle, the existing universal connection pill, and a Car settings control. Switching the viewed car does not silently assign it to a gauge.

**Needs attention:** only visible issues, with a specific title, one useful detail line and one direct destination. A coolant alert says what crossed its limit. A fault shows a readable meaning with the exact code and Engine or Transmission beneath it. Up to three rows appear on Car; “View all” opens the current list when needed.

**Quiet state:** a compact “No active alerts” with an accurate check age, only after usable checks. A complete set containing only hidden codes is visually identical to a zero-visible-code result. This is not a diagnosis of vehicle health.

**Routine startup:** no health card, fault placeholder, CEL question mark or “checks incomplete” task. The connection pill owns waiting. Missing evidence cannot produce a success state.

**Utilities:** Vehicle actions, Alert settings and History. Adapter management, car naming and gauge assignment move to Car settings. The optional transmission adapter remains an Advanced child of the primary vehicle connection.

**Alerts:** Current and History are two views of the same scoped presentation. Current contains actionable findings; History is a dated timeline. Rules, notification permission, exports and raw diagnostics do not compete with current issues.

**Fault detail:** meaning first, code/controller second, useful time context and a short explanation from the existing lookup catalog. Unknown manufacturer codes say a description is unavailable. Do not invent a repair, map a code to the wrong vehicle, or add AI lookup as a prerequisite.

**Threshold detail:** value at the event, configured limit and a small useful context set. The phone does not yet offer general live companion telemetry, so numbers are labelled “At alert.” “Got it” acknowledges the episode; it does not resolve it. “Snooze 5 min” appears only for eligible noncritical alerts. Critical alerts retain their existing policy.

## State and attention rules

- Never duplicate a current fault as both a fault-code row and a generic MIL episode. Merge diagnostic evidence into the row; retain an independent unknown warning only when current evidence cannot be attributed to known codes.
- Deduplicate a code across Stored/Pending/Permanent categories within one controller. Do not merge identical codes from different vehicles, controllers or independent gauge observations.
- Use severity from the existing alert policy. A code description alone is not evidence to upgrade a fault to critical.
- A new critical issue appears first. Other active threshold alerts precede recorded noncritical codes. Keep stable ordering within a severity group; normal refreshes must not shuffle rows under a user's finger.
- In partial connectivity, show usable issues from the working source. The pill communicates that the vehicle is partly connected; expanded connection details name the affected child. Do not create a second workspace or persistent incomplete-check lecture.
- When a recorded event is stale or disconnected, retain its time and label its current status unavailable. Never change it to “Returned to normal” without fresh resolving evidence.
- With no current evidence, remove current health claims. Previous activity is labelled “Last drive” and links to History.
- A confirmed unexplained MIL remains a real warning. Do not infer that it belongs to a hidden code. Unresolved startup data stays quiet under the current startup policy.
- “View all” and counts use the visible set. Zero rows means no empty attention card, phantom badge or “hidden items” footer.
- Acknowledging does not remove an active problem. Hiding a code does not alter the lifecycle of an unrelated threshold or provider alert.
- Back returns to the originating list, tab and scroll position. Changing cars dismisses old-car details and cancels old-car edits.

## Hidden means hidden

Apply one vehicle/controller/code visibility policy before every consumer renders:

- Car attention rows and summaries, current alerts, full history and empty states.
- Recorded codes nested in threshold or provider context.
- Normal and Expert diagnostic code lists, semantic labels and badge/count summaries.
- Global banners, Android notifications, notification actions and deep-link destinations.
- Ordinary saved/shared reports, including transition text and nested context.
- Gauge CEL/wrench indicators, counts, shared alert banners and future diagnostic inspection.

The sole deliberate App exception is **Advanced > Hidden fault codes**, where the owner manages the exact rules. No ordinary page links to a hidden count. Do not add a “Show hidden faults” toggle to everyday lists.

Existing exact-code validation and the sixteen-rule limit stay. Scope is the selected vehicle plus Engine or Transmission, independent of which adapter carries that controller. The four current TCM rules do not automatically hide matching Engine codes.

For a mixed event, remove hidden code strings and rebuild the visible title, count and context from the remainder. Omit a diagnostic-only episode proven to consist solely of hidden faults. A threshold event remains even if all incidental fault-code context is hidden.

Old generic MIL episodes without enough immutable code context cannot be attributed retroactively. They may remain as a dated generic vehicle-warning record, with no hidden code string or count. This is an evidence limitation, not an invitation to display suppressed code content.

When hiding takes effect, reconcile already-posted notifications. Cancel a hidden-only notification or update a mixed one with the visible remainder. A deep link to an omitted episode opens that vehicle's Alerts view. Removing a rule can reveal history again but must not replay old sounds, vibrations or notifications.

Keep raw observations and immutable event context internally. Do not delete or rewrite evidence to simplify the screen. Developer capture files can contain raw evidence; the normal App UI and report path must not expose it through a filter bypass.

## Saving and multiple gauges

Use **saved vehicle intent** for App visibility. Editing an unsaved field does nothing; saving the hidden-code rule updates that vehicle's App presentation, including offline history, immediately. This is a proposed change from the current live-confirmed-document-only App filter.

Gauge behavior follows its last acknowledged configuration. A connected compatible gauge uses the existing serialized review/send transaction. “Hide and send to gauge” is one user action when the change is isolated and the target is unambiguous.

If sending would apply unrelated staged dashboard changes, show the existing setup review instead of silently committing them. Do not introduce a partial configuration writer or replay uncertain writes.

An offline gauge remains pending. Advanced shows per-target “Applied” or “Offline · Change waiting”; normal App surfaces use the existing generic unsent-setup indication without listing hidden codes. Never promise that an unreachable gauge changed.

Persist App visibility through the existing vehicle/profile storage owner. Retain gauge-specific acknowledged revision/hash metadata through existing association/configuration owners. Resolve historical presentation by the event's vehicle, not whichever car is selected now. Avoid a second alert repository or sync service.

## Alert setup

Use one existing alert definition and editor path for Car and Gauge customization. List readable names and compact summaries, such as “Coolant temperature · Above 230 °F.” Add alert searches the supported reading catalog, including Transmission and future custom readings.

The simple editor exposes enable/disable, direction and limit. Preserve existing timing, delay, reset and unit values; disclose them under “Alert behavior” without resetting them on entry/exit. Use the existing full editor where a richer rule cannot be represented faithfully.

One Save initiates the existing transaction when connected. Offline, the control is “Save on phone” and the result remains pending. A spinner or timeout never becomes “Saved on gauge.” Failed or uncertain sends retain the edit and use existing reconciliation.

Keep notification permission and background monitoring separate from thresholds. The notification switch must reflect Android permission and actual monitoring state; denied permission produces one actionable instruction there, not a warning on every Car visit.

## Visual and interaction rules

Reuse existing eGauge dark/light tokens, universal connection widget, 28 dp cards, 16 sp body text, 14 sp labels and 48 dp minimum touch targets. Green indicates interactive/selected state; amber/red preserve alert semantics. Never rely on color alone.

Compact screens use current bottom navigation. At existing rail/pane breakpoints, retain bounded content width and optionally show selected detail beside the list. Large text returns to a scrolling column. Respect keyboard, navigation bars, predictive Back and focus order.

Use full-screen lists and editors for multi-step work. Avoid sheets opening more sheets. Short contextual confirmation can use one sheet. Do not require a generic Details step before useful content.

Accessible names include title, visible code/source, recorded time and action. Hidden codes must be absent from the semantics tree as well as pixels. Live regions announce meaningful new attention, not every polling refresh.

No new framework or dependency is proposed. HTML/CSS examples use the existing Compose language; they are not proof of implemented Compose, TalkBack or hardware behavior.

Design references: [Android grids and units](https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units) and [Android accessibility touch targets](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views?hl=en). These support the existing grid and minimum targets; product decisions come from the source audit and owner goals.

## Review coverage

The gallery contains 27 independently reviewable screens: active/quiet Car, startup, partial source loss, offline history, all-hidden/mixed-hidden results, full current list, known/unknown fault detail, active/stale threshold detail, populated/empty history, alert settings/editor/catalog, hidden-code editor and multi-gauge pending state, car selection/settings, phone notification, circular-gauge parity, light theme and 200% detail/editor text.

The walkthrough covers direct issue inspection, editing a coolant limit and hiding a code. Surrounding copy identifies example data; prototype actions never reach devices. Static examples preserve the state being approved.

Each screen can be approved or receive changes and a comment. Comments auto-save on pause/blur, with browser drafts retained on failure. Submission retains a full snapshot. Review assets are frozen by content hash, served on loopback, and saved to private artifact storage. Cross-window revision conflicts must not overwrite newer feedback.

Unchanged image approvals can carry to a new round using the existing server seed mechanism. Mark a screen revised when its interaction or meaning changes even if pixels remain identical. Do not infer approval from silence or reuse previous action-page approvals for this Car design.

## Implementation sequence after approval

**1. Shared visible projection. Effort M, risk medium.** Introduce one App projection for current issues, history, nested context, counts and status. Resolve saved vehicle visibility independently of live-cache freshness. Keep raw AlertRepository entries intact. Replace scattered predicates; test historical, offline, mixed and multi-vehicle attribution first.

**2. Complete filtering coverage. Effort M, risk medium.** Route banners, notifications, notification taps, Expert fault views and normal exports through the projection. Reconcile notifications on policy changes. Verify gauge projection and presentation consumers with equivalent cases. Extend firmware only for a demonstrated gap; retain existing wire/schema if sufficient.

**3. Car navigation and content. Effort M, risk low to medium.** Replace redundant panels with overview/list/detail. Reuse Car/profile/adapter ownership and approved action controls. Delete obsolete UI components and strings only after references are gone.

**4. Alert setup and policy application. Effort M, risk medium.** Reuse editor, storage and serialized transactions. Add simple entry points and per-gauge pending acknowledgements. Guard unrelated staged setup writes and offline success claims.

**5. Verification and delivery. Effort M, risk medium.** Run unit/fixture, managed-emulator and targeted indoor checks below. After CI and separate release authorization, publish/install through App/Wi-Fi. Live ECU/filter checks remain a parked Jeep gate. Update canonical architecture/design docs with approved behavior in the implementation commits.

This design branch contains proposal assets, documentation and local review-tool support only. It does not merge, publish, install, change filters or clear codes.

## Acceptance

- One tap from Car to a displayed issue; one tap to History or Alert settings.
- Hidden-only fixtures produce no code, icon, count, accessibility text, historical row or normal-export residue. Mixed fixtures preserve unrelated findings.
- Same Engine code stays visible when only Transmission is hidden. Vehicle/gauge scopes survive restart and switching cars.
- Saved rules update offline history. Unhiding does not replay notifications. Notification taps cannot bypass visibility.
- No current health claim from empty, stale, partial or disconnected evidence. No unresolved startup icon. Confirmed unfiltered warnings remain visible.
- Acknowledgement, snooze, source loss and hiding do not falsely resolve active alerts.
- Rules work for all supported readings, including TCM; units, validation, hysteresis and timing stay correct.
- Save/timeout/reconnect/unknown-write tests preserve transactions and unrelated pending setup.
- Managed UI checks cover small screens, keyboard, 200% text, light/dark, long labels, scroll/Back and accessibility. Prototype screenshots alone do not satisfy these.
- Gauge fixtures cover complete hidden-only, mixed, incomplete coverage, stale source and independent ECM/TCM recovery. No extra heap-backed list or polling loop is introduced for visual simplification.
- Physical checks separately verify current/hidden display, notification removal, reconnect and per-gauge applied state. Code clearing is not part of acceptance.

## Documentation and website

After approval, consolidate accepted rules into the shared alert architecture and design system; retain this proposal as dated design evidence. Add focused source-map/feature-index pointers, not duplicate operating instructions.

Website impact: review only, no new public capability claim or immediate publication. Capture implemented Car UI and circular gauge states after qualification, then update at the next permitted daily maintenance window.

## Proposal verification

Host lint, documentation policy tests and fourteen review-server tests pass. Browser checks cover all 27 examples at phone width, saved rule edits without altering recorded alert limits, hidden-code removal from Car/history/deep links, and feedback autosave/submission read back in a separate browser context. Desktop and mobile review layouts were inspected. These checks qualify the proposal and review tooling only, not App or firmware implementation.
