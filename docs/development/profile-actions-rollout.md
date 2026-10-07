# Profile actions development rollout

Date: 2026-10-07. Publication and App/Wi-Fi installation were explicitly authorized
by the owner. This records device evidence separately from gesture qualification.

## Published and installed identity

- App `0.2.0-dev.35`, version code 35, installed on the physical Pixel before firmware.
- Source commit `ce68d88c4006d9fdecf40e44a0f56e7367e3f7d0`.
- Signed [dev.42 prerelease](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.42),
  catalog generation 24, release workflow 37628211422 succeeded at that commit.
- Downloaded catalog signature, bundle hash/size, image signature, board/layout,
  application descriptor and version independently verified against the APK's pinned public key.
- Image length 1,514,144 bytes.
- Image SHA-256 `1f86e5fdc95eca61ad645e6c62c54c3a6fc52467b629c0a310e15768f3db3a02`.
- ELF SHA-256 `782f19f54a0b101a6030869d3ce192aab16e6e9c97710395692527e0e8d32611`.

The physical Pixel downloaded and transferred the hosted release using App/private
Wi-Fi. No USB flashing was used. Transfer reached 100%, but its initial post-reboot
confirmation timed out and displayed “Update outcome is unknown”. Subsequent
protected readback established the exact expected version/ELF and OTA health 2.
This timeout remains a recovery/UX follow-up; successful transfer alone was not
used as proof of installation.

The read-only `AutomaticConnectionJourneyTest` passed in 5.899 seconds, asserting
exact firmware identity, healthy boot, automatic opening/resume protected reads,
and unchanged configuration, settings and local profiles within that test.
Bluetooth toggling and gauge-picker cancellation were not requested in this run.
Configuration revision 31 and digest
`1cc480a237646eb259ffc6c48d230c0c66a87d6f3198c701f45f45a6d7d02b3c`
were retained. USB serial also reported dev.42 and preserved owner/configuration;
serial evidence does not establish physical screen or gesture behavior.

## Phone and gauge setup mismatch

The gauge readback contains its TCM TRANSMISSION dual page and GEAR numeric page.
The phone's saved collection contains engine profiles and no matching TCM profile
or child. This was observed before sending any configuration; the firmware update
preserved the gauge's existing setup. Car > Actions opened correctly on the Pixel,
but no action was saved or sent. Temporary profile browsing was returned to Jeep.

Recover the gauge's saved TCM setup into an appropriate local profile without
replacing engine profiles or binding one physical adapter simultaneously to two
sources. Then review/send the shortcut through the normal atomic path.

## Remaining checks

- Owner confirmation of normal physical display and horizontal swipes.
- Physical upward-gesture shortcut, cooldown, stationary pairing hold, rotation,
  automatic cycling and persistence checks after profile recovery.
- Investigate initial update confirmation timeout using retained transfer/boot evidence.
- Two-adapter coexistence/recovery qualification still requires a second adapter.
- No high-idle, ABS/ESC or other vehicle commands were enabled or executed.

Private device identifiers, readback, screenshots and logs remain ignored under
`artifacts/profile-actions-rollout/`. Phone wake settings were restored after the session.
