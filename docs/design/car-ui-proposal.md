# Approved Car experience design record

Approved by the owner on 2026-10-28: “the review looks good, let's go ahead and implement.”
The 27 screen examples and interactive walkthrough remain in
[the review gallery](gauge-ui-proposal/car-pages/index.html). Source implementation
is verified offline; installation and physical qualification are separate.

## Decision and rationale

One Needs attention list replaces overlapping Vehicle health and recent Alerts
panels. Direct detail removes the generic controller/fault landing step. History,
Alert settings and Vehicle actions remain direct utilities; Advanced owns hidden
codes and the optional child adapter. Connection/retry status stays in the universal
pill. The model remains one logical vehicle regardless of its physical adapters.

Hidden means hidden across App and gauge presentation. The sole UI exception is
rule management in Advanced. Keep raw immutable evidence internally; hiding must
be reversible and never clear vehicle codes or fabricate resolution. Saved vehicle
intent governs App/offline visibility; each gauge retains its last acknowledged
configuration. This deliberately replaces the previous live-confirmed-only App
filter and raw-history/normal-export exceptions.

Current authoritative behavior lives in the [shared alert contract](../architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters),
[design system](design-system.md#current-component-and-messaging-rules) and
[Android storage contract](../architecture/android-runtime.md#storage-and-recovery).
This record preserves review context rather than duplicating operating instructions.

## Audit that motivated the change

At review time, ui/car/CarScreen.kt and ui/AlertPresentation.kt exposed overlapping
health/episode summaries. Recent diagnostic events were filtered only while current;
history, recorded context, Expert raw lists and normal exports exposed hidden codes.
DiagnosticCodeFilters.filtersAlert and AppModel.effectiveDiagnosticFilters depended
on live revision-matched evidence, so the same saved policy appeared inconsistent
after disconnection. The gauge already had a separate bounded filtered projection;
a physical gauge bypass was not established.

## Approved coverage

The review contains active/quiet Car, startup, partial source loss, offline history,
all-hidden and mixed-hidden results, full current list, known/unknown fault detail,
active/stale threshold detail, populated/empty history, settings/editor/catalog,
hidden-code and multi-gauge pending management, vehicle selection/settings,
notification, circular-gauge parity, light theme and 200% detail/editor examples.

The interactive flows cover direct issue inspection, coolant-limit editing and
hiding a code. Every screen accepts approval or specific feedback. Comments autosave
after a pause/blur; browser drafts survive failures, server storage survives browser
changes, and revision checks reject stale-window overwrites. Review assets are frozen
by hash. Unchanged approvals can carry to later rounds; changed interactions need a
new decision. No prior action-page approval was inferred for these Car screens.

Run `python3 docs/design/gauge-ui-proposal/review_server.py --review car-pages --port 8771`
and open http://127.0.0.1:8771/docs/design/gauge-ui-proposal/car-pages/index.html.
Private feedback is artifacts/gauge-ui-review/car-pages-review-v1.json. Never seed it
with test feedback. After editing this record, regenerate its browser view using
`python3 docs/design/gauge-ui-proposal/car-pages/render_plan.py`.

## Implementation trace and remaining gates

AlertVisibility owns copied current/history/context/report projections. Existing
notification, SQLite, profile and transaction owners remain. The Car navigation and
shared alert editor retain existing styling, canonical units, severity, timing and
validation. Phone schema 19 retains disabled drafts; the existing gauge schema stays 2.
No firmware worker, allocation or new wire field is needed for the audited parity.

The plan requires unit/migration/projection tests, managed-emulator interactions,
notification reconciliation and real ZIP redaction before delivery. Tests must prove
raw evidence unchanged, controller/vehicle isolation, silent unhide, mixed remainder,
no startup all-clear, unavailable rather than resolved, and retained navigation.
Device checks separately verify actual gauge presentation, per-gauge acknowledged
application, reconnect and live filtered/unfiltered/partial ECM/TCM behavior. The
inherited dev.72 MIL-on/count-zero policy is a separate labelled-capture question.
ECU clearing is outside this acceptance run.

Website impact: capture implemented App and circular gauge states after verification;
update the public website in its normal daily window after qualification. No immediate
publication or unqualified capability claim follows from this approval.

Executed source/managed checks and the captured Car screen are recorded in the
[dated implementation evidence](../evidence/car-ux-review.md#2026-10-28-approved-car-and-hidden-code-implementation).
