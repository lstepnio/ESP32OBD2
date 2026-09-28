# eGauge Android redesign audit

Baseline: `848e59d`, 2026-09-27. The companion is functional but spreads one task across four destinations. `MainActivity.kt` contains 1,318 lines. Screenshots in `screenshots/before/` were captured from the existing app on the paired Pixel, without changing its saved profiles or querying a car.

The new Android design supersedes previous Android visual and navigation guidance. Firmware, round-display geometry, protocols, and the A01+ trust requirements remain unchanged. The exhaustive baseline literal inventory is [strings.csv](strings.csv); this table groups the same content by user job.

| Existing screen / action / strings | User job | New location and level | Decision | Default-path jargon leak |
| --- | --- | --- | --- | --- |
| Header, `GAUGE FOUND`, `OFFLINE PREVIEW`, raw stage enum | Know whether it is safe to act | Connection pill + persistent status, default | Merge and rename | Stage names and disconnected discovery confused with connection |
| Gauge introduction, “Build a glanceable dashboard” | See the display | Gauge hero, default | Remove redundant instruction | Fluff obscures preview |
| Pages, Add, Earlier, Later, Remove | Arrange readings | Customize / Readings, default | Move; descriptive action names | None |
| Numeric, Arc, Bar, Trend, Dual; primary/second reading | Choose a display | Customize / Layout, default | Keep, add visual examples and capability limits | “PID support”, “ECM” |
| All example values and graphs | Try a design | Round preview, default | Keep; always label Preview | Global demo label incorrectly covers actual device facts |
| Coolant warning, critical | Know when to act | Customize / Limits, default | Rename to Warn above / Critical above | “ECM”, thresholds called sent without running confirmation |
| Hysteresis, trigger/clear dwell | Tune alerts | Limits Details; editable in Expert | Move; explain alert reset and delay | Hysteresis |
| Phone draft vs gauge, match/difference/unknown counts | Reconcile changes | Gauge status + Details comparison | Merge; preserve partial/unknown comparison | PHONE DRAFT / GAUGE SAVED, document, revision, SHA-256 |
| Refresh saved configuration; import saved settings | Avoid overwriting a different setup | Send preflight and Details, default/detail | Rename Check gauge / Use gauge settings | Revision, authenticated document readback |
| Review exact pages and coolant alert; send | Put settings on the display | Customize / Review, default | Keep exact payload, one Send to gauge action | Projection blockers, “experimental”, source identities |
| Profiles, create/select profile | Switch cars | Car / vehicle profile, default | Keep; simplify wording | “Local draft”, “PID support” |
| Find nearby gauge; candidate list | Pair a display | Guided setup, default | Move; name first, candidate identities in Details | dBm, address fragments, protected access |
| Physical-code pairing; owner authorization | Trust the correct display | Setup / Pair, default | Keep OS code entry; never collect code in app | Owner bond reset buried in ordinary instructions |
| OBD adapter placeholder | Connect the car | Setup / Adapter and Car, default | Honest unavailable state | Unimplemented discovery implied as future working setup |
| Second-adapter preference, ECM/TCM binding | Configure a swapped vehicle | Expert / Second adapter | Move, keep source-loss warnings visible | Advanced topology exposed alongside routine work |
| Reading search and example results | Pick readings | Customize / Readings, default | Merge into flow | Request hex, ECM, catalog/evidence counts |
| Source filters, discovery-state simulation | Explore supported data | Expert / PID explorer | Move; visibly Example | Discovery evidence |
| Mode 01 decoder, pasted response, errors | Inspect a response | Expert / Decoder lab | Keep, selectable Details | Mode 01 response lab, response bytes |
| Custom Mode 01/09/22 read request and prefix | Validate a definition | Expert / Custom PIDs | Keep read-only syntax checks; no fabricated support | Read request bytes, ECU, local draft |
| Connection, board, protocol, link slots, simultaneous flag | Inspect device capability | Settings / Details | Move, copy all values | Protocol identity, experimental control inventory |
| Hardware memory, processor, reset, Wi-Fi, subsystem flags | Troubleshoot hardware | Expert / Diagnostics; Settings Details | Keep every field | Internal RAM watermarks and subsystem inventory |
| Wi-Fi security self-check | Check transport protection | Expert / Security | Keep existing authenticated implementation | Session/key/replay terminology |
| Saved reading and display rotation | Set up the display | Settings / Gauge, default | Keep confirmed rotation; built-in selection in Details | Saved revision |
| Stored/running setup and document readback | Verify configuration | Details, plus default recovery card | Keep separate facts and selectable JSON | Hash, revision, definition counts |
| Check-engine, code categories/counts/first code | Check the car | Car, default + Details | Move; explain known standard codes and incomplete results | MIL, DTC, source freshness internals |
| Clear-code unavailable notice | Clear faults safely | Car empty state; prototype consequence dialog | Keep unavailable; no live clear command | “Vehicle-scoped confirmation” |
| Firmware identity, OTA state, partition, ELF digest | Know installed version | Settings / Updates + Details | Version default; all identity details selectable | OTA state, partition and digest |
| GitHub catalog check, download/verify, release channel | Keep current | Settings / Updates, default; development source in Expert | Merge Install stages, gate stable availability honestly | GitHub, catalog generation, development release channel |
| Local signed development package | Test a development release | Expert / Development updates | Move; preserve signed checks | Package selection in ordinary settings |
| Interrupted update journal and reconciliation | Recover safely | Persistent status + Updates, default | Keep unknown / rolled back / checking outcomes visible | Running image identity |
| Errors from transport, decoder, profile and signature checks | Recover with one next step | Default human message; raw reason in Details | Rename centrally, retain technical cause | Vendor error codes and exception messages |

No first-run task may depend on the user reading Details. No successful saved-storage read can claim the intended setup is running. A first dashboard in two minutes is a usability target, not a measured result: adapter setup and live telemetry are unavailable in this build.
