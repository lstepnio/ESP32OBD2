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

An isolated worktree built a deliberately unhealthy trial image that calls `esp_restart()` when booted in `ESP_OTA_IMG_PENDING_VERIFY`, before the normal health task can confirm it. The source change is not part of the integration branch. Its signed image was 1,053,328 bytes with ELF SHA-256 `9ac1a2c41346e38585b4e99e03fa21916e3a0c607912e0cd3582cb8774ee5a11`.

The Pixel imported and verified the signed package. An early attempt stopped when the phone left eGauge before activation. A fresh attempt reached 88% while observed; the phone later locked and the BLE link disconnected. After the link was idle, a USB read of both 4 KiB OTA metadata sectors showed sequence 3, state `ESP_OTA_IMG_VALID` (`2`) in `ota_0` and sequence 4, state `ESP_OTA_IMG_ABORTED` (`4`) in `ota_1`. The first 512 bytes of each app slot contained the expected descriptors: `ota_0` had the valid build's ELF SHA-256 `865859ebcf68cfeb70ccec7ba17b5027ef6935ba405bbe5d8ae857f986ae4105`; `ota_1` had the probe hash above. A subsequent serial boot log showed the bootloader loading `0x60000`, app version `0.2.0-dev.1`, and ELF prefix `865859ebc`, followed by normal configuration, display and BLE startup. The saved document still loaded with three PIDs, three pages and one alert.

This verifies the bootloader's aborted-trial fallback to the previous valid image and preservation of the active configuration. The probe's own restart log was not captured, and opening the USB serial port also resets the device, so the exact reset that first marked the trial aborted was not isolated. The Android install result after activation was not captured. Power-loss recovery, a genuine firmware crash, and the Android rollback message remain separate checks.
