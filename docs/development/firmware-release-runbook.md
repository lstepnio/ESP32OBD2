# Development firmware release runbook

Date: 2026-09-26. This runbook covers signed development prereleases hosted in GitHub Releases. It does not define production signing or stable-channel approval.

## Supported board matrix

| Board ID | Hardware revisions | SDK/toolchain | Partition layout | Transfer | Status |
| --- | --- | --- | --- | --- | --- |
| `waveshare-esp32-s3-touch-lcd-1.28` | `all` pending finer identity | ESP-IDF 5.4.1 | `egauge-16m-ab-v1`, two 3 MiB app slots | protocol 0 | Development hardware path observed previously |

Adding a catalog row does not support a board. Each new target needs its own sdkconfig, board support, display and touch validation, partition identity, signed bundle bounds, USB recovery package, and physical qualification.

## Trust and repository setup

The development app and firmware pin the public half of the development P-256 key. The private key is stored as the `DEVELOPMENT_UPDATE_SIGNING_KEY` secret in the GitHub `development-release` environment. Do not put it in Git, an APK, a release asset, or logs. Beta and stable channels require new keys and an explicit provisioning and revocation decision.

The workflow is `.github/workflows/development-release.yml`. Actions are pinned to commit SHAs. The build uses the same ESP-IDF version as quality CI, requires the ESP app descriptor version to equal the requested release version, signs the application-only image, creates a bounded `.egauge-dev-update` bundle, creates and signs `egauge-release-catalog.json`, and publishes an immutable prerelease.

The workflow becomes dispatchable after it exists on the default branch. Until this integration branch is merged, a release may be bootstrapped with the exact workflow commands and uploaded as immutable GitHub release assets targeting the reviewed commit. A clean GitHub download must be compared byte for byte and its catalog signature independently verified before the app can install it. After merge, all releases use the protected workflow. Device installation and validation always start from GitHub Releases; local bundles are limited to explicit recovery engineering and are not a release path.

## Publish

1. Merge or select the exact reviewed commit. Confirm quality CI is green.
2. Choose a new semantic development version and a monotonically increasing release sequence. Never reuse a published tag, version, sequence, or asset name.
3. Run the **Publish development firmware** workflow with version, sequence, and concise notes.
4. Confirm the workflow built from the intended commit and the release is marked prerelease.
5. Download all three assets and verify catalog signature, bundle size/hash, bundle signature, board ID, layout, and ESP app descriptor.
6. From a fresh companion install, check GitHub, download, verify, upload to a powered gauge, and read running firmware after reboot. Record the app commit, release tag, phone, gauge, partition, and ELF hash.

The catalog expires after 180 days. A newer release publishes a new catalog containing the supported set. Published versions are not edited in place.

The protected workflow queries every published `egauge-release-catalog.json` asset and requires the requested sequence to exceed the maximum generation already present. This check includes withdrawn releases because their generations remain consumed. The companion independently searches all bounded release candidates, verifies their signatures and freshness, selects the highest compatible generation, and rejects conflicting signed content under one generation. GitHub API ordering is not treated as release order.

## Withdrawal and recovery

For a bad development release, mark it withdrawn in the next signed catalog by omitting it and publish a higher generation. Also edit the GitHub prerelease notes to say withdrawn. Do not replace its assets. Devices already running it recover through normal signed upgrade or the documented USB partition procedure. Keep the last known-good bundle and its factory recovery artifacts.

`dev-v0.2.0-dev.11` is the first recorded withdrawal. Its catalog accidentally reused generation 3, which had already been consumed by dev.3. The companion's conflicting-generation guard rejected it before download or installation. Its assets remain immutable, its release notes identify the withdrawal, and dev.12 consumes generation 4.

The companion currently searches published GitHub releases for the first signed catalog, verifies its signature and freshness, rejects a generation older than one previously trusted on the phone, and rejects different content reused under the same generation. It then selects the highest compatible sequence for the exact board, hardware range, layout, channel, and transfer protocol. A wrong board or layout cannot enter the updater. GitHub availability does not bypass the gauge's independent signature and image checks.

## Qualification gates

- Hosted check from a fresh install without a file picker.
- Bad catalog signature, expired catalog, unknown board, wrong layout, changed asset, truncation, HTTP failure, and rate limit.
- Interruption during download and BLE transfer, app rotation/lock/process death, reboot before confirmation, valid trial, and rollback.
- Exact running identity after activation and known-good identity after rollback.
- USB recovery from both app slots and preservation expectations for NVS and configuration slots.

Until these are recorded for the published asset, keep the release in the development channel and keep public OTA capability false.

Use the [GitHub OTA recovery matrix](ota-recovery-matrix.md) for repeatable percentage-triggered resets and the evidence required after each attempt.
