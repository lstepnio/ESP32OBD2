# GitHub hosted firmware validation

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

Recorded 2026-09-26 against the `feat/owned-gauge-control` integration branch. This record covers the published artifacts and the complete owner-app installation on physical hardware.

## Published development release

GitHub prerelease [`dev-v0.2.0-dev.9`](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.9) contains:

- `egauge-release-catalog.json`
- `egauge-release-catalog.sig`
- `egauge-waveshare-esp32-s3-touch-lcd-1.28-0.2.0-dev.9.egauge-dev-update`

The catalog is generation and release sequence 2, expires after 180 days, and authorizes only board ID `waveshare-esp32-s3-touch-lcd-1.28`, board revision `all`, partition layout `egauge-16m-ab-v1`, development channel, and transfer protocol 0. The bundle was created with `--expected-version 0.2.0-dev.9`, which checked the ESP application descriptor before signing.

## Clean-download verification

All assets were downloaded into a new directory through the GitHub release API. Their SHA-256 values exactly matched the upload inputs:

| Asset | SHA-256 |
| --- | --- |
| Catalog | `8d3f62ba71db7950ea0a082ff925a3388c2dece7db1775ac43de078951d46e4e` |
| Catalog signature | `15ea8541cfbae5e9bd21b7cbc3650d70bd86316dd632de462e32669a5171384b` |
| Update bundle | `0fd49fdf0309b91e8e1a31ba0dd1cc833d7d457820c2a45cb97aaa87d4d8d9e9` |

OpenSSL verified the downloaded catalog signature with `firmware/gauge/main/certs/dev-update-public.pem`. The application image is 1,473,408 bytes with SHA-256 `8f6afea20e9d1e7c38dbd973a7af927458ad28e19f2140b1ee38e566ed8d340e`; its ELF SHA-256 is `1948f04c9088382b6d61aab42b13fbf435b30cf7669da24b142a54c707623d97`.

## Companion safeguards

Before offering a hosted release, the app now reads the authenticated running image identity and applies semantic version precedence. Equal and older hosted versions are reported as up to date and cannot populate the install selection. Unit fixtures cover increasing development versions, equal versions, older versions, stable-over-prerelease precedence, a higher major version, and unparseable values.

The final debug APK was installed on the owner-bonded Pixel 10 Pro. The app authenticated to the gauge, read running version `0.2.0-dev.8`, discovered prerelease `0.2.0-dev.9` through the GitHub API, downloaded all three release assets, and reported `GitHub development firmware 0.2.0-dev.9 verified and ready` only after catalog, signature, board, layout, channel, image-version and digest checks passed.

The first manual attempt exposed two integration defects before image transfer: the GitHub API endpoint was missing from the HTTPS host allowlist, and Android rejected its first local-only `WifiNetworkSpecifier` request after a brief association. The downloader now explicitly permits `api.github.com` alongside GitHub release and GitHub content hosts. The Wi-Fi client unregisters a failed callback and makes one bounded retry with a fresh network request. Unit fixtures reject HTTP and lookalike hostnames.

With those fixes installed and bounded retry enabled, the app sent the hosted image over the temporary authenticated gauge Wi-Fi network. The successful run did not expose whether Android used the first or second network request. Android reported 100 percent and independently confirmed the restarted image. Serial boot evidence showed:

- application partition `ota_0` at `0x60000`
- version `0.2.0-dev.9`
- ELF SHA-256 `1948f04c9088382b6d61aab42b13fbf435b30cf7669da24b142a54c707623d97`
- configuration revision 2 with three PIDs, three pages and one alert retained
- trial image confirmed after UI, BLE and application progress

A subsequent hosted check read the authenticated running identity and reported `Installed 0.2.0-dev.9 is up to date`. It did not offer the equal release for download or installation. This provides physical evidence for discovery, signed download, compatibility filtering, Wi-Fi transfer, activation, post-reboot confirmation and equal-version suppression on the development channel.

## Catalog and session policy hardening

The follow-up release-integrity pass stores the SHA-256 of the accepted catalog alongside its highest generation. It rejects a lower generation and rejects different content reused under the same generation, while migrating the earlier generation-only preference on its next successful check. Compatibility selection happens before advancing stored trust state, so a valid catalog without an entry for this gauge cannot consume a generation locally.

Android unit fixtures cover invalid signatures, expired and future-dated catalogs, wrong board, layout, channel and transfer protocol, HTTP and lookalike hosts, generation rollback, changed content under one generation, and invalid generation or digest bounds. Firmware routes its session binding, strictly increasing frame sequence, expiry and tick-wrap decisions through a pure policy function exercised under AddressSanitizer and UndefinedBehaviorSanitizer. Wrong-session, wrong-key, and replay rejection are now also observed on physical hardware; see [Wi-Fi transport security validation](wifi-transport-security-validation.md).

GitHub does not guarantee that the first release returned by its API contains the highest catalog generation. The companion now examines every bounded release candidate, verifies each catalog independently, and selects the highest compatible generation. A live check exposed a reused generation in dev.11 because the historical dev.3 release had already consumed generation 3. The companion rejected the conflict before bundle download. Dev.11 was marked withdrawn without replacing its assets, the release workflow gained a published-maximum preflight, and dev.12 advances to generation 4.

## Corrected GitHub-only release

Prerelease [`dev-v0.2.0-dev.12`](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.12) targets commit `f0173c6` and consumes catalog generation and release sequence 4. A clean GitHub download matched the publishing inputs and OpenSSL verified its catalog signature. Published asset SHA-256 values are:

| Asset | SHA-256 |
| --- | --- |
| Catalog | `d96ac6a51ed1f672045e6a211c26e0b7581875785c38fa9e68bc6916413400da` |
| Catalog signature | `ee77534bcbbfa7d168b106723cf5c9c00d483e576cc179b1dad62d8d1a2bd9a6` |
| Update bundle | `4a38b0dab723efdb4a6355ed42a255ce0af6825d1a9dd9bce8a3b25cc3744aba` |

The Pixel enumerated the GitHub catalogs, advanced its pinned catalog identity from generation 2 to generation 4, downloaded and verified dev.12, and installed it over the authenticated private Wi-Fi transport. Android confirmed the healthy image at `ota_0` offset `0x60000` with ELF SHA-256 `383a71f7919d36e561bc7c800bc44f8b72b343492afb845ebed3011a69c94683`. A subsequent GitHub check reported `Installed 0.2.0-dev.12 is up to date`. Local update bundles were removed from the phone after this validation so routine development installations start from GitHub Releases.

## Release automation boundary

The protected GitHub workflow and signing environment are present on the integration branch. GitHub only registers a manually dispatched workflow after the workflow file reaches the default branch, so dev.9 was published as a documented bootstrap using the same local signing tools. After merge, future development releases should be built and published by `.github/workflows/development-release.yml`.
