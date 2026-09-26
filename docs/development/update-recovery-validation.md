# Development update recovery checks, 2026-09-25

These checks use a paired Pixel 10 Pro, the owner-only BLE update path, and the USB-powered Waveshare gauge. No OBD adapters or vehicle are present. The development signing key remains outside Git.

## Interrupted transfer and retry

1. The starting image was `0.1.0-baseline`, OTA state valid, partition `0x360000`, ELF SHA-256 prefix `6b933b9e46838eaa`.
2. Built `0.2.0-dev.1` from the repository's new `version.txt`, signed its 1,054,528-byte app image, and imported the package on the Pixel. Its raw image SHA-256 begins `64123799e3a3`; its ELF SHA-256 begins `865859ebcf68cfeb`.
3. Started BLE delivery and forcibly stopped the Android app at 12% progress. After relaunch and owner reconnect, the gauge still reported `0.1.0-baseline`, partition `0x360000`, valid OTA state, and the prior ELF hash.
4. Selected the same signed package again. The client discarded the incomplete prior transfer and sent a fresh image through 100%. The gauge verified and activated it.
5. The app reported the new image at `0x60000`. A separate owner identity read showed `0.2.0-dev.1`, valid OTA state, partition `0x60000`, and ELF SHA-256 prefix `865859ebcf68cfeb`.

This confirms restart-from-zero recovery after an interrupted Android process while the gauge remains powered. It does not confirm resume from a partial offset, power-loss recovery, or a disconnected BLE link during activation.

## Rollback probe

Pending observation. An isolated worktree contains a deliberately unhealthy trial image that restarts before confirming OTA health. It is signed with the development key and has ELF SHA-256 prefix `9ac1a2c41346e385`. The probe is never part of the integration branch source. A successful rollback requires the gauge to return to the valid `0.2.0-dev.1` image at `0x60000`, with the probe slot marked aborted in OTA metadata.
