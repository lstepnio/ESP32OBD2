# GitHub hosted firmware validation

Recorded 2026-09-26 against integration commit `1bcb8fb`. This record distinguishes published artifact evidence from the remaining phone operation.

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

The final debug APK is installed on the owner-bonded Pixel 10 Pro. The phone locked before the hosted discovery/download/install interaction completed. No claim is made yet that the Pixel selected dev.9 from GitHub or installed it on the gauge. The gauge remains confirmed on dev.8 until that final journey is observed.

## Release automation boundary

The protected GitHub workflow and signing environment are present on the integration branch. GitHub only registers a manually dispatched workflow after the workflow file reaches the default branch, so dev.9 was published as a documented bootstrap using the same local signing tools. After merge, future development releases should be built and published by `.github/workflows/development-release.yml`.
