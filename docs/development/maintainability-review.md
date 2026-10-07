# Maintainability review

Reviewed 2026-10-06 on the continuing integration branch, including the latest
`main` vehicle research changes. Scope: runtime/control ownership, recent fault
state/codec path, public capability promises, host/CI setup, documentation entry
points and stale active plans. This is a focused repository review, not a claim
that every line or physical behavior has been audited.

## Findings resolved

| Finding | Resolution | Evidence |
| --- | --- | --- |
| Public capability JSON advertised two adapter links while simultaneous operation remains unqualified | Both built-in and document variants now advertise one; validation rejects regressions | Source guard, document validation and firmware build |
| CI installed only schema dependencies while fake BLE tests import `bleak` | Dedicated pinned `requirements-test.txt` includes capture dependencies; CI/setup use it | Offline host tests, CI merge gate |
| Linux strict C11 config fixtures failed because `strnlen` is POSIX-only | Replaced with a bounded portable scan; exact maximum/oversized/empty label tests added | Config compiler sanitizer fixture |
| Generated Kotlin cache appeared as untracked source | Ignore the incremental compiler cache | Git status inspection |
| Two unused Android properties retained obsolete configuration/freshness logic | Removed `configurationBlockers` and `diagnosticsCurrent` after checking all callers; current projection and category freshness stay authoritative | Android compile/unit tests/lint |
| BLE discovery emitted an unexplained warning on normal item callbacks | Removed warning/FIXME; retained collection until `BLE_HS_EDONE` with explicit comment | Firmware compile; control flow unchanged |
| README/current state/setup/runtime docs described old branches and unavailable implemented features | Replaced with current source/evidence boundaries and exact setup commands | Link/token/schema checks and source inspection |
| Two completed review plans repeated old findings and future steps | Removed; redirected references to maintained quality/runtime guidance | Local link validation |
| New AI sessions had to traverse historical reports | Root instructions, concise AI map and documentation index define reading order and ownership | Verified named code paths and links |
| Historical reports mixed older pending steps with today's plan | Marked reference records; current status and remaining work are centralized | Documentation review |
| README lacked a clear visual preview | Existing native example fixtures show the round gauge and layout chooser | Inspected labelled fixture assets; no new physical claim |

## Current verification

The focused changes retain protocol-0 command bytes, protected owner checks,
configuration trial/recovery, update signing/trust and source separation. The
capability correction changes advertised link capacity only; it does not remove
legacy internal slot scaffolding. At that review the firmware source was dev.39, an uninstalled candidate. Consult
[current state](../current-state.md) for later changes.

Local checks: 70 offline host tests, capture self-test, C sanitizer fixtures,
ESP-IDF build, Android unit tests/debug build/instrumentation compilation/lint,
contract/document/token checks and whitespace validation. GitHub checks must pass
for the exact final PR commit before merging. No phone, gauge or vehicle was
connected or installed by this review.

## Remaining maintenance boundaries

- `AppModel.kt` and transport classes remain large. Extract an independently tested
  responsibility when a concrete feature needs it; avoid a broad rewrite solely for size.
- Keep protocol constants/vectors and source identity checks together when extending
  wire messages. Do not silently downgrade authentication or transport failures.
- New public feature claims require their corresponding physical path checks.
- Historical evidence is retained for traceability; use current-state/roadmap instead
  of following old session steps. Old prepared binaries are identified by their manifest
  commit, not assumed to be the current source image.
- Imported catalog/license scope remains a research boundary. No catalog write action
  or new drivetrain definition was enabled by this review.
