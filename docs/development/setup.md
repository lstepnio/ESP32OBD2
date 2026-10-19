# Development setup and checks

Use an existing working environment first. Commands below run from the repository
root unless stated otherwise. Hardware work needs the owner's readiness/authorization;
normal firmware installation is through App/Wi-Fi. Building does not install anything.

## Toolchains and shared checks

[Root README](../../README.md#development) owns fresh-clone prerequisites and the
single lint/format/test/build commands. [Quality](quality.md) owns test scope and
hardware safety. This page contains environment-specific troubleshooting and sessions.

Use the existing ESP-IDF Python environment before exporting IDF on the known Mac:

```sh
export IDF_PYTHON_ENV_PATH="$HOME/.espressif/python_env/idf5.4_py3.9_env"
export PATH="$IDF_PYTHON_ENV_PATH/bin:$PATH"
. "$HOME/esp/esp-idf-v5.4.1/export.sh"
. .venv/bin/activate
```

These paths describe one installation, not fresh-clone requirements. Do not recreate
old package-metadata workarounds or reinstall working tools without an actual failure.
The build invokes IDF's own selected Python for dependency checks. Registry components
must match exact manifest pins; the local resolver lock embeds an absolute BSP path
and stays ignored. Do not broaden the ignore rule to all lockfiles.

To intentionally update Android dependencies, edit explicit declarations, run the
relevant wrapper tasks with `--write-locks`, review each resolved change and update
verification metadata only against trusted artifacts. Never regenerate checksums just
to bypass a mismatch. Formatter versions/checksum live in `tools/requirements-dev.txt`
and `tools/tooling.json`; CI consumes the same pins. No global formatter install is needed.

## Adapter and vehicle sessions

The companion selects and saves a source-specific adapter binding in the configuration.
The default setup uses one primary adapter for the whole vehicle dashboard, including
any supported transmission requests. An explicitly configured child adapter changes
request routing, not the visible reading/page list. The per-gauge Expert option requires
`da:1` and distinct bindings. Menuconfig ECM MAC is a legacy fallback; the idle TCM
worker is retired. See [dual recovery qualification](dual-adapter-recovery.md).
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
Full USB flashing changes OTA metadata; use the [migration procedure](../evidence/usb-partition-migration.md)
only for explicitly authorized provisioning/recovery. Never erase bonds/config or
change eFuses as an incidental repair. Current field updates use App/Wi-Fi.
