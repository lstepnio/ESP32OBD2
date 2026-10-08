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
| How does the current implementation work? | `docs/architecture/` | Runtime ownership, persistence or recovery changes |
| Which bytes/schema are accepted? | `docs/protocol/`, `contracts/`, codecs | Wire or compatibility changes; explicitly separate proposed contracts |
| What should the consumer UI do? | `docs/design/design-system.md`, tokens | Shared UI rules or components change |
| How do I build, release or run a controlled session? | `docs/development/` | A maintained procedure changes |
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

## Change checklist

1. Verify current Git/worktree and the affected source/contract.
2. Update only the authoritative document for each changed fact.
3. For implementation, record behavior, compatibility and focused verification.
4. For physical work, record exact App/image/health identity and distinguish phone,
   gauge, serial and vehicle observations. Keep private identifiers/artifacts out of Git.
5. Update backlog readiness/acceptance and current state if either changed.
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
