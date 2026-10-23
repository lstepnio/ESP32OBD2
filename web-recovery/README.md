# Browser USB recovery

[Public tool](https://egauge.majjix.com/recovery/) for unsupported DIY bootstrap and
repair of the **Waveshare ESP32-S3-Touch-LCD-1.28**, ESP32-S3 / 16 MB. This is not an
App/Wi-Fi replacement or a production provisioning service. Browser verification
is not physical qualification of display, touch, pairing or vehicle operation.

## Boundaries

Adapted from [Emviary's recovery tool](https://github.com/lstepnio/Emviary/tree/main/web-recovery),
with its MIT notice retained. `esptool-js` 0.7.0 and SparkMD5 3.0.2 provide the same
ROM/stub transport and readback hashing; esbuild 0.25.12 produces a self-hosted ESM
bundle. These pins, npm lock and committed notices avoid runtime CDN dependencies.
No cloud account, UART application protocol or credential restoration is imported.

- Download the reviewed fallback manifest and four separate, bounded SHA-256-checked regions
  before offering USB. HTTPS and the reviewed page/manifest are the trust boundary;
  browser SHA checks are integrity checks, not independent firmware signatures.
- Require explicit exact-board confirmation and a browser port selection. Check S3,
  16 MB and security state before writing. Chip checks cannot identify display wiring.
- Use one SerialPort and local MAC identity across retries, without uploading either.
  Try 460800, 230400 and 115200 baud, at most 120 seconds each. Close transport before
  another attempt; fence late writes after timeout. A failed cleanup requires reload.
- Read only public application bytes to qualify USB and consume the stub's final
  MD5 packet. Check whole-region MD5 before and after writes; skip matching regions.
- Write application at `0x60000`, bootloader at `0`, table at `0x8000`, then select
  recovered slot zero with blank `0x10000..0x11fff` OTA data. Keep image headers.
  Never merge regions, erase all, write NVS/PHY/config slots or modify eFuses.
  Existing eGauge settings regions remain untouched; other firmware layouts do not
  have that preservation guarantee. Interrupted writes can require another USB repair.
- Report flash verification separately from reset and owner-observed boot/touch.
  Normal signed development updates remain App/Wi-Fi. No capability flag changes.

## Build and checks

```sh
npm ci --prefix web-recovery --ignore-scripts
npm test --prefix web-recovery
npm run build --prefix web-recovery
python tools/dev.py lint --target host
```

`core.mjs` owns flash scope and timeout cleanup; `releases.mjs` owns release metadata and signed local-package validation. `recovery.js` owns
DOM/browser gesture and retry orchestration. `website/recovery/` contains HTML,
generated bundle and notices. Host tests run the pure Node cases; CI also rebuilds
and compares the committed bundle. Keep generated output and source in one commit.

## Package provenance and updates

`website/downloads/manifest.json` is the single pinned download inventory. Its
firmware entry identifies `website/recovery/package.json`, including source release,
support-build provenance and region hashes. Binaries live outside Git under the
website's content-addressed `/downloads/` paths. Do not replace files in place or
silently rotate the APK signing identity. Keep earlier URLs for cached pages.

The initial package uses **the exact signed published dev.60 application**. Since
that GitHub release did not include bootstrap regions, the bootloader/table were
rebuilt from its source with ESP-IDF 5.4.1. They are not original release CI assets.
The generated blank OTA selector chooses the recovered application. No new firmware
release or release asset was published. Future application releases still require
owner authorization under the [release runbook](../docs/development/firmware-release-runbook.md).

Reproduce the initial package from repository root, with ESP-IDF 5.4.1 exported:

```sh
mkdir -p artifacts/recovery-input artifacts/recovery-source
# Immutable, published artifacts; no signing key is used here.
gh release download dev-v0.2.0-dev.60 --dir artifacts/recovery-input
git archive 860dc3f9f9ffa7e28a388a7100fb6e61ff616a39 | tar -x -C artifacts/recovery-source
(cd artifacts/recovery-source/firmware/gauge && idf.py bootloader partition-table)
python tools/dev.py build --target android
# Get the certificate fingerprint from apksigner verify --print-certs, not a guess.
python tools/package_web_recovery.py --input artifacts/recovery-input \
  --source artifacts/recovery-source --apk android/app/build/outputs/apk/debug/app-debug.apk \
  --app-commit EXACT_CLEAN_APP_COMMIT --apk-certificate-sha256 VERIFIED_CERTIFICATE_HASH \
  --output artifacts/website-downloads
```

APK packaging runs build-tools 36.0.0 `apksigner verify --print-certs` and
`aapt2 dump badging`, requiring the reviewed signer, package name and source version.
It derives the actual version/code/variant from the APK, rejects invalid signatures
and missing verification tools, and labels the supplied source revision as operator
attestation. Signature/package/version checks alone do not prove build-to-source linkage.
Set `ANDROID_HOME` as for the Android build.

The packager verifies catalog/application signatures with the existing public key,
pinned published hashes, source board configuration, toolchain, image headers and
partition descriptors. Record the actual APK source commit and signer; the debug
APK is intentionally not a stable production distribution channel. The initial APK
is App dev.51 from `414cedd`; it includes offline-only command foundations, not
enabled high-idle control. Timestamped builds may produce new hashes even at the
same source: review a new inventory and package URL instead of overwriting.

For a package change, review and copy its generated `manifest.json` to
`website/downloads/manifest.json` and the referenced `recovery.json` to
`website/recovery/package.json`. Check binary hashes using a full website build:

```sh
python tools/build_website.py --downloads artifacts/website-downloads --check
python tools/build_website.py --downloads artifacts/website-downloads --output artifacts/website-preview
```

For a website-only rebuild, retrieve the current inventory's URLs from
`https://egauge.majjix.com/downloads/` into the matching relative paths in an ignored
input directory. The builder checks every size/hash. Default `--check` is offline
metadata/link validation; a deployable build always requires `--downloads`.

Follow [static deployment and rollback](../website/README.md#deployment-and-rollback).
Serve `/recovery/index.html` as `/recovery/`; existing Nginx redirects `/recovery`
to that directory. The [Nginx site configuration](../website/deployment/nginx.conf)
sets `absolute_redirect off` so directory redirects keep the public HTTPS origin,
instead of exposing the internal HTTP port behind Caddy. Self-hosted scripts/downloads
fit the existing same-origin CSP. Preserve existing contact APIs and no-index policy.

## Hardware acceptance still required

On a spare exact board: blank-board install; repair from both OTA slots; verify
exact running app identity; physical display/swipes; saved NVS/config/bond retention;
unplug during app write and selector write followed by retry; unsupported chip/flash;
BOOT/RESET on actual USB bridge; repeated retries and cleanup on desktop Chrome/Edge.
Do not use the owner's configured gauge for destructive bootstrap qualification
without explicit authorization. Automated mocks do not establish this matrix.

## Release selection and local packages

The recovery page reads public GitHub release metadata through the fixed same-origin
`/downloads/releases.json` Nginx endpoint. It includes development prereleases,
excludes drafts and accepts asset links only from this repository. Ten-minute
upstream caching avoids spending the anonymous API allowance on every visit;
stale successful metadata can survive an upstream outage. This is not an arbitrary
URL proxy and uses no token. No background job or new service is required.

The existing verified recovery remains a fallback. Other firmware releases use
**select release → download `.egauge-dev-update` → choose file → install**. Local
files stay in the browser. The strict two-entry ZIP reader bounds compressed and
decoded data, verifies the existing P-256 development signature over board tag,
image length and SHA-256, then checks the selected GitHub asset hash when provided.
It accepts the current 0.2.0 development image format, ESP32-S3/DIO/16 MB and IDF
5.4.1 with the reviewed support regions. Other toolchains/layouts need matching
bootstrap support. A layout change must change the board/compatibility contract;
do not reuse this board tag or silently relax the image checks. USB write regions,
NVS preservation and post-write checks are unchanged. Raw BINs are not accepted.
The PEM import comes from the firmware's public key, not a duplicated key copy.

APK selection lists release assets only when a release contains exactly one APK.
Until APKs are published, the existing verified hosted APK remains explicitly
labelled by version. Publishing an APK with a future release makes it appear
without a website change. Release authors must retain a consistent signing identity
and include compatibility notes. This change does not publish an APK, rotate a
signer or change the firmware release workflow. Android install/update and backup
instructions live on the recovery page.

Offline Node checks cover metadata/link validation, signed package acceptance,
wrong hashes/signatures/versions, bounded archives, malformed entries and existing
USB write/timeout behavior. Browser checks must also cover blocked metadata,
selection/file-validation races, keyboard access and mobile overflow. No USB write
is needed to check these flows. Server configuration changes require Nginx syntax
validation and reload using the existing deployment procedure.

During an installation, the verified bundle is captured once for all baud-rate
retries. Delayed metadata can populate choices but cannot replace that bundle or
reset board consent. Starting install or choosing a local file counts as explicit
user intent. `orchestration.test.mjs` exercises deferred metadata during port/write
and retry; these mocked checks do not establish physical USB recovery.
