# Android companion

Native Kotlin/Compose companion for Gauge, Car, Settings and optional Expert tools.
The gauge remains autonomous; the phone stores setup and configures it through one
protected Bluetooth operation owner. Ordinary gauge previews are labelled in the App.

## Build, test and run

Follow the root [setup](../README.md#development) with JDK 17 and SDK 36. From root:

```sh
.venv/bin/python tools/dev.py build --target android
.venv/bin/python tools/dev.py test --target android
.venv/bin/python tools/dev.py lint --target android
.venv/bin/python tools/dev.py test --target android-ui
```

The last command uses a disposable Gradle-managed API 35 Pixel 2, never the connected
owner phone. APK: `app/build/outputs/apk/debug/app-debug.apk`. Use an explicit device
serial for an authorized installation: `adb -s SERIAL install -r PATH_TO_APK`.

## Ownership and further reading

- [Android runtime](../docs/architecture/android-runtime.md): lifecycle, storage,
  presentation and operation ownership.
- [Source map](../docs/ai-context.md#source-map): feature/transport entry points.
- [Current support and qualification](../docs/current-state.md).
- [Testing and screen captures](../docs/development/quality.md#android-ui-and-screen-captures),
  including physical-phone safeguards and the separate reviewed golden baseline.
- [Design tokens/components](../docs/design/design-system.md).

Gradle locks resolved dependency versions and verifies artifact checksums. Material 3
Expressive uses a pinned prerelease dependency that still needs production review.
