# eGauge continuation notes

## PR cleanup, 2026-10-28

Worktree: `codex/assessment-remediation`, PR #93. Original assessment head
`8261de3ca38edff96a66afc5f9daec75209917b5` is preserved in remote branch
`codex/assessment-remediation-preserved-20261028`. Reconciliation incorporates main
`f2dbf7596dbbb378eec9485c0f097ed79159ef10` without rewriting history.

All assessment fixes remain. Newer mainline modal fixtures, profile schema 19,
polling policy, Car UX, orientation and startup stack guards remain authoritative.
Release evidence follows the chosen build directory and its recorded sdkconfig;
default and both candidate selections have regression coverage. The Air up website
link targets maintained implementation/qualification documentation. Closure evidence
and retained unique PRs are in `docs/evidence/repository-maintenance.md`.

Fresh host lint, 156 host tests, 14 review-server tests, 42 Node fixtures and native
sanitizers passed. Five focused evidence/candidate tests passed. Final formatting,
Android/firmware builds and remote CI are checked before final delivery; exact head
and final results belong to the PR check records and cleanup completion comment.
Historical counts in the assessment record describe the original head only.

The dirty `codex/obd-definition-catalog` checkout was not edited. PR #112 installation
evidence and unique work in #41/#57/#58/#59/#64/#72/#73/#74/#77 remain separate.
No merge to main, deployment, release publication, installation or physical
qualification is authorized or established by this cleanup. Do not delete retained
branches as an incidental cleanup step.
