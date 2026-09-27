# GitHub OTA recovery matrix

This matrix qualifies recovery behavior using signed firmware downloaded by the companion from GitHub Releases. Local firmware import is outside this procedure.

## Preconditions

- The current image is confirmed valid and its version, partition, ELF SHA-256 and active configuration revision are recorded.
- The next development image is published by the protected GitHub workflow with a new version and release sequence.
- Clean downloads of its catalog, signature and bundle match the published hashes, and the catalog signature verifies with the embedded development public key.
- The Pixel is unlocked, the debug stay-awake policy is active, and the gauge has stable USB power and a serial connection.

## Transfer interruption cases

| Case | Reset point | Expected result |
| --- | --- | --- |
| Early | 5 to 15 percent | Current confirmed partition boots; candidate partition is never selected; configuration is unchanged; app reports interruption rather than success |
| Middle | 40 to 60 percent | Current confirmed partition boots; partial candidate is ignored; configuration is unchanged; retry starts safely |
| Late | 85 to 99 percent | Current confirmed partition boots unless activation was durably accepted; app reconciles the actual running identity and never infers success from transfer percentage |
| Verification | After 100 percent transfer but before activation | Current confirmed partition remains selected; digest or signature work cannot make a partial image bootable |
| Trial boot | After activation and before the health gate | Bootloader either confirms a healthy image or rolls back to the last valid image; app reports the observed identity |

For percentage-triggered cases, start **Install development update** from the GitHub candidate and run:

```bash
python3 tools/ota_interrupt_at_progress.py \
  --adb "$HOME/Library/Android/sdk/platform-tools/adb" \
  --serial-port /dev/cu.usbmodem5C931582021 \
  --esptool-python "$HOME/.espressif/python_env/idf5.4_py3.9_env/bin/python" \
  --target-percent 10 \
  --output /tmp/egauge-ota-early.json
```

Repeat with targets 50 and 90. A target of 100 resets on the first visible complete-transfer state to exercise the verification and activation boundary. The tool reads the visible Android progress and invokes an ESP32-S3 hard reset through esptool. It does not select a package or initiate an update. For targets below 100, a skipped threshold that reaches 100 percent fails without resetting the gauge.

## Evidence after every case

1. Read the authenticated running firmware identity from Android.
2. Record version, partition offset, OTA state and full ELF SHA-256.
3. Read the active configuration identity and confirm revision and hash.
4. Confirm the gauge display, touch, BLE owner link and application health gate operate normally.
5. Record the Android outcome text. A timeout, disconnect or unknown outcome must not appear as success.
6. Start a fresh GitHub update attempt only after reconciliation completes.

Physical removal of USB power remains a separate final case because a serial reset does not remove rail power or reproduce brownout behavior.

## Observed GitHub dev13 run, 2026-09-26

The Pixel downloaded and verified `0.2.0-dev.13` from GitHub Releases. The gauge began each case on confirmed `0.2.0-dev.12` at `0x60000`. The harness triggered ESP32-S3 serial resets at 12, 50, and 90 percent. Android's last visible progress values were 17, 55, and 94 percent because an in-flight Wi-Fi batch can complete after the UI observation and before reset propagation.

After every reset, the app reported the closed socket and required an authenticated firmware identity read. Each read reported confirmed `0.2.0-dev.12`; no interrupted candidate was reported as installed. Authenticated configuration readback reported running and stored revision 2 with SHA-256 `6689a89228758d4bbe43cb772ed4902db447b1f68a3980ab1502b0d11c0bfba9` after all three cases.

The uninterrupted retry completed over the private Wi-Fi transport. The gauge confirmed `0.2.0-dev.13` at `0x360000` with OTA state valid and ELF SHA-256 `a318ff2001dd156e79ea4e05186479d7cb5da1bea9db331ce9ab7bad2657a73b`. A fresh protected configuration read showed the same revision and hash, and a new GitHub check reported `Installed 0.2.0-dev.13 is up to date`.

The reset cases exposed a usability issue: Android waited for the 70-second socket read timeout before reporting loss. Reducing the socket timeout to 20 seconds alone did not solve it: a dev14 reset at 12 percent remained at 17 percent for more than 60 seconds because best-effort OTA abort and BLE Wi-Fi shutdown cleanup ran sequentially after the socket failure. Bounding those cleanup attempts to three and five seconds fixed the complete path. A repeat reset at 14 percent left the last visible value at 18 percent and surfaced `Socket closed` 15.13 seconds after the reset completed.

The app then reconciled to confirmed `0.2.0-dev.13` and retried the same verified GitHub download. The uninterrupted retry installed confirmed `0.2.0-dev.14` at `0x60000` with ELF SHA-256 `c63b9b2f54841f4233042b2eeb186bfffb2514fa388bdf5e5cbd9c2a52c40d94`. Configuration remained at running and stored revision 2 with the same full hash. A fresh GitHub check reported `Installed 0.2.0-dev.14 is up to date`.

## Observed GitHub dev15 complete-transfer boundary, 2026-09-26

The protected release workflow published `0.2.0-dev.15` as catalog generation 7. A clean GitHub download produced catalog SHA-256 `e772df48cce17d5e0cb0c36d55664f3cfed2012c53b0d746401be4a885fd2a97`, catalog-signature SHA-256 `a622d88043375736edab345ddb2fca7182b1ab4c11de3bf1148e883c33754c23`, and bundle SHA-256 `393cb1de7324a24d23f71f4244874d6f93d770fa8d5b9bd75171cb8f8cc00e23`. OpenSSL verified the catalog signature. The catalog bundle hash and downloaded bundle hash matched, and the bundle contained the expected 1,473,472-byte image with raw SHA-256 `b79f520d276dba190138d047c7e172ec5566c703ed0a234278c0d3e0a5fba3d9`.

Starting from confirmed `0.2.0-dev.14`, the Pixel downloaded and verified dev15 from GitHub. The harness observed transfer progress at 96 percent and then 100 percent at 62.12 seconds, immediately issuing an ESP32-S3 serial reset at the first visible 100-percent state. The update had already crossed the durable activation boundary: dev15 booted from `0x360000`, passed the application health gate, and became confirmed valid. Android then performed a separate owner-authenticated firmware read and reported the full expected ELF SHA-256 `26b5c630441a4d0f353c94ba4f6a40f273674a6119b8a00388a844b4f3422055`.

An authenticated configuration read reported running and stored revision 2 with unchanged SHA-256 `6689a89228758d4bbe43cb772ed4902db447b1f68a3980ab1502b0d11c0bfba9`. Display, touch, BLE owner access, and the application health gate remained operational. A fresh catalog check reported `Installed 0.2.0-dev.15 is up to date`.

These results qualify early, middle, and late serial reset interruptions, a reset at the first Android-visible complete-transfer state, and complete GitHub-hosted installation. The 100-percent observation did not isolate the narrower interval before durable activation because the firmware had already accepted activation by the time reset propagation completed. Physical power removal, a precisely instrumented reset after verification but before activation, and reset during the trial health window remain open cases.
