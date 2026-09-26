# Sources and reference decisions

Checked 2026-09-26. Official documentation and actual checked-out source anchor the design; URLs can change. Design proposals in this repository are our own unless explicitly attributed.

| Source | Use | Boundary |
| --- | --- | --- |
| [Exact-board meter](https://gitlab.com/janoskut/esp32-obd2-meter) | Imported baseline, BSP, original UI/OBD flow | MIT retained; source inspected at recorded commit |
| [Waveshare hardware documentation](https://docs.waveshare.com/ESP32-S3-Touch-LCD-1.28) | Board components, interfaces and pin functions | Two SKUs and multiple schematic revisions are listed; exact assembled revision still requires a physical/probe check |
| [Waveshare resources](https://docs.waveshare.com/ESP32-S3-Touch-LCD-1.28/Resources-And-Documents) | Rev3 schematic, component datasheets and mechanical model | Vendor files describe the reference board; population and electrical limits must be confirmed before accessory loads |
| [ESP32-S3 USB device documentation](https://docs.espressif.com/projects/esp-usb/en/latest/esp32s3/usb_device.html) | Native USB capability boundary | The current board USB-C connector routes through CH343P USB-to-UART, so native USB is not assumed available there |
| [ESP-IDF 5.4.1 setup](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/get-started/linux-macos-setup.html) | Toolchain baseline | Working local installation documented separately |
| [ESP-IDF OTA](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-reference/system/ota.html) | A/B slots, boot confirmation and rollback APIs | Current baseline is not OTA-enabled |
| [NimBLE multi-connection](https://docs.espressif.com/projects/esp-idf/en/v5.5.3/esp32s3/api-guides/ble/ble-multiconnection-guide.html) | Central/peripheral capability | Guide is newer than pinned IDF; validate actual board/version |
| [Android architecture](https://developer.android.com/topic/architecture/recommendations) | Native Compose, repositories, observable state | Exact dependency versions selected at implementation |
| [Android BLE background](https://developer.android.com/develop/connectivity/bluetooth/ble/background) | Connection lifecycle and background work | No promise of unrestricted background execution |
| [Android permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) | Version-specific scan/connect permissions | Permissions are separate from gauge ownership |
| [Companion association](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing) | System association option | Does not create a GATT connection automatically |
| [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) | Touch targets and semantics | Physical gauge readability still requires hardware review |
| [ELM327 datasheet](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf) | Command flow and supported-PID query reference | Clone behavior varies; manufacturer PID meanings need separate sources |
| [BAR OBD reference](https://www.bar.ca.gov/obd-test-reference) | MIL/readiness and permanent DTC distinction | No jurisdiction-specific inspection promise in product |
| [ELM327 emulator](https://github.com/Ircama/ELM327-emulator) | Candidate multi-ECU fixture harness | Not yet integrated; radio behavior separate |
| [OBDb](https://github.com/OBDb) | Candidate vehicle-specific definition research | No catalog copied; per-project license/provenance review required |

Earlier broad project research is retained at [reference projects](reference-projects-and-architecture.md), with a new status notice to prevent stale setup instructions being mistaken for the current baseline.
