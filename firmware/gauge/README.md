# Gauge firmware

The firmware supports configurable pages and alerts, protected companion configuration, and experimental signed updates. Its upstream origin is documented in [current state](../../docs/current-state.md); the MIT notice is retained in [LICENSE](LICENSE). Capability flags remain the authority for what a particular build exposes.

Build with ESP-IDF 5.4.1: source its `export.sh`, then run `idf.py build` in this folder. The direct registry dependencies are pinned in `main/idf_component.yml`. `dependencies.lock` contains machine-specific local BSP paths with this component-manager version and is generated locally; review resolved dependencies against the manifest pins.

The tracked partition table uses 16 MB flash with two 3 MB OTA slots. A full `idf.py flash` also replaces OTA metadata; use it only for an intentional board migration. Application-only bench flashing must first identify the running slot and preserve NVS, configuration partitions and OTA metadata. See [development setup](../../docs/development/setup.md) and [OTA design](../../docs/protocol/firmware-update.md).

The tracked configuration enables the board's 2 MB QSPI PSRAM and uses performance compiler optimization with assertions retained. CPU and peripheral clocks remain at their existing settings. The [performance review](../../docs/development/gauge-performance-review.md) records touch-path changes, persistence behavior, optional timing instrumentation, and the distinction between source checks and physical measurements.
