# Development setup and checks

Use an existing working environment first. Commands below run from the repository
root unless stated otherwise. Hardware work needs the owner's readiness/authorization;
normal firmware installation is through App/Wi-Fi. Building does not install anything.

## Offline tooling

Python 3.10+ and a C compiler are needed for host checks. `bleak` is imported by mocked
adapter tests even when no Bluetooth connection is made.

```sh
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements-test.txt
.venv/bin/python tools/validate.py
.venv/bin/python -m unittest discover -s tools/tests -p 'test_obd*.py'
.venv/bin/python tools/obd_capture.py self-test
```

[Quality workflow](../../.github/workflows/quality.yml) contains the exact standalone
C sanitizer and config-binding commands. Diagnostics host tests also compare the
production C snapshot with the Android wire fixture.

## Android

Use JDK 17, Android SDK platform/build-tools 36 and the checked-in Gradle wrapper.
Set `ANDROID_HOME`, or use ignored `android/local.properties` with `sdk.dir`.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
# When instrumentation contracts change:
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk` from the root.
[Android README](../../android/README.md) covers screenshots, phone test constraints
and release signing. Do not run connected tests on the user's phone incidentally:
they can replace its app and remove local data.

For an authorized live Android session, run `python3 tools/adb_debug_awake.py start`
beforehand and `stop` afterward. This restores the phone's original wake settings.
A manually locked phone still requires the owner to unlock it.

## Firmware

Use ESP-IDF 5.4.1 targeting ESP32-S3, then:

```sh
. "$IDF_PATH/export.sh"
cd firmware/gauge
idf.py build
```

On the existing Mac, select the managed environment before exporting IDF; this
avoids accidentally choosing a newer Homebrew Python:

```sh
export IDF_PYTHON_ENV_PATH="$HOME/.espressif/python_env/idf5.4_py3.9_env"
export PATH="$IDF_PYTHON_ENV_PATH/bin:$PATH"
. "$HOME/esp/esp-idf-v5.4.1/export.sh"
```

These Mac paths describe the known local installation. New machines should use a
supported clean IDF setup or the pinned CI container. Do not recreate old package
metadata workarounds unless an actual error requires them.

Component manifests pin dependencies; generated component downloads and the local
BSP lockfile remain ignored. `firmware/gauge/version.txt` supplies image identity.
Build outputs are not signed release packages. Use the [release runbook](firmware-release-runbook.md)
for authorized publication and the [recovery matrix](ota-recovery-matrix.md) for qualification.

## Adapter and vehicle sessions

The companion selects and saves a source-specific adapter binding in the configuration.
The normal runtime polls one active Engine or Transmission source. Legacy menuconfig
MAC fields are fallback/scaffolding, not the normal setup or qualified dual-adapter support.
Keep real addresses and raw recordings in ignored local artifacts.

Use [TCM resume](tcm-session-resume.md) for the next Jeep session and the
[vehicle capture runbook](vehicle-capture-runbook.md) for general recording. Do not
select an adapter by its name alone. Release other clients before a Mac capture.

## Preview and recovery

Serve the explicitly simulated design prototype with:

```sh
python3 -m http.server 8765 --bind 127.0.0.1
```

Open `http://127.0.0.1:8765/design/prototype/`. It does not access real Bluetooth.
Full USB flashing changes OTA metadata; use the [migration procedure](usb-partition-migration.md)
only for explicitly authorized provisioning/recovery. Never erase bonds/config or
change eFuses as an incidental repair. Current field updates use App/Wi-Fi.
