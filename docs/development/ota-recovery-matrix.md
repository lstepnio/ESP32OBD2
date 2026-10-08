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

For a deterministic development-only verification-to-activation case, start the debuggable app with a bounded activation pause:

```bash
adb shell am force-stop com.lstepnio.egauge
adb shell am start -n com.lstepnio.egauge/.MainActivity \
  --el debug_ota_pause_before_activation_ms 15000
```

After starting **Install development update**, target the exact debug status text:

```bash
python3 tools/ota_interrupt_at_progress.py \
  --adb "$HOME/Library/Android/sdk/platform-tools/adb" \
  --serial-port /dev/cu.usbmodem5C931582021 \
  --esptool-python "$HOME/.espressif/python_env/idf5.4_py3.9_env/bin/python" \
  --target-text "Debug activation pause active" \
  --output /tmp/egauge-ota-before-activation.json
```

Only a debuggable build honors the intent extra, and values are capped at 60 seconds. Normal launches and release builds use no pause. The app now reports preparing, transferring, verifying, restarting, and confirmation as separate stages so a completed byte count is not presented as an installed update.

## Evidence after every case

1. Read the authenticated running firmware identity from Android.
2. Record version, partition offset, OTA state and full ELF SHA-256.
3. Read the active configuration identity and confirm revision and hash.
4. Confirm the gauge display, touch, BLE owner link and application health gate operate normally.
5. Record the Android outcome text. A timeout, disconnect or unknown outcome must not appear as success.
6. Start a fresh GitHub update attempt only after reconciliation completes.

Physical removal of USB power remains a separate final case because a serial reset does not remove rail power or reproduce brownout behavior.

## Qualification record

Earlier serial-reset measurements are [historical evidence](../evidence/ota-recovery-history.md).
Current pending gates are **QUAL-02** in [the backlog](../backlog.md). Repeat relevant
cases for the candidate under test; a prior version's result does not qualify new code.
