# Documentation consolidation, 2026-10-07

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

## Scope

Documentation and offline documentation tooling only. No App/firmware behavior,
protocol bytes, device installation, vehicle session or public capability changed.

- Moved 46 dated development reports into the evidence directory, preserving their
  recorded measurements and marking old instructions historical.
- Split dated catalog rollout, OTA reset results, action fixtures and capture
  preparation from maintained procedures; retained their evidence and incoming links.
- Retired the superseded adapter integration plan after retaining outstanding
  acceptance in the stable backlog. Current state owns support, roadmap orders
  outcomes and backlog owns task readiness/dependencies/acceptance.
- Corrected mixed-controller Dual-page/TCM-alert claims, profile schema/migration,
  SharedPreferences storage, per-adapter ownership, response-buffer bounds and old
  installed-image/session instructions. Distinguished public v1 proposals from
  implemented development protocol 0 and partial/proposed alert/control behavior.
- Added AI task routing, a machine-readable document/source map and standing
  documentation maintenance instructions. Kept dated research scoped and source
  evidence distinct from physical qualification.

## Verification

Repository validator passed schema/examples/rejection vectors, generated catalog
and design-token parity, and authored document coverage/local file/heading links.
Twelve isolated documentation regression tests passed: missing files/headings,
Unicode/repeated headings, fenced examples, unclassified/duplicate ownership,
retired source paths, undefined/duplicate task IDs and historical scope banners.
These checks now run in the contracts-and-docs CI job. Whitespace review passed.
External URLs were not revalidated over the network; dated third-party research
was scoped rather than presented as newly checked compatibility.
