# GitHub OTA recovery history

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

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

## Observed GitHub dev16 pre-activation boundary, 2026-09-26

The protected release workflow published `0.2.0-dev.16` as catalog generation 8. A clean GitHub download produced catalog SHA-256 `7cfcdf1a8fc4a802ae120a9b5fedb24b508b542f17543f0a57277ba992d89231`, catalog-signature SHA-256 `677da8ce7840228a885ecf164af311fe91792ec5536400655d1cab7f25053fa4`, and bundle SHA-256 `ec25b501790118504f9f214ca283d7a83d7adf70a5542bb01b4112a39f312056`. OpenSSL verified the catalog signature. The bundle contained the expected 1,473,472-byte image with raw SHA-256 `589da61c34be58dc1637da9e3b33df18db7193b4d7107372b89dfa8fa86c3775` and ELF SHA-256 `1bad389693ad76bcac1d8e1618c411d711b163b1f5f386bfb080cc0126548451`.

The debuggable Pixel app inserted a 15-second pause only after the gauge returned a successful signed-image verification and before Android sent activation. The harness matched the exact visible `Debug activation pause active` stage at 100 percent and issued an ESP32-S3 serial reset. A separate owner-authenticated read then reported the previous confirmed dev15 image at `0x360000`; Android classified the candidate as not activated or rolled back. A protected configuration read reported running and stored revision 2 with unchanged SHA-256 `6689a89228758d4bbe43cb772ed4902db447b1f68a3980ab1502b0d11c0bfba9`.

The first run exposed a misleading delay: after the gauge network disappeared, Android requested that already-used temporary network again and eventually reported a platform approval failure. The Wi-Fi client now permits its bounded retry only before the first encrypted frame. Repeating the same deterministic reset surfaced `Gauge maintenance network was lost during transfer` within the first 6.3-second post-reset UI observation and again reconciled to confirmed dev15 with configuration revision 2.

The final app was launched normally without the debug pause and downloaded dev16 again from GitHub. The uninterrupted retry installed confirmed `0.2.0-dev.16` at `0x60000` with the full expected ELF SHA-256. A separate protected configuration read returned the same revision and full hash, and a fresh catalog check reported `Installed 0.2.0-dev.16 is up to date`.

The serial-reset matrix now qualifies early, middle, late, post-verification pre-activation, and first-visible-complete states. Physical power removal and a reset during the unconfirmed trial health window remain open hardware cases.
