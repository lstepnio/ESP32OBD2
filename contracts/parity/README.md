# Shared companion fixtures

Kotlin and Swift read the same platform-neutral input/expected-output files here.
These are synthetic software tests, not captured BLE or vehicle observations.

`runtime-identity.json` covers the 44-byte protocol-0 runtime identity and activation
decision: healthy target, pending trial, rollback, wrong revision/hash, inactive,
unsigned u32 boundary, stored generation ahead, malformed length/version/reserved
fields/flags and inconsistent revision/digest. Rejected cases must fail decoding.
Both test suites invoke their production parser and confirmation function.

Run `./gradlew :app:testDebugUnitTest` from `android/` and
`swift test --package-path ios/EGaugeCore` from the repository root.
See the [parity process](../../docs/development/companion-parity.md) for remaining
coverage and how to review platform differences.
