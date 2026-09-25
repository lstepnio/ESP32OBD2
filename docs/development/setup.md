# Development setup

## Design and contracts

Python 3.10+ for tooling. Create a venv, install `tools/requirements.txt`, run `python tools/validate.py`. Serve repository with `python3 -m http.server 8765 --bind 127.0.0.1` and open `/design/prototype/`. The prototype has no build step, network dependencies, or Web Bluetooth access.

## Firmware baseline

Install [ESP-IDF 5.4.1](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/get-started/linux-macos-setup.html) for esp32s3 and activate its environment. Then:

```sh
cd firmware/gauge
idf.py build
# Only when intentionally uploading:
idf.py -p YOUR_SERIAL_PORT flash monitor
```

`main/idf_component.yml` pins all directly/transitively resolved registry components for this baseline. IDF resolves a local BSP path into its lockfile; that machine-local lockfile and downloaded components are ignored. CI uses the IDF 5.4.1 container and builds from manifests. Capture resolved lock/SBOM in release artifacts with paths sanitized. No claim of bit-for-bit reproducibility until two clean environment builds are compared.

## Adapter selection for the M1 hardware spike

Run `idf.py menuconfig` and open **eGauge adapter selection**. Leave the ECM MAC blank only for first-compatible discovery. Enter `AA:BB:CC:DD:EE:FF` format to bind ECM deterministically. Enter a different, observed address for TCM; a blank TCM address disables the second link. The TCM link stays idle after connection until source-specific TCM PID definitions are available. Keep real vehicle adapter addresses in local `sdkconfig` only, and restore the tracked defaults before committing. BLE privacy addresses may rotate, so the companion association flow will need stable identity/bond handling instead of relying on a plain MAC string.

## Paired quick-selection setup

After uploading the integration firmware, launch the Android app and read gauge capabilities. The Device screen shows paired quick selection when the `quickSelect` capability is present. Choose one of the five built-in PID examples in Design. On first use, long press the gauge to open a two-minute pairing window, then tap **Set preview reading on gauge** in Device. Enter the six-digit code shown on the round LCD in Android's system pairing dialog. The app writes the selected built-in index and waits for an authenticated state readback before reporting success. A 12-second touch hold deletes the owner bond and restarts the gauge if the phone is lost. This flow needs an on-phone hardware review before claiming successful pairing.

If the gauge owner is reset, also forget the old eGauge bond in Android Bluetooth settings before pairing again. Android can otherwise retain a stale bond that the gauge no longer accepts.

## Experimental BLE client probe

With the gauge powered nearby, install `tools/requirements-ble.txt` in an isolated Python environment and run `python tools/ble_probe.py`. The script scans for the project service UUID, connects, and reads the public capability characteristic. It sends no vehicle commands and does not pair. `configWrite` and OTA remain disabled; the separate `quickSelect` flag describes the bounded paired operation.

## Existing Mac notes

The active local setup is `~/esp/esp-idf-v5.4.1`, with its managed environment at `~/.espressif/python_env/idf5.4_py3.9_env`. Bootstrap used system Python 3.9.6 because the Homebrew Python was newer than the toolchain expected. The Python 3.9 package-metadata checker failed to resolve ruamel distribution names. This environment was repaired with ruamel.yaml 0.17.21 and a local `ruamel.yaml.clib-0.2.15.dist-info` symlink to the underscore-named metadata directory. CMake 3.31.10 and Ninja 1.13.2 were installed into that venv. These are local workaround notes, not a portable installation recipe. Prefer the pinned container or a clean supported Python environment for CI/new machines.

Use `source "$HOME/esp/esp-idf-v5.4.1/export.sh"` to activate on this Mac; the baseline installation selected system Python through PATH during setup. Device USB serial port names can change after reconnect. Do not hardcode this machine's port in project scripts.

## Android

Architecture is in [android/README.md](../../android/README.md). Create the Gradle/Kotlin/Compose project in milestone M2 after BLE framing/capability contract is validated. Add Gradle wrapper and dependency verification metadata then. No APK build command is claimed at this stage.

## Recovery

The current development image can be re-flashed via USB using the built source. OTA-enabled production firmware will require its own known compatible images and [migration procedure](../protocol/firmware-update.md). Never erase bonds/config or change eFuses as an incidental part of UI development.
