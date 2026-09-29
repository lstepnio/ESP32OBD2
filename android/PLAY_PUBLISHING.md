# Google Play publishing setup

## Local release bundle

This Mac has Android SDK 36, JDK 17, and an ignored `local.properties` pointing to the local SDK. The eGauge upload key and signing settings are stored outside this repository in `~/Library/Application Support/eGauge/android-upload/`. Both files are readable only by the current user. The associated private [release vault](https://github.com/lstepnio/ESP32OBD2-secrets) holds an encrypted backup. Its decryption identity is stored locally outside both repositories and needs a separate secure backup. Losing both the local upload key and the recovery identity requires a Play Console upload-key reset.

From `android/`, build a signed bundle with:

```sh
source "$HOME/Library/Application Support/eGauge/android-upload/signing.env"
./gradlew :app:bundleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

The output is `android/app/build/outputs/bundle/release/app-release.aab`. The build reads four `EGAUGE_UPLOAD_*` environment variables. Without them, Gradle can produce an **unsigned** release bundle that Play will reject. Never commit or upload the keystore or `signing.env`, and never put passwords in a command or build log.

The first signed bundle created on 2026-09-29 used package `com.lstepnio.egauge`, version code 1, version name 0.1.0, target SDK 36, and upload certificate SHA-256 fingerprint `BA:68:A5:93:AC:65:F6:7A:6D:A3:D0:80:B5:6B:3A:3D:05:E4:59:4C:29:5D:B2:76:A8:0D:8D:C0:3E:26:CF:01`. This fingerprint identifies the **upload** certificate. Google Play App Signing will use its own app signing certificate for installed Play copies.

## Play Console and release gates

- Complete personal developer registration, payment, and identity/contact verification in Play Console. The account owner must personally accept the agreements and pay the fee.
- Create the eGauge app and confirm the package name before the first upload. A Play package name cannot be changed for that app after publication.
- Add a public privacy policy and in-app link, then complete an accurate Data safety declaration for this exact release. Review BLE, legacy location permission, local vehicle profiles, and GitHub firmware-release requests.
- Complete the store listing, app icon, screenshots, content rating, target audience, app access, and other dashboard declarations. Describe current behavior honestly: the app has an offline simulated preview and experimental numeric gauge configuration. Live vehicle telemetry, code clearing, full renderer transfer, and public OTA are not implemented.
- Upload a signed bundle to an internal test first and verify installation and the normal flows on a physical phone and gauge. This build has not been qualified for public release.
- For a new personal account, Google currently requires a closed test with at least 12 continuously opted-in testers for 14 days before applying for production access. Recheck the account dashboard for its exact requirements.

Increment `versionCode` for every later Play upload. Keep the upload key backed up separately from the repository and check the Play Console upload certificate fingerprint after the first bundle is accepted.
