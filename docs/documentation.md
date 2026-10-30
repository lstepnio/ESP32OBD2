# Documentation ownership and maintenance

This policy keeps AI and human contributors on the same current context. Root
[AGENTS.md](../AGENTS.md) governs work; [AI context](ai-context.md) routes tasks.
The [documentation map](documentation-map.json) is validated by `tools/validate.py`.

## Authority

| Question | Authoritative location | Update when |
| --- | --- | --- |
| How should an agent work? | `AGENTS.md` | Owner/project operating rules change |
| Where is the code and what should I read? | `docs/ai-context.md`, documentation map | Source ownership or task routing changes |
| What is implemented, installed and actually observed? | `docs/current-state.md` | Source compatibility, release/device state or qualification changes |
| What should happen next and in what order? | `docs/roadmap.md` | Outcome order/scope changes |
| Which tasks are ready, blocked or deferred? | `docs/backlog.md` | Task status, dependency or acceptance changes |
| Why was an architectural choice made? | `docs/decisions/` | A major tradeoff or revisit condition changes |
| How does the current implementation work? | `docs/architecture/` | Runtime ownership, persistence or recovery changes |
| Which bytes/schema are accepted? | `docs/protocol/`, `contracts/`, codecs | Wire or compatibility changes; explicitly separate proposed contracts |
| What should the consumer UI do? | `docs/design/design-system.md`, tokens | Shared UI rules or components change |
| How do I build, release or run a controlled session? | `docs/development/` | A maintained procedure changes |
| How does the public website stay current? | `website/README.md`, `website/content.json` source authorities | Feature/UI changes, accepted owner priorities and the next eligible daily publication |
| What was measured in a particular session? | `docs/evidence/` | New dated evidence, never guessed updates to old observations |
| What did external research establish? | `docs/vehicles/`, sources, data scope | Provenance or candidate definition changes |

Current source/codec behavior resolves an implementation mismatch; it does not
establish hardware qualification. The latest authorized observation resolves device
state; do not infer it from a version file. A proposal, historical pending note or
backlog item cannot grant authorization or establish supported capability.

## Keep the context small

Read instructions, AI context and current state first. Read only the selected task's
backlog row, source, tests and maintained contract next. Search evidence when exact
measurements, past failures or migration details are needed. Do not make every new
session reread chronological rollout reports, screenshots or imported catalogs.

Use links rather than copying state into README, architecture and several reports.
No separate changelog, TODO list or ranked plan should compete with the backlog.
README indexes the product and navigation; it does not repeat release counters.
Architecture describes ownership; release/test numbers belong in evidence/status.

## Feature completion rule

Documentation is part of every feature, including experiments, refinements and
removals. Humans and AI must be able to understand it from the repository without
chat history. Update the canonical contract in the same PR as the implementation;
do not defer it to release day or leave the details only in a PR/conversation.

For the affected feature, cover the meaningful details:

- Purpose, user-visible behavior, defaults and how to configure/use it.
- Owning modules and the App/firmware/transport/control path where relevant.
- Persistence, validation, capability negotiation and migration/compatibility.
- Loading, missing/stale data, failure, cancellation and recovery behavior.
- Resource/timing limits, design tradeoffs and the reason for non-obvious rules.
- Relevant tests/check commands, evidence and remaining physical qualification.
- Experimental boundaries, enable/remove path and known limitations, when applicable.

Extend an existing topic owner when possible. Give substantial new features one
focused contract and link it from the [feature index](features.md) and [source map](ai-context.md).
Classify new documents in the documentation map. Update status/backlog/roadmap only
for changed facts, and retire duplicate plans after moving enduring rules and open
acceptance. Removal includes cleaning incoming references and recording any migration
or compatibility consequence. A minor change can be documented with a short update;
this is not a requirement for a new document per commit.

PRs identify the documentation updated, or explain why a change has no feature or
contract impact. Existing CI validates document inventory, links and source paths;
review checks semantic completeness. A green link check alone cannot establish that
the prose matches behavior. Public website review follows its existing daily policy
and is separate from technical feature documentation.

## Change checklist

1. Verify current Git/worktree and the affected source/contract.
2. Update only the authoritative document for each changed fact.
3. For implementation, record behavior, compatibility and focused verification.
4. For physical work, record exact App/image/health identity and distinguish phone,
   gauge, serial and vehicle observations. Keep private identifiers/artifacts out of Git.
5. Update backlog readiness/acceptance and current state if either changed. Review
   the public website impact under its [maintenance cadence](../website/README.md#maintenance-cadence).
6. Preserve dated evidence and label old pending instructions historical. Retire a
   completed duplicate plan after moving its remaining acceptance requirements.
7. Check local file/heading links, documentation-map coverage, source paths and stable
   backlog IDs with the validator; run `git diff --check`, commit and push.

A documentation-only change does not need a phone, firmware install or full runtime
regression suite. Verification should match the changed boundary.

## Document conventions

- Active guidance starts with its purpose and scope; add an updated date to status,
  task and procedure snapshots. Do not add dates that imply new physical verification.
- Proposed protocol/control sections say **Proposed, not implemented** near their start.
  Implemented development contracts name capability negotiation and public gates.
- Historical reports live in evidence with the standard historical banner and links
  to current state/backlog. Their old image identities and measured numbers remain exact.
- Use stable task IDs in the backlog; references in the roadmap and PRs use those IDs.
  Describe acceptance, evidence type and blockers before marking completion.
- Use repository-relative source paths and working local Markdown links. Preserve
  heading anchors when linking a subsection; update incoming links when moving files.
- Use concise tables for status/ownership and small diagrams for boundaries. Avoid
  prose that mixes current fact, future intention and old experiment without labels.
- New maintained documents must be classified in the map; new evidence uses the
  evidence directory. The validator rejects unclassified authored documents.


## Product quality and public story

This is the delivery framework for firmware, App, GitHub, documentation and website.
It coordinates existing topic owners rather than introducing a second architecture,
feature registry or release system. The [review](evidence/product-coherence-review.md)
records why it is needed; the backlog owns implementation work.

### One feature, separate evidence states

Every changed feature has one existing backlog ID and canonical contract. Record
source/PR, automated evidence, installed App/FW pair, physical observations and
remaining qualification separately. These are independent dimensions: an installed
owner candidate is not automatically merged, generally available or vehicle-qualified.
Never reduce them to a single Done badge. Public availability must name its audience
and supported hardware/profile, especially for owner-only control candidates.

Keep current-state summaries under 200 lines with one Pixel/Gauge baseline table,
focused on the current baseline, active limitations
and evidence links. Replace superseded statements in place; move historical
narrative to dated evidence without losing measurements or inbound anchors. The
backlog row describes the next acceptance outcome, not the feature's release diary.

### Delivery loop

| Boundary | Required result | Existing owner |
| --- | --- | --- |
| Frame | User outcome, scope, affected surfaces, failure cases and observable acceptance | Backlog row + feature contract |
| Implement | One authoritative state owner; bounded resources/recovery; compatibility preserved | Source map + architecture contracts |
| Exercise | Reproduce the reported failure; test the transition and recovery, not just the final happy screen | Quality guide + feature tests |
| Review experience | Matching meanings, clear next action, minimal steps and production-rendered states | Design system + approved review |
| Deliver | Exact source/CI/artifacts, one device owner, preserved setup, exact healthy identity | Release runbook + dated delivery evidence |
| Tell the story | Benefit-led copy, current visuals, scoped claims and useful next steps | Website content + daily publication receipt |

A failed boundary stays visible in the backlog. A retry is another attempt, not a
replacement for the failed evidence. Delivery does not require recapturing unaffected
screens or running the full hardware matrix after a copy-only edit.

### Reliability that can be measured

Use the [memory qualification](development/memory-qualification.md),
[dual-adapter matrix](development/dual-adapter-recovery.md) and
[interaction contract](architecture/interaction-recovery.md). Do not introduce new
polling owners, watchdog workarounds or arbitrary global timeout increases to make
a failure disappear. Map each field incident to a regression scenario and an
independent physical acceptance check before calling it resolved.

For affected resource boundaries, record queue admission/rejection, time to cleanup,
reconnect latency, sample age, task progress, minimum/largest internal heap and stack
margin. Compare equivalent workload phases and exact App/FW/settings. Use existing
contract budgets; a new budget needs a measured baseline and documented rationale.
Report percentiles and worst case only with sample count, workload and failures.
No generic reliability score or universal latency promise replaces that evidence.

The release matrix must include startup with missing sources, independent source
loss/return, phone loss, page/action transitions during recovery, persisted uncertainty
and the relevant update/config interruption points. Reuse existing fixtures and
runbooks, adding only missing cases. Real BLE, power loss and vehicle controller
behavior remain physical checks; host doubles cannot close those gates.

### A coherent experience across surfaces

For every stateful feature, use the same scenario IDs and semantic expectations
across firmware, Android and rendered review fixtures. Start with actions and
connection recovery; do not add a new UI framework or force C and Kotlin into one
runtime. A small neutral test-vector file is appropriate only when both suites
consume it, replacing duplicated fixture facts.

Each scenario records identity/session, observation age, authoritative state,
allowed actions, expected wording/priority and exit/recovery path. Include ready,
connecting, unavailable, running, stopped, rejected and uncertain outcomes as
applicable. A response for one action must not refresh a sibling's observation age.
A durable uncertainty record is distinct from an active operation. Presentation
simplification must preserve the recovery action and must not invent resolution.

Review the journey at normal speed as well as static screens. Count required taps,
confirmations and transient screens. On the round gauge prioritize the reading or
one named action, one useful status and a direct exit; put diagnostic detail in the
App. Approved visual changes retain unchanged approvals, but a semantic change
requires review of the affected states. See the [UX standard](design/design-system.md#ux-completion-standard).

### GitHub, screenshots and daily publishing

A PR identifies the feature/task, affected state scenarios, focused checks, remaining
physical gates and website delta. CI enforces what can be checked offline; reviewers
own usability and meaning. Do not claim that a Markdown checkbox verifies behavior.
Keep stacked PR dependencies explicit, and coordinate one owner for each device
session and shared documentation edit.

Use production Compose/LVGL captures as shared inputs to README, review pages and
website. Retain source commit, relevant input hashes, scenario, dimensions/theme
and capture method beside the assets. Detect changes to those inputs to flag a
review; do not invalidate all images for an unrelated commit. Unchanged outputs may
be retained with an explicit equivalence check. No invented image can serve as
implementation evidence.

The [website policy](../website/README.md) owns brand, captions and publication cadence.
At most once daily, while authorized work is active and capacity permits, reconcile
accepted changes since the live manifest, refresh only affected imagery, review
mobile/desktop layout, and publish a verified batch. If deferred, retain the delta
on the existing backlog item. No new scheduler or social-posting automation is
created by this framework. Downloads continue through the existing verified catalog.

### Enforcement sequence

Existing CI already validates builds, contract examples, document ownership/links,
formatting and UI tests. The review adds ADR/task-ID coverage to that same validator.
The remaining automation belongs in the backlog: shared state vectors first,
then source-aware image/claim review checks. Extend existing Python tools and test
fixtures before adding dependencies. Keep slow capture/device/publication work off
the normal developer feedback path.
