# Development firmware release runbook

Updated 2026-10-19. This runbook covers signed development prereleases hosted in GitHub Releases. It does not define production signing or stable-channel approval.

## Distribution boundary

Only the original reference board is eligible for public firmware. The public
workflow checks [platform policy](../../contracts/platform-policy.json); other
hardware uses the private integration/release path. Follow the
[platform/RC contract](../architecture/platforms-and-releases.md) before adding a
target or channel. This development runbook does not implement private RC/GA
publication and does not promote any additional board.

## Supported board matrix

| Board ID | Hardware revisions | SDK/toolchain | Partition layout | Transfer | Status |
| --- | --- | --- | --- | --- | --- |
| `waveshare-esp32-s3-touch-lcd-1.28` | `all` pending finer identity | ESP-IDF 5.4.1 | `egauge-16m-ab-v1`, two 3 MiB app slots | protocol 0 | Development hardware path observed previously |

Adding a catalog row does not support a board. Each new target needs its own sdkconfig, board support, display and touch validation, partition identity, signed bundle bounds, USB recovery package, and physical qualification.

## Trust and repository setup

The development app and firmware pin the public half of the development P-256 key. The private key is stored as the `DEVELOPMENT_UPDATE_SIGNING_KEY` secret in the GitHub `development-release` environment. Do not put it in Git, an APK, a release asset, or logs. Beta and stable channels require new keys and an explicit provisioning and revocation decision.

The workflow is `.github/workflows/development-release.yml`. Actions are pinned to commit SHAs. The build uses the same ESP-IDF version as quality CI, requires the ESP app descriptor version to equal the requested release version, signs the application-only image, creates a bounded `.egauge-dev-update` bundle, creates and signs `egauge-release-catalog.json`, and publishes an immutable prerelease.

Main is protected by pull requests and strict quality checks. The `development-release`
environment requires the repository owner's review and allows only `main` and
`codex/**` refs, so explicitly approved pre-merge hardware candidates remain possible.
Publication is serialized across the workflow, and its prerequisite rejects a source
SHA whose latest Foundation quality run is absent, incomplete or unsuccessful.
The build uses the same digest-pinned IDF image as CI; only the publish job has write
permission. Temporary signing material is removed even on failure. Environment review
is a technical gate, not a replacement for explicit owner release authorization.

The protected workflow is available on the default branch. Dispatch it against the
exact reviewed feature ref and verify the run's Git SHA before publication. Every
release uses this workflow; keep signing credentials in its protected environment.
Device installation and validation start from GitHub Releases. Local bundles are
limited to explicit recovery engineering and are not a release path.

The optional `parked_idle_test` workflow input defaults to `false`. Use `true` only
for an owner-authorized [parked idle candidate](../architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate),
and identify that limitation in its release notes. It selects the separately built
`build-parked-idle` image; ordinary releases keep the test switch off. CI compiles
both images from the exact source before publication. Publication and installation
still require owner authorization; this input does not qualify a vehicle profile.

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

The companion searches bounded published GitHub release candidates, verifies signatures and freshness, rejects a generation older than one previously trusted on the phone, and rejects different content reused under the same generation. It then selects the highest compatible sequence for the exact board, hardware range, layout, channel, and transfer protocol. A wrong board or layout cannot enter the updater. GitHub availability does not bypass the gauge's independent signature and image checks.

## Browser USB bootstrap

The unsupported Waveshare DIY tool at [egauge.majjix.com/recovery/](https://egauge.majjix.com/recovery/)
uses an existing signed published application plus separately identified bootstrap
regions. Its [package and preservation contract](../../web-recovery/README.md) owns
USB offsets, provenance and the physical acceptance matrix. This does not authorize
a new firmware release, erase-all operation or device installation. Normal field
updates remain App/Wi-Fi.

## Qualification gates

- Hosted check from a fresh install without a file picker.
- Bad catalog signature, expired catalog, unknown board, wrong layout, changed asset, truncation, HTTP failure, and rate limit.
- Interruption during download and BLE transfer, app rotation/lock/process death, reboot before confirmation, valid trial, and rollback.
- Exact running identity after activation and known-good identity after rollback.
- USB recovery from both app slots and preservation expectations for NVS and configuration slots.

Until these are recorded for the published asset, keep the release in the development channel and keep public OTA capability false.

Use the [GitHub OTA recovery matrix](ota-recovery-matrix.md) for repeatable percentage-triggered resets and the evidence required after each attempt.

## Legacy TCM image version labels

Historical `0.2.0-dev.N-tcm` image labels attached the test suffix to a numeric
prerelease identifier. Standard SemVer consequently ranks that identifier above
later numeric identifiers. Android compares this known legacy family by `dev.N`
for update availability, including equal/older rejection. Other version families
retain standard ordering. Bundle/catalog signature, descriptor and post-boot
image checks still use the exact published identity; no comparison alias changes
what image is accepted or confirmed. Publish new candidates as `0.2.0-dev.N`.

## Authorized Bluetooth repair when maintenance Wi-Fi cannot start

Wi-Fi remains the ordinary installation path. If the running image crashes while
starting its maintenance AP, an owner may explicitly authorize the existing signed
App/Bluetooth path for repair. Do not silently switch transports or use USB flashing.
Use the debug App and test APK, retain a private setup backup, stage only the verified
signed bundle under the App's external files as `authorized-recovery.egauge-dev-update`,
and opt in to `BluetoothFirmwareRecoveryTest` with `allowBluetoothFirmwareRecovery=true`
and exact `expectedFirmwareVersion` / `expectedFirmwareElf`. The test uses the App's
serialized update owner and journal, verifies the healthy exact boot and checks that
configuration, display settings and phone profiles remain unchanged. Run only while
the phone and gauge are available; stop/restore the debug awake helper afterward.

After a Wi-Fi repair, `WifiMaintenanceJourneyTest` opts in with
`allowMaintenanceWifi=true` and the same expected version/ELF. Start from an idle App,
with phone monitoring off and its process stopped, so the fixture can keep eGauge
visible with automatic BLE polling disabled. It opens a maintenance network twice,
reads authenticated OTA/config status through Wi-Fi, closes each session, verifies
fresh session identities and checks unchanged boot/configuration/settings. Network
startup/cleanup qualification is distinct from a full firmware upload, live adapter
coexistence, or power-loss recovery. Record the physical result and memory evidence
under the dated rollout report; leave private credentials and identifiers out of Git.


## Full App/Wi-Fi installation qualification

`WifiFirmwareJourneyTest` requires explicit `allowWifiFirmwareInstall=true` and exact
`expectedFirmwareVersion` / `expectedFirmwareElf`. Use an authorized newly published
signed development candidate: the production updater rejects the exact running image.
Do not bypass that guard or downgrade solely to manufacture a transfer test. A version-only
candidate can qualify the current firmware implementation without changing its logic.

The fixture uses the ordinary hosted catalog check, signed download, App update owner,
Wi-Fi transfer and post-restart confirmation. It checks the expected healthy ELF in the
alternate OTA slot, cleared recovery journal and unchanged configuration/settings/profiles.
Its sampled stage timestamps are diagnostic observations, not exact wire throughput;
retain the existing `eGaugeUpdate` stage timings and private USB memory evidence. Keep
phone monitoring off, take a private setup backup, keep the App visible and restore the
awake helper at session end. Obtain owner display/touch confirmation separately.
A successful indoor transfer does not qualify live dual-radio coexistence or interruptions.


## Pairing recovery with an unowned gauge

Confirm owner-window state, Android bond state and physical gauge behavior separately.
A missing gauge owner and stale phone bond do not authorize a silent bond reset;
obtain explicit authorization for removing the specific bond. Stop competing App
reads while performing an approved fresh pairing. Exact-name Bluetooth Settings
selection is available through the opt-in `SystemBluetoothUiTest`, using
`allowSystemBluetoothUi=true`, `exactLabel` and optional `matchDescription=true`.
It operates only in Android Settings and matches the current visible accessible
control. Preserve remote shell quoting for labels containing spaces. Never use
stale scan-row coordinates or interact with unrelated discovered devices.

If firmware rejects fresh pairing before a passkey and no owner can authenticate,
App/BLE and App/Wi-Fi installation cannot repair that image. Any USB recovery needs
separate explicit authorization, a verified signed image, private flash backup,
application-only partition targeting and preservation checks. Do not erase NVS,
configuration slots or the partition table, or bypass authentication to make OTA work.

## Owner-supervised orientation qualification candidate

The optional `orientation_candidate` workflow input defaults false. After explicit
owner authorization it selects `build-orientation-candidate`, can retain the existing
parked-idle option, and forces the board-qualified orientation flag off. The test-only
permission offers protected Auto after live sensor health without promoting public
capabilities or default board support. Record its limitation in release notes; do
not publish it as a qualified normal image. Use the [indoor procedure](display-indoor-qualification.md)
and restore user settings after testing. A candidate build is compiled in quality
CI before publication, including the optional sensor worker's real IDF branch.
