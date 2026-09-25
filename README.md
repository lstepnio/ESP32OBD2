# eGauge

A standalone round OBD-II gauge and native Android companion for vehicle telemetry, flexible PID discovery, dashboard configuration, and recoverable firmware updates.

**Status: design foundation, an Android app foundation, and a working upstream firmware baseline.** The Waveshare display and touch work on the connected device. The Android app builds with simulated data, local dashboard drafts, an offline PID decoder, and a read-only BLE capability reader. A Pixel 10 Pro found the gauge and read protocol 0 capabilities over BLE. Live vehicle communication, configuration transfer, and firmware updates have not been verified.

## Start here

- [Interactive design](design/prototype/index.html): Android companion concept and 240 × 240 gauge preview. All readings, discovery, configuration transfers, and updates are simulated.
- [Product requirements](docs/requirements.md): everyday driving, off-road/thermal monitoring, performance, and diagnostics receive equal priority.
- [Architecture](docs/architecture/system.md), [Android design](android/README.md), and [decisions](docs/architecture/decisions.md).
- [PID discovery and custom definitions](docs/protocol/pid-discovery.md), [BLE contract](docs/protocol/ble-v1.md), and [firmware updates](docs/protocol/firmware-update.md).
- [CEL/DTC diagnostics and threshold alerts](docs/protocol/diagnostics-and-alerts.md).
- [Visual and interaction specification](docs/design/design-system.md).
- [Foundation validation report](docs/development/validation-report.md).
- [Quality and release gates](docs/development/quality.md), [implementation roadmap](docs/roadmap.md), and [development setup](docs/development/setup.md).
- [Current hardware/firmware evidence](docs/current-state.md) and [research sources](docs/sources.md).

## Preview and validate

```sh
python3 -m http.server 8765 --bind 127.0.0.1
# Open http://127.0.0.1:8765/design/prototype/
```

```sh
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt
.venv/bin/python tools/validate.py
```

## Repository map

| Path | Purpose |
| --- | --- |
| `firmware/gauge/` | Imported ESP-IDF baseline, upstream MIT notice retained; not yet the new architecture |
| `android/` | Native Jetpack Compose app, offline demo flows, build instructions, and screen contracts |
| `contracts/` | Draft JSON Schemas and examples, versioned alongside documentation |
| `design/` | Shared visual tokens and a dependency-free interaction prototype |
| `docs/` | Requirements, decisions, protocols, quality gates, sources, and roadmap |
| `tools/` | Documentation, schema, and semantic validation |

The browser prototype is a review tool. The Android app uses Jetpack Compose; gauge UI is planned in LVGL. The gauge operates without a phone after configuration. No account or cloud connection is needed for normal operation.

## Contribution and licensing

Read [CONTRIBUTING](CONTRIBUTING.md) before changing contracts or behavior. Imported firmware retains its [MIT license](firmware/gauge/LICENSE); fonts retain the [SIL Open Font License](third_party/NotoSans-OFL.txt). See [third-party notices](THIRD_PARTY_NOTICES.md). A license for newly authored project material has not yet been selected; upstream licensing does not automatically license the entire repository.

## Separate ECM and TCM adapters

The design includes source-specific adapter selection, connection state, discovery, PID binding and alerts for swapped vehicles. [Dual-adapter feasibility and fallback TODOs](docs/architecture/multi-adapter.md). Simultaneous operation remains unverified.

## Hardware-aware transport extension

BLE provides association/control and a universal update path; Wi-Fi is designed as a negotiated faster bulk transport sharing the same operation state, trust checks and recovery semantics. Two-adapter radio coexistence, board sensors, PSRAM, USB and power management are covered in the [hardware and transport strategy](docs/architecture/hardware-and-transports.md). Preferred production update transport remains subject to WIFI-001/RADIO-001 measurements.
