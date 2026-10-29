# Quality strategy and evidence gates

Quality is demonstrated with reproducible evidence for the behavior being claimed. A successful build proves compilation; an emulator proves chosen fixture behavior; neither proves compatibility with every adapter or vehicle.

## Checks in this foundation

`tools/validate.py`: validates schema definitions and all example documents; checks semantic references, layouts, units/ranges, alert ordering, decoder vector results and invalid-case rejection; verifies repository Markdown local link targets and documented visual token parity. The schemas are draft shape contracts, not a secure production parser. Example release hashes are intentionally zeros and not trusted artifacts.

The shared `tools/dev.py` commands are the only local/CI command definitions;
[README](../../README.md#development) lists invocation and prerequisites. Host tests
discover every `test_*.py` suite under tools and the review server, plus native C
sanitizers. Firmware tests use production LVGL/fonts and config binding. Android
checks include JVM tests, instrumentation compilation, lint and an isolated UI lane.
Quality jobs have read-only repository access and bounded execution times.
The shared JVM gate runs both `testDebugUnitTest` and `testReleaseUnitTest` in one
Gradle invocation. Positive configuration fixtures that require development-only
projection are tagged `DevelopmentConfiguration`; release excludes that category.
Shared storage/parser/standard-reading tests still run in both variants. Variant
contract tests live in `src/testDebug` and `src/testRelease`: release explicitly
checks blocked TCM pages/alerts/routes, preserved saved data and working ECM projection.
New development-only fixtures need a corresponding release gate assertion; never
loosen a runtime flag or add blanket ignores to make the suite pass.

Pinned formatters cover authored Python, C (including authored `.inc` vector paths) and Kotlin. Generated catalogs, tokens,
fixtures and fonts are checked by their generators, not hand formatted. C include
order is preserved because FreeRTOS and BSP header order has semantic dependencies.

## Change-based check selection

Choose the affected ownership boundary from [AI context](../ai-context.md). Run its
complete check set once, then repeat only failures or checks affected by a later edit.
Do not sweep devices, archived evidence or imported research to answer a source-only task.

| Changed boundary | Local check selection |
| --- | --- |
| Maintained docs, status or evidence only | `lint --target host`; no device or runtime build |
| Host tooling or contracts | Python format check, host lint/test; native fixtures are included |
| Firmware UI, board seam or fonts | C format check, host lint/test, firmware build/test; compare reference frames before changing presentation |
| Other firmware runtime | C format check, host lint/test, firmware build; firmware fixtures for the affected boundary |
| Android source/UI | Kotlin format check, Android build/test/lint; `test --target android-ui` for changed interactions/layout |
| Website/recovery source/assets | Host lint/test; rebuild recovery bundle for recovery-source changes; publication remains a separate gate |
| Private display profiles/ports | Use the private repository's check entry point and pinned core; keep board resources/evidence private |

The entries are arguments to `python tools/dev.py`; setup is owned by README. This
selection does not replace required CI, broaden release authorization or establish
physical qualification. CI runs all required lanes and cancels superseded PR runs
without cancelling main-branch verification. Include exact commit, checks and
remaining gates in the handoff; leave temporary failures out of current-state claims.

## Android UI and screen captures

`python tools/dev.py test --target android-ui` runs only allowlisted fixture suites
on a Gradle-managed API 35 Pixel 2. It has no adapter/gauge owner, saved-profile writes
or physical-phone installation. CI publishes test reports and additional output.
The allowlist includes `PresentationAccessibilityTest` for 200% text and primary-action
reachability. Node fixtures execute browser recovery orchestration and form failure
paths with deferred network/transport inputs; Python intake tests use temporary
synthetic databases and restore checks.
This lane checks interactions and accessibility-related semantics, not the old Pixel
pixel baseline; API/font rasterizers differ.

Native IME resizing can move modal-sheet touch targets outside Compose's clock.
Text-entry fixtures close the keyboard through `closeKeyboardAndWaitForIdle`
before touching non-text controls, then observe the expected editor destination
with a bounded condition. On API 30+ the helper addresses the focused window's
IME controller and awaits hidden insets: Espresso may otherwise select the
unfocused Activity behind a Compose dialog. It never dismisses a dialog with Back. Keep actual taps and exact saved-value assertions;
do not hide keyboard-specific tests, retry taps, add sleeps or relax assertions.

`RepositoryGalleryTest` captures current production Gauge/Car/Settings composables
with fixed fixture state and the compact navigation shell. Copy its three PNGs from
`android/app/build/outputs/managed_device_android_test_additional_output/` into
`docs/assets/app/` after visual review. The neutral README captions are screen names;
these files are rendered fixtures, not vehicle observations. No privacy-sensitive
phone capture is needed. For gauge images run firmware tests, then
`python tools/render_gauge_gallery.py`; the script converts selected production LVGL
RGB frames into `docs/assets/gauge/`. It adds no independent mock renderer.

`GoldenScreenshotTest` is a separate reviewed baseline. Its recorded environment is
the Pixel 10 Pro/API 37 with fixed density/font scale. Missing images or differences
above 0.05% of pixels (three channel levels of antialias allowance) fail. Ordinary
runs never update goldens. Recording requires explicit `recordGoldens=true`, visual
review of every intended change and a second comparison without that flag.

For authorized physical sessions, start/stop `tools/adb_debug_awake.py` and always
select the exact device serial. Never use broad `connectedDebugAndroidTest` on the
owner phone: it previously uninstalled the target and cleared its data. Build both
APKs, install with `adb -s SERIAL install -r`, then use `adb -s SERIAL shell am instrument`
with an exact class. Activity fixtures disable automatic discovery using the existing
debug-only intent extra. Physical configuration, Bluetooth and OTA journeys require
their explicit opt-in arguments and the corresponding owner authorization. Back up
private setup, preserve uncertain outcomes and restore wake/radio settings afterward.
A fixture, golden or passed compilation never substitutes for hardware qualification.

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
