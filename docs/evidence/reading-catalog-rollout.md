# Reading catalog rollout evidence

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

### Session result, 2026-10-07

- Android debug APK build, instrumentation APK build, lint and 145 JVM tests pass.
- Pixel native fixtures: 18 tests pass, comprising four new reading/alert tests,
  ten dashboard editor journeys and four unified dashboard tests. Three older
  assertions were corrected to compare canonical doubles; the editor behavior
  remained verified. App dev.46 was installed with replacement install preserving
  data, and the original phone wake settings were restored after testing.
- ESP-IDF 5.4.1 candidate dev.46 build passes. Production configuration fixtures
  compile all 55 definitions/alerts and evaluate all catalog vectors. Core logic
  fixtures pass with AddressSanitizer and UndefinedBehaviorSanitizer. The host
  compile suppresses macOS deprecation warnings in the pinned third-party cJSON
  implementation; project warnings remain errors.
- Repository contract/examples/rejection cases, generated catalog parity, document
  links and design token parity pass.
- The subsequent authorized signed installation is recorded below. Vehicle
  qualification remains separate from these source and editor checks.

### Signed App/Wi-Fi rollout, 2026-10-07

- All three quality jobs passed for source commit `022d7451e1790f621e2af6070443de3fd16b8e3d`
  in run 37703919973. Owner explicitly authorized publishing and installing dev.46.
- Protected release run 37704367028 published immutable prerelease
  [dev.46](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.46)
  from that exact commit, consuming catalog generation 28. Every previously published
  catalog was checked first; the previous maximum was 27.
- Clean GitHub downloads passed independent catalog and bundle signatures against
  the App-pinned public key, board/layout/protocol checks, size/hash checks and the
  ESP descriptor version check. Image size: 1,519,472 bytes. Image SHA-256:
  `2666be389322364736549d4f62ce9a5d28d0522e5e22062f4e281e951b6397fe`.
  ELF SHA-256: `cca5675b8b9160511d4cd75df55e2e359f166bfec178cdf924519e1602572c86`.
- Actual Pixel App dev.46 checked GitHub through Settings, downloaded and verified
  the hosted release, then installed it over private gauge Wi-Fi. The same warm
  App showed **Update installed** after authenticated reboot confirmation. No USB
  flash, local package picker or manual version check was used for installation.
  Android network availability took 9.192 s, first Wi-Fi response 12.021 s from
  preflight, and gauge flash preparation 2.334 s. These are this session's timings.
- Read-only protected opening/resume check passed both before and after installation.
  After installation it asserted the exact hosted ELF hash, version dev.46 and OTA
  health 2. Opening confirmation took 20.521 s and resume 3.235 s. An initial GATT
  133 owner-read failure recovered automatically; no pairing or connection tap was
  required. Bluetooth toggle and interrupted OTA were not tested in this session.
- The current three-page dashboard, including its one alert, stayed byte for byte
  unchanged at revision 49. Phone profile, gauge-association and presentation
  preferences were also byte for byte unchanged; update recovery journal cleared.
  Gauge settings stayed 100% brightness, 270°, Imperial, page cycling Off. The owner's
  current dashboard differed from the earlier revision-46 six-page baseline; the
  fresh before/after comparison used revision 49.
- Owner separately confirmed the physical gauge displayed a normal page and swipes
  worked. Original Pixel wake settings were restored. Private screenshots/readback
  evidence remains under ignored artifacts, without personal identifiers in Git.
- No vehicle capture or alert observation was performed here. New decimal gauge rendering, real TCM alert
  entry/clear/stale recovery, independent temperature meaning and gears 2..8 remain
  physical follow-up. Public capabilities and qualified link capacity stay unchanged.
