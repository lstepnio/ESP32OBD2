# Gauge firmware

ESP-IDF/NimBLE/LVGL firmware for the Waveshare ESP32-S3-Touch-LCD-1.28: autonomous
vehicle readings, configurable pages, shared alerts and protected configuration/updates.

Source ESP-IDF 5.4.1, then from repository root:

```sh
.venv/bin/python tools/dev.py build --target firmware
.venv/bin/python tools/dev.py test --target firmware
```

The second command uses native production UI/config fixtures, not a physical gauge.
[Setup](../../README.md#development) lists prerequisites. The build checks exact
registry resolutions against `main/idf_component.yml`; the machine-specific BSP lock
is regenerated locally. [Fonts](assets/fonts/README.md) has the sole generation procedure.

See [runtime ownership/memory](../../docs/architecture/firmware-runtime.md),
[current support](../../docs/current-state.md), [source map](../../docs/ai-context.md)
and [development release procedure](../../docs/development/firmware-release-runbook.md).
Field installation uses App/Wi-Fi. USB flashing requires explicit provisioning/recovery
authorization because full flashing changes OTA metadata; preserve NVS/config/owner state.

The upstream [MIT license](LICENSE) and font notices remain intact.
