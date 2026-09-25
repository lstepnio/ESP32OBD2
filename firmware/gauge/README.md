# Firmware baseline

Imported from the upstream commit documented in [current state](../../docs/current-state.md). This is the existing single-value OBD meter, not the designed companion-enabled firmware. MIT notice is retained in [LICENSE](LICENSE).

Build with ESP-IDF 5.4.1: source its `export.sh`, then run `idf.py build` in this folder. The direct registry dependencies are pinned in `main/idf_component.yml`. `dependencies.lock` contains machine-specific local BSP paths with this component-manager version and is generated locally; review resolved dependencies against the manifest pins.

Use `idf.py -p YOUR_PORT flash monitor` only when intentionally replacing the board image. The baseline has no OTA layout. See [development setup](../../docs/development/setup.md), [OTA design](../../docs/protocol/firmware-update.md), and [roadmap](../../docs/roadmap.md).

The `sdkconfig` matches the working reference with PSRAM disabled; the physical board reports 2 MB QSPI PSRAM. Enabling it and adjusting memory layout is a separate measured change. The inherited project suppresses some compiler warnings; removing broad suppression belongs to the first hardening milestone.
