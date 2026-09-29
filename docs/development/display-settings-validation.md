# Display settings qualification

Date: 2026-09-29. Hardware: Waveshare ESP32-S3-Touch-LCD-1.28 gauge and Pixel 10 Pro. The user observed the gauge screen directly; the app observations below came from the Pixel UI. This record is for the development channel.

## dev.27 protected path

- PR #49 CI passed Android build, lint, unit tests, firmware build, and contract checks. The signed `dev-v0.2.0-dev.27` release targeted commit `dfef5244f18a3bd3bae045c8bce71b89fbf3ea97`. Its downloaded catalog signature, generation 17, bundle hash, board and partition identity, embedded version, and image signature were independently verified. Image SHA-256: `115f4d57ef1b9e301516aabea9d56239ec03e6b8b7427bcd213327a29ba8848a`.
- The package copied to the Pixel had the same SHA-256 as the verified download. The app's signed package picker accepted it and transferred it over the gauge Wi-Fi path. The app reported gauge confirmation, then a separate installed-version read returned `0.2.0-dev.27`. The gauge had reported dev.26 before the transfer.
- The dev.27 public `ds` capability remained absent. A temporary, uncommitted debug APK allowed the Settings screen to call the protected display commands for qualification. It was replaced afterward with the normal APK.
- Initial protected read returned 0 degrees and 80% brightness. The Pixel saved 47% and 90 degrees; protected readback showed both values. The user observed the physical screen dim and rotate.
- The user then changed brightness to 100%, so the first post-reset read of 100% and 90 degrees was not a persistence failure. A controlled second test saved 57% and 90 degrees, hard reset the gauge without flashing, restarted the app process, and read 57% and 90 degrees from the gauge. This confirms both settings survived that restart.
- The Pixel then saved 100% and 0 degrees and received protected readback for both, restoring the user's chosen brightness and original orientation.

## Public capability gate

Dev.28 enables `ds:1` after the above physical qualification. The repository is private, so the app's anonymous hosted update check returns 404; use the verified GitHub release package through Expert > Development updates > Choose development package.

## dev.28 public path

- PR #49 CI passed Android build, lint, unit tests, firmware build, and contract checks. The signed `dev-v0.2.0-dev.28` release targeted commit `d93cdc6554488c39b1112a02f5a87f0868fb593b`. Its downloaded catalog signature, generation 18, bundle hash, board and partition identity, embedded version, and image signature were independently verified. Bundle SHA-256: `8ee85961d46263f6afbb060c624affdea7e59e87728760d7bf736650bedd9d6e`. Image SHA-256: `d231e8e74821c7db36f67943eefc1dcf3224949057f8eef98f87e91cac8cc6e4`.
- The package copied to the Pixel had the same SHA-256 as the verified download. The normal app accepted it and transferred it over the gauge Wi-Fi path. The app reported gauge confirmation, then a separate installed-version read returned `0.2.0-dev.28`.
- After restarting the app to refresh BLE capabilities, Settings showed both controls. A protected read returned the user's 100% brightness and original 0-degree rotation. The normal Settings screen saved 61% brightness and 90-degree rotation and reported gauge readback for each. It then restored 100% and 0 degrees with protected readback. Direct physical display and touch observation on dev.28 is pending; the user had already observed dimming and rotation on dev.27.
