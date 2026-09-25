# Quality strategy and evidence gates

Quality is demonstrated with reproducible evidence for the behavior being claimed. A successful build proves compilation; an emulator proves chosen fixture behavior; neither proves compatibility with every adapter or vehicle.

## Checks in this foundation

`tools/validate.py`: validates schema definitions and all example documents; checks semantic references, layouts, units/ranges, alert ordering, decoder vector results and invalid-case rejection; verifies repository Markdown local link targets and documented visual token parity. The schemas are draft shape contracts, not a secure production parser. Example release hashes are intentionally zeros and not trusted artifacts.

CI runs these checks and builds the imported firmware with ESP-IDF 5.4.1. Jobs use read-only repository permission. Toolchain/container and action provenance should be locked/reviewed for releases; source-level component versions are pinned. No Android build exists yet, so CI must not show an invented Android success badge.

## Required implementation checks

| Boundary | Required evidence |
| --- | --- |
| BLE framing | MTU 23 and larger, fragmented/coalesced messages, duplicates, offset gaps, timeouts, session changes, bounded memory, unauthorized writes |
| ELM/vehicle parser | Recorded headers, echoes, prompt splits, malformed hex, NO DATA, multiple ECU replies, ISO-TP variations, service mismatch, late response isolation |
| PID definitions | Golden Kotlin/C decode vectors; signed/endian/scale bounds; rejected oversized/imported executable expressions; provenance |
| Discovery | Per-ECU bitmap mapping, range continuation, cancelled/partial scans, unsupported vs timeout, vehicle identity change |
| Threshold engine | High/low, warning/critical, hysteresis, real-time dwell, invalid gaps, reboot, acknowledgment, escalation, multiple alerts, unit conversion |
| Diagnostics | MIL/DTC/readiness decode, categories per ECU, clear token expiry/replay, conditions changing, ambiguous command completion, permanent codes, no repeat on reconnect |
| Configuration | Interrupted stage/commit, flash readback failure, revision conflict, hash mismatch, generation rollback, compatibility migration |
| OTA | Wrong board, corrupt/signature mismatch, oversized image, BLE loss, power cuts, new-image crash, incompatible config, rollback and user-visible result |
| Android | ViewModel state, process death, GATT callback generation, permissions/radio off, foreground restrictions, export/import, database migrations |
| UI | Golden states at 240 × 240 and phone sizes; 200% fonts, TalkBack, contrast, RTL, long values, reduced motion, dark/daylight |

Use fuzzing/host tests for bounded parsers and a fake clock for alert/scheduler timing. Use Android fake repositories for deterministic screen tests. [ELM327-emulator](https://github.com/Ircama/ELM327-emulator) is a candidate for multi-ECU scenarios; verify its license and transport behavior before incorporating it. A BLE UART bridge can feed fixtures without claiming the emulator itself duplicates a target adapter's radio stack.

## Hardware matrix

Record board revision, IDF/build SHA, adapter hardware/firmware/GATT profile, vehicle model/year/engine/transmission, ECU/protocol, phone model/OS/app version and test timestamp. Start with the actual Waveshare and owned adapter, then add at least one alternate BLE profile and another phone generation. Keep unknown compatibility explicitly unknown. Do not automate live ECU clearing as a regression test.

Before beta: two-hour combined polling/display/phone session without reset; 100 disconnect/reconnect cycles; queue/heap/stack watermarks recorded; power interruption tests for configuration and updates; daylight/night legibility. Before production: 24-hour controlled bench soak, expanded adapter/phone matrix, measured flash wear/radio recovery, release threat review and unresolved-risk signoff. Durations are proposed gates, not completed results.

## Definition of done

Behavior and limitations documented; acceptance trace to a requirement; focused tests and relevant builds pass; updated schema/examples/ADRs; diagnostics redaction checked; dependency/license/SBOM record; recovery instructions; release notes; no pending claims masquerading as implementation. PRs state exactly which checks ran and which require hardware.

Release bundle contains board-specific image, signed metadata, signatures, hashes, exact source revision/toolchain/dependency inventory, migration compatibility and release notes. Never commit signing keys. Signing and production release publication require a protected workflow and concrete release review.
