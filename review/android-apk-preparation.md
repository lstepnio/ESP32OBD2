# Android APK preparation evidence

Read-only inspection on 2026-10-29 (America/Denver). No APK was rebuilt,
repackaged, re-signed, copied into downloads, installed or published for this step.

## Existing artifact

| Property | Observed value |
| --- | --- |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk` |
| Size | 34,814,748 bytes |
| SHA256 | `b0142c38ce37a8569d3cfaa9ed9c7f430bde97d62f1a7689765f6c45b0cc99c0` |
| Package | `com.lstepnio.egauge` |
| Version name / code | `0.2.0-dev.53` / `53` |
| Min / target / compile SDK | `29` / `36` / `36` |
| Variant | Debug; binary declares `application-debuggable` |
| ABI libraries | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Signers | One |
| Signer certificate DN | `C=US, O=Android, CN=Android Debug` |
| Signer certificate SHA256 | `be0fae89e04e5c71660273c4084ed0c98879f567fe13b6d15f5df9b243586f87` |
| Signature verification | Pass; APK Signature Scheme v2 |

Source checkout is `083b0013363a697ab832179ee1af71b4456ca181` plus the
capture-only Android instrumentation test. There are no Android production source
modifications in this review. A direct Git comparison of Android production/debug
sources, shared core and Gradle build inputs found no differences between the
dev.75 runtime source `62d1e4b` and review base `083b001`. The current Android
working-tree addition is solely `WebsiteReviewCaptureTest.kt`. The focused capture build accepted the debug APK
build tasks as UP-TO-DATE. The APK file timestamp is October 28 at 20:49 local, so
this is the existing incremental artifact reused by that successful build, rather
than a newly emitted APK in this follow-up. The source SHA is checkout/build
context, not a signed embedded source attestation. The capture-only test is in the
separate androidTest APK and does not add a production App feature.

## Pinned website comparison

The repository's `website/downloads/manifest.json` currently pins:

| Property | Pinned website metadata |
| --- | --- |
| Version / variant | `0.2.0-dev.51` / `debug` |
| Source | `414cedd46a2273dd13435ddce273236b70db7b74` |
| APK URL | `/downloads/egauge-android-7ea52f9c0d98d6db.apk` |
| APK SHA256 | `7ea52f9c0d98d6dbb6fa2793d7a0eadaa79b4e30230668f296c538077ea2210e` |
| Size | 45,109,037 bytes |
| Signer certificate SHA256 | `be0fae89e04e5c71660273c4084ed0c98879f567fe13b6d15f5df9b243586f87` |

The downloaded pinned binary is now available at
`artifacts/review-downloads/egauge-android-7ea52f9c0d98d6db.apk`. Read-only binary
inspection confirms:

| Property | Pinned APK binary | Current APK binary |
| --- | --- | --- |
| Package | `com.lstepnio.egauge` | `com.lstepnio.egauge` |
| Version name / code | `0.2.0-dev.51` / `51` | `0.2.0-dev.53` / `53` |
| Min / target / compile SDK | `29` / `36` / `36` | `29` / `36` / `36` |
| Signature verification | Pass; v2; one signer | Pass; v2; one signer |
| Certificate SHA256 | `be0fae89e04e5c71660273c4084ed0c98879f567fe13b6d15f5df9b243586f87` | Same |

The downloaded pinned binary's SHA256 exactly matches the manifest's
`7ea52f9c0d98d6dbb6fa2793d7a0eadaa79b4e30230668f296c538077ea2210e`.
The current binary was rehashed and remains
`b0142c38ce37a8569d3cfaa9ed9c7f430bde97d62f1a7689765f6c45b0cc99c0`.

**Package and signer match, and current versionCode 53 exceeds pinned code 51.**
These inspected packaging properties support proposing the existing current APK
for the same-signer upgrade/download path without re-signing or replacing the key.
It remains a development/debug APK, not a release-signed or stable artifact.

This is binary packaging compatibility evidence, not a performed upgrade or data
preservation test. No installation was attempted. Existing users of code 53 remain
a separate versioning case; this inspection does not establish a new monotonically
increasing version for them. The incremental build linkage described above also
remains distinct from a signed source attestation.

No public-download replacement or live website check was performed here. The
parent review owns the publication proposal, source attestation, release/version
choice and any later installation qualification.

## Exact tools and commands

SDK root comes from existing `android/local.properties`:
`/Users/lukasz.stepniowski/Library/Android/sdk`. Existing SDK build-tools 36.0.0
were used. Commands ran from the repository root:

```sh
/Users/lukasz.stepniowski/Library/Android/sdk/build-tools/36.0.0/aapt2 dump badging android/app/build/outputs/apk/debug/app-debug.apk
/Users/lukasz.stepniowski/Library/Android/sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs android/app/build/outputs/apk/debug/app-debug.apk
shasum -a 256 android/app/build/outputs/apk/debug/app-debug.apk
cat android/app/build/outputs/apk/debug/output-metadata.json
cat website/downloads/manifest.json
git rev-parse HEAD
git status --short
/Users/lukasz.stepniowski/Library/Android/sdk/build-tools/36.0.0/aapt2 dump badging artifacts/review-downloads/egauge-android-7ea52f9c0d98d6db.apk
/Users/lukasz.stepniowski/Library/Android/sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs artifacts/review-downloads/egauge-android-7ea52f9c0d98d6db.apk
shasum -a 256 artifacts/review-downloads/egauge-android-7ea52f9c0d98d6db.apk android/app/build/outputs/apk/debug/app-debug.apk
git diff --name-only 62d1e4b 083b001 -- android/app/src/main android/app/src/debug android/core android/app/build.gradle.kts android/build.gradle.kts android/gradle
git status --short -- android
```

`aapt2` and `apksigner` both exited 0. Size was read from the filesystem, version
metadata from the binary was cross-checked against Gradle output metadata, and the
certificate digest was compared exactly with the manifest field and the downloaded
pinned APK. The earlier lookup only covered `website/downloads/`; that location
was absent, but the downloaded binary under `artifacts/review-downloads/` has now
been inspected. The follow-up used that existing download and changed only this
note. No installation, signing, repackaging or rebuild was performed.
