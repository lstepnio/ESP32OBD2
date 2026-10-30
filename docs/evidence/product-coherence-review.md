# Product coherence review

> Historical evidence. Review of source `bf397d2`, 2026-10-29 (America/Denver).
> [Current state](../current-state.md) owns subsequent facts;
> [backlog](../backlog.md) owns remaining work.

## Scope and conclusion

Reviewed current delivery source, firmware/control/recovery boundaries, Android
command presentation, CI/documentation enforcement, website builder/content and
public desktop presentation. This was a focused architectural review, not a complete
line-by-line audit or a new hardware/reliability measurement. No phone, gauge or
vehicle was operated. The clean action-UI worktree was reviewed; unrelated dirty
work in the original checkout was preserved.

The project has substantial working foundations: bounded operations, source/session
fences, durable uncertainty, signed release verification, production renderers,
four firmware CI variants and Android interaction tests. There is no evidence here
that the whole platform needs a rewrite. There is evidence that cross-layer state,
qualification and current-status discipline need stronger integration checks.

## Ranked findings and proposed changes

| Impact | Finding and source | Change | Effort / risk |
| --- | --- | --- | --- |
| High | `android/app/src/main/java/com/lstepnio/egauge/AppModel.kt:238-241,288-300` uses one observation timestamp for the command map; updating one kind refreshes retained siblings. `ui/state/PresentationMapper.kt:789` consumes that shared freshness. UI fixtures supply `commandsFresh` directly. | CODE-06: timestamp each kind or invalidate/re-read a complete snapshot; fake-clock coordinator regression across gauge/session changes. Firmware admission remains independent. | S / medium, user-visible availability |
| High | `firmware/gauge/tests/parked_idle_runtime_test.c:84-105` writes immediately in the set fake and always succeeds commit. Production `src/vehicle_commands_runtime.c:143-155` uses separate set/commit. | CODE-07: staged versus durable fake storage, independent open/set/commit failures and reboot replay. Retain the separate physical interruption gate. | S / low for tests |
| High | Runtime queue is bounded, but `firmware/gauge/main/src/vehicle_commands_runtime.c:247,520` returns failed queue admission without existing health drop accounting. Complete loaded recovery budgets remain only partly defined in `docs/development/dual-adapter-recovery.md`. | CODE-07 + QUAL-08: count rejected admission; saturate/drain in a fixture; measure end-to-end UI/source/recovery latency under combined load before choosing enforceable budgets. | M / medium for instrumentation; hardware required for qualification |
| High | `docs/current-state.md` has 530 lines with current dev.80 at the top but old installed/future claims later; `docs/backlog.md` says Burnout paused while FEATURE-04 retains its four-page availability narrative. `docs/roadmap.md` still includes it in parallel delivery. | CODE-08: replace chronological status with compact current facts and qualification links, archive exact history once, preserve inbound anchors. No inferred completion of old gates. | M / low runtime risk, medium evidence-loss risk |
| Medium | `docs/decisions/README.md` assigns ADR-019 twice. `tools/documentation_policy.py` omitted PERF, UX and QUALITY task families from roadmap/backlog checks. | Fixed in this framework change: preserve smoothing ADR-019, assign adapter roles ADR-020; validate unique decisions and all existing task families, with rejection tests. | S / low |
| Medium | `tools/build_website.py` checks source file/image existence and hashes output, but does not establish that a claim matches a capability/audience or that image inputs remain equivalent. `website/index.html` also has manually repeated ABS availability beside `content.json`. | CODE-09: extend current content/provenance fields with scoped claim/evidence and relevant input hashes; derive repeated availability copy once. Fail missing provenance, flag changed source for semantic review. | M / low runtime risk |
| Medium | README and website have strong product visuals and a footer credit, but no concise recurring story connecting Luke's design decisions to demonstrated outcomes. | Editorial direction added to `website/README.md`: benefit, real visual, design decision, observed result, next step. Apply during the next eligible batch. | S / low; no claims invented |

The freshness defect is demonstrated in source. The persistence and queue findings
are verification/observability gaps, not demonstrated flash corruption, missed Stop
or deadlock. Loaded fragility cannot be attributed to OOM from this review alone.

## Website and delivery observations

The public desktop hero was inspected in the browser: a clear two-column layout,
round gauge, benefit-led heading, direct DIY action and restrained visual style.
This is a foundation to retain. This review did not requalify mobile layout or
accessibility. The live [site manifest](https://egauge.majjix.com/site-manifest.json)
reported source `482b7960004c7fc0a2c733c692b22fdf879f5258`, reviewed 2026-10-29.
A review date or manifest hash establishes provenance, not semantic freshness.

`docs/assets/app/marketing-provenance.json` identifies source `083b001`, runtime
equivalence to `62d1e4b`, production composables and synthetic input. Those captures
may remain valid where unchanged; their age alone is not a reason to recapture.
Changed action screens need their own representative production captures and
relevant-source comparison before inclusion.

The source's current delivery record names App dev.57 and firmware dev.80; PR #127
is stacked on #126. This audit did not reinstall or reread device identity. No
main merge, release publication or website deployment is established by this work.
The public statement that ABS/TCS execution is disabled needs an explicit audience:
ordinary public qualification and an owner-only installed candidate are different
facts. Do not broaden a public claim to match an unqualified private test.

## Framework and tradeoff

The [delivery loop](../documentation.md#product-quality-and-public-story) is the
maintained framework, and [website editorial policy](../../website/README.md#brand-and-editorial-direction)
owns the brand voice. Existing architecture/runbooks retain detailed budgets and
procedures. Backlog CODE-06 through CODE-09 hold the follow-up, rather than creating
another competing implementation plan here.

The strongest objection is that more process could slow an experimental project.
The response is to make checks proportional: automate inexpensive consistency,
reuse production captures, test affected transitions, keep slow hardware/publication
separate, and defer unrelated refactoring. A dashboard that merely counts green
checks would add noise. The useful scorecard is the actual failed scenario, exact
baseline, recovery measurements and unresolved qualification.

Confidence is high for the source/document contradictions and medium for the
broader reliability diagnosis. Combined-load measurements and parked controller
restoration can change the priorities. The owner's personal brand is best served
by a distinctive product that visibly improves, with confident, specific copy and
honest limits near the relevant feature.


## Framework change verification

No product runtime, device state or live website content changed. Fourteen focused
documentation-policy tests passed, including duplicate ADRs and previously unchecked
task families. Ruff lint/format passed for both changed Python files. The existing
validator passed 152 document link sets plus contracts/token parity; the website
metadata build passed nine image placements and 28 assets. These are offline checks,
not new firmware, App or vehicle qualification.
