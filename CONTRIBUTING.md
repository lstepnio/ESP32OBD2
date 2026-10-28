# Contributing

Use [README](README.md) for setup/checks and [AGENTS.md](AGENTS.md) for project
boundaries. [Current state](docs/current-state.md) distinguishes implemented code,
installed software and physical qualification.

## Changes and review

- Use a short-lived `codex/` branch, preserving other work. Commit each tested cohesive
  change with a verb-led message explaining the outcome; keep code/tests/docs together.
- Push at session end. Open a PR describing scope, checks and remaining qualification.
  Merge only after required checks, review, applicable hardware evidence and owner
  authorization. Main rejects direct pushes, deletion and non-fast-forward updates.
- Routine commits/pushes do not authorize merging, release publication or installation.
  Releases require separate authorization and the [release procedure](docs/development/firmware-release-runbook.md).
- Do not force-push shared history. Remove only merged branches unused by open PRs.
  Preserve unique/stacked work. Never commit keys, captures or personal device identifiers.

## Verification and contracts

Run the shared lint, format and test commands for affected boundaries. CI invokes the
same entry point; [quality gates](docs/development/quality.md) describe hardware limits.
An optional local hook runs host lint: `git config core.hooksPath .githooks`.
It is a convenience, not a replacement for CI or boundary-specific tests.

Wire/storage changes need compatibility notes, examples and focused rejection/migration
vectors. PID definitions need provenance, licensing, exact vehicle/ECU scope, units,
decoder and sample evidence. Catalog membership never proves vehicle compatibility.

Features are complete only with the [feature documentation rule](docs/documentation.md#feature-completion-rule)
satisfied in the same PR, including refinements, experiments and removals. Use the
[feature index](docs/features.md) and [source map](docs/ai-context.md) to find the owner.
Follow [documentation ownership](docs/documentation.md). Link to topic owners instead
of duplicating them, classify maintained documents in the map, and retain historical
measurements. Third-party notices remain intact; a project-wide license is still an
owner decision.
