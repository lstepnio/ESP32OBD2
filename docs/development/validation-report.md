# Foundation validation, 2026-09-25

## Completed locally

- ESP-IDF 5.4.1 build of the imported `firmware/gauge` source: passed. App binary 953,184 bytes (`0xe8b60`), 9% free in the inherited 1 MiB app partition. This is the old baseline layout, not the proposed 4 MiB A/B layout.
- Draft contract tooling: 3 schemas, 6 examples, 15 invalid-case rejection fixtures, independent signed/little-endian vector, local documentation links and prototype color token parity passed.
- Browser JavaScript syntax check passed.
- Browser interaction checks: dual layout with synthetic ECM and TCM sources; units; demo config apply; PID search; invalid/valid raw-byte decode; invalid threshold ordering; critical alert acknowledgment; clear-code confirmation disabled before consent; simulated readback retains permanent code; update pause/resume.
- Responsive check at 390 px: document width equals viewport width with no page-level horizontal overflow. Desktop and mobile gauge presentation visually inspected. Browser console contained no error entries at inspection.

## Limits

The interactive concept uses synthetic data. Browser checks do not validate production security, accessibility compliance, native Android lifecycle, LVGL rendering or vehicle communication. No new firmware was flashed in this design milestone. No real code-clear operation was sent. Dual adapters, Wi-Fi coexistence, signed OTA, and configuration protocol remain unimplemented and need the documented hardware spikes.

The CI workflow has been authored; its first remote result must be recorded separately after publication. Local success does not imply GitHub Actions has run.
