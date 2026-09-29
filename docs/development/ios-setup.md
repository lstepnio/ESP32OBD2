# Local iOS setup

## Reproduce

Full Xcode is required; command-line tools alone are insufficient. On this Mac,
Xcode is already selected at `/Applications/Xcode.app/Contents/Developer`.

```sh
xcodebuild -version
xcodebuild -checkFirstLaunchStatus
swift test --package-path ios/EGaugeCore
python3 tools/ios_simulator.py --download --open
xcodebuild -project ios/eGauge.xcodeproj -scheme eGauge \
  -destination 'platform=iOS Simulator,name=eGauge Compact' \
  CODE_SIGNING_ALLOWED=NO test
```

The setup tool compiles an ad-hoc-signed, simulator-only environment probe,
downloads Apple's current iOS runtime only if none is available, creates reusable
`eGauge Compact` (iPhone 16e) and `eGauge Large` (iPhone 17 Pro Max) devices,
boots each, installs and launches the probe, checks the shared runtime vectors,
records the simulator's Core Bluetooth state and takes screenshots. It shuts down
devices it booted for the checks, then `--open` boots the compact device and
opens [Xcode 27's Device Hub](https://developer.apple.com/documentation/xcode/device-hub).
Select eGauge Compact there to see its screen.
It does not erase existing simulators, touch physical-device bonds, require an
Apple developer account, or change the global Xcode selection.

If Xcode is already downloading the runtime, let that finish and run without
`--download`. Downloads can be large and slow. To validate compilation independently:

```sh
python3 tools/ios_simulator.py --build-only
xcrun simctl list runtimes
xcrun simctl list devices available
```

To automatically finish setup after an existing download, without starting a
second download, use `python3 tools/ios_simulator.py --wait-for-runtime 120 --open`.
The wait limit is in minutes. A failed/timed-out setup has no fresh success record;
inspect its output and rerun after resolving the download.

Local build products, device IDs, probe output and screenshots go to ignored
`artifacts/ios-setup/`. `validation.json` is written only after both simulator
runs pass. It is simulator evidence, never an iPhone/gauge qualification record.
The probe source is in `ios/SimulatorProbe/` and is separate from the product
app target in `ios/eGauge.xcodeproj`. The final command runs the app's native
navigation test on the compact simulator. The Swift package can also be opened
directly.

## Simulator coverage

Use an injected fake gauge for future editor, profile, error, interruption and
recovery UI tests. Native simulator execution can validate Swift code, rendering,
navigation and local persistence. It cannot qualify actual gauge pairing, passkey
entry, BLE timing/radio behavior, temporary Wi-Fi joining, physical lock/background
behavior or power-loss recovery. Local-network permission behavior explicitly
requires a real device per [Apple's guidance](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy).

Keep hardware integration unavailable until an actual iPhone completes the
[parity matrix gates](companion-parity.md). A Mac Bluetooth harness or proxy would
still be Mac hardware evidence. Do not use retired iOS 5 simulator Bluetooth
instructions to change this Mac's radio configuration.

The proposed iOS 18 minimum is not covered by installing the current runtime.
Before release, install a compatible oldest-supported runtime/Xcode as needed and
test the minimum OS and a physical small device. Only the current runtime is
downloaded here to avoid unneeded multi-gigabyte installations.

## Local execution record

2026-09-29, Apple Silicon Mac:

- macOS 26.6.2 (25G83), selected Xcode 27.0 (27A266a), Swift 6.4.
- Xcode first-launch check passed. Initially no simulator runtimes or devices.
- Apple installed the native arm64 iOS 27.0 (24A434) runtime, 8.05 GB.
- Swift host tests passed all 17 shared cases. The same 17 cases passed through
  Android's production parser and confirmation code. Android debug assembly,
  unit tests, instrumentation compilation and lint passed. No phone was installed
  or debugged as part of this task.
- The simulator-only SwiftUI probe compiled and ad-hoc signed successfully.
  The saved iPhone 16e and iPhone 17 Pro Max simulators both booted and ran all
  17 shared cases. Both reported Core Bluetooth as Unsupported. Exact device IDs,
  runtime/build and probe screenshots are in `artifacts/ios-setup/validation.json`.
- Contract/docs validation passed: four schemas, seven examples, 15 rejection
  cases, signed decode vector, 59 document link sets and color token parity.
- The native app built in Debug and Release for iOS Simulator. Eight Swift host
  tests passed, covering default/profile migration, config projection, protected
  state and runtime confirmation.
- Both phone sizes passed the two native UI journeys: offline navigation/review
  and a vehicle profile retained after app restart. The app was installed and
  launched on both devices. Final visual captures are
  `artifacts/ios-setup/app-compact.png`, `app-large.png`, and
  `app-large-dark.png`. The compact light and large light/dark screens were
  inspected. An initial line-wrap and light-preview contrast issue was corrected
  before these final captures.

No physical iPhone, gauge, adapter or vehicle observations were made in this task.
