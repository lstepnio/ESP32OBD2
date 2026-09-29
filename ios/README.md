# iOS companion

`eGauge.xcodeproj` contains the first native SwiftUI app. It follows the newer
Android preview-led flow: Gauge, Car, Settings, optional Expert, local vehicle
profiles, ordered gauge pages, five preview layouts, reading-specific alert
editing, and a review screen. Preview values are always labeled examples.
Profiles use Android's version-4 JSON shape and can read older versions 1-3.
`EGaugeCore` holds profile migration, validation, configuration projection,
protected state and runtime confirmation logic.

Gauge setup has a read-only Core Bluetooth path for service discovery, public
capabilities and protected owner status. It is compiled but has not been tested
against a physical iPhone. Sending settings, live telemetry, diagnostics, firmware
installation and temporary Wi-Fi joining remain unavailable in the UI. Owner
confirmation is never inferred from the public capability read alone.

- [Android review, parity matrix and implementation differences](../docs/development/companion-parity.md)
- [Local setup and validation](../docs/development/ios-setup.md)

Open `eGauge.xcodeproj` in Xcode to build the app. `project.yml` is the XcodeGen
source for the checked-in project; regenerate it after adding targets or files:

```sh
xcodegen generate --spec ios/project.yml --project ios
```

Build and test locally:

```sh
swift test --package-path ios/EGaugeCore
xcodebuild -project ios/eGauge.xcodeproj -scheme eGauge -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build-for-testing
python3 tools/generate_ios_tokens.py --check
```

The proposed deployment floor is iOS 18. The core also supports macOS 15 for
host tests. Current simulator builds use no Apple account. The app and package
have no third-party runtime packages. The first two UI journeys passed on both
local iOS 27 phone simulators; app-store signing and real accessory behavior
need separate gates.
The initial app targets iPhone. Add iPad only after its navigation and layouts
have their own acceptance pass.
