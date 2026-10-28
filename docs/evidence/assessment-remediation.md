# Project assessment remediation

> Historical evidence. Software work recorded 2026-10-23. [Current state](../current-state.md)
> owns installed facts; [backlog](../backlog.md) owns remaining work. These checks
> do not establish deployment, physical behavior or vehicle qualification.

## Scope and fixes

CODE-03 addresses the 23 October assessment against main `bbc232b6` and separately
hash-verified deployed intake scripts. Work is isolated on `codex/assessment-remediation`;
the older shared research checkout and its uncommitted files are preserved.

| Assessment | Prepared correction and regression evidence |
| --- | --- |
| F01 | Shared trial policy requires application ticks plus advancing/recent UI heartbeat and BLE readiness. Host harness extracts the actual confirmation task, proving frozen/stale UI or absent BLE confirms neither OTA nor configuration; healthy progress confirms both. Wrap/bounds fixtures cover the pure policy. No panel/rollback test was run. |
| F02 | Recovery captures one bundle for all retries; late metadata and file events cannot change an active selection/consent. Deferred port/write/retry tests execute the real browser orchestrator with simulated dependencies. |
| F03 | Backup opens existing source read-only, validates schema/integrity, verifies and fsyncs a private snapshot before atomic publication/directory sync, then prunes. Missing/corrupt/wrong-schema and failed-target-validation tests preserve prior backups; fixture restore compares all rows. |
| F04/F06 | Readiness checks schema and a write reservation; malformed/duplicate/file multipart fields get controlled 400 errors; storage failures return 503 with sanitized SQLite category. Tests cover unavailable storage, lock contention, parsing failures and subsequent success. |
| F05 | Forwarded identity is walked from explicitly trusted socket peers inward. Unit and HTTP fixtures cannot bypass throttle by changing left header values. Actual Caddy/Nginx addresses and edge behavior remain deployment checks. |
| F07 | APK signature/signer/package/version are verified using Android tools; manifest derives actual identity and explicitly labels source revision as operator attestation. Positive actual debug APK and negative generic ZIP checks were exercised locally. |
| F08 | Catalog rejections retain bounded date/trust/network/format counts; messages distinguish failures from incompatibility, preserve cancellation and allow verified compatible candidates. Both Android variants run the new fixtures. |
| F09 | Workflow retains exact ELF/map/bootstrap/config outputs, sanitized resolved dependencies, notices and SPDX dependency inventory in a separate signed evidence archive. Collection was exercised against the actual local build; fake/missing-output/license fixtures fail closed. No signed release was published. |
| F10 | Managed CI allowlist includes the existing five accessibility fixtures. |
| F11 | Scheduler intervals/fairness/dispatch accounting and profile schema 17 documentation match source. |
| F12 | Form failures preserve input and report unconfirmed outcomes without raw parser/network details. Deferred/error/validation/success fixtures cover both forms; fetch has a bounded deadline. |
| F13 | Routine candidate identities and configuration chunks move to DEBUG; INFO health/error/phase diagnostics remain. No claim of measured performance improvement. |
| F14 | Form sends server-validated consent version; new signup stores reviewed text and an acceptance event. Repeat signup and additive migration retain original text/timestamp. Fixture asserts form/server wording agreement. |

## Verification and limits

Local verification passed: formatting and host lint; 142 host tests and 11 review-server
tests; 42 Node recovery/form/orchestration tests; native sanitizers; ESP-IDF 5.4.1
firmware build and production LVGL/configuration fixtures; 237 Android debug and 212
release JVM tests; debug APK, instrumentation compilation and lint; all 54 managed
emulator UI tests, including five accessibility fixtures. Twelve intake tests also
passed on Python 3.12, the service runtime family, with temporary synthetic databases.
Exact-commit remote CI is a separate PR check, not inferred from these local results.
Its first full run exposed a timer-editor fixture failure during keyboard movement,
consistent with an intercepted coordinate click. The fixture now retires the keyboard, asserts the Done action is enabled
and visible, invokes its semantic click and waits for the parent save action. It
retains the exact 150-second saved-value assertion. Product touch qualification is
unchanged; no runtime behavior was altered to make this fixture pass.

The release collector was exercised with all eight resolved reference dependencies,
24 retained payloads (including ELF/map/support binaries and license notices), and
per-payload SHA-256 metadata. Its CLI rejects dirty tracked source. Original ELF/map
can contain build paths; dependency metadata is sanitized. This is not a claim of
bit-for-bit reproducibility or a complete transitive source-license review.

The current host source files matched the reviewed legacy scripts before maintenance
source was restored to this repository. No customer records were read, migrated,
submitted, exported or changed. No service restart, website publication, phone APK
installation, gauge flash, capability promotion or physical fault injection occurred.

## Remaining acceptance

Backend source/migration/proxy configuration and website assets need a scoped,
backed-up deployment using the [website procedure](../../website/README.md#intake-source-and-operational-checks).
Verify actual trusted proxy addresses, readiness, backup scheduling/failure alerts
and isolated restore before calling the live path fixed. Existing consent rows retain
their original purpose; removal includes the separate event records.

Future releases can exercise the signed evidence sidecar through the protected
workflow. No production signing/key rotation or distribution approval was added.
Physical QUAL-02/04/08/09/11/12 and action gates remain open, including rail-power loss,
trial rollback/fallback, loaded sources, real display/touch/assistive technology and
vehicle command/Stop/restoration behavior. New software checks do not close them.

Website impact: the current form/consent/error path and generated recovery bundle
change; publication remains a separate daily batch. Product layout/screens did not
change, so new marketing screenshots are unnecessary.

## Mainline reconciliation, 2026-10-28

The original reviewed head `8261de3ca38edff96a66afc5f9daec75209917b5` is preserved
by `codex/assessment-remediation-preserved-20261028`. PR #93 incorporates main
`f2dbf7596dbbb378eec9485c0f097ed79159ef10` without rewriting that history.
Current profile schema 19, polling/freshness policy, Car presentation, orientation
and startup stack guards are retained. The newer shared modal-editor test helpers
replace the older semantic-click workaround; all three Android test files from
PR #102 already match main. Historical test counts above describe the original
head only. Fresh reconciliation results are recorded below when completed.

This cleanup does not merge the PR, publish a release, deploy the website/backend,
install software, or close physical qualification gates.
