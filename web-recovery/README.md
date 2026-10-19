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

- Download the pinned manifest and four separate, bounded SHA-256-checked regions
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

`core.mjs` owns validated bytes, flash scope and timeout cleanup. `recovery.js` owns
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
to that directory without a configuration change. Self-hosted scripts/downloads
fit the existing same-origin CSP. Preserve existing contact APIs and no-index policy.

## Hardware acceptance still required

On a spare exact board: blank-board install; repair from both OTA slots; verify
exact running app identity; physical display/swipes; saved NVS/config/bond retention;
unplug during app write and selector write followed by retry; unsupported chip/flash;
BOOT/RESET on actual USB bridge; repeated retries and cleanup on desktop Chrome/Edge.
Do not use the owner's configured gauge for destructive bootstrap qualification
without explicit authorization. Automated mocks do not establish this matrix.
